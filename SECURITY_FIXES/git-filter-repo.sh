#!/usr/bin/env bash
set -euo pipefail

# git-filter-repo wrapper script for purging secrets from repository history
# Usage:
# 1) Place replacements.txt in the same parent directory as the mirror will be created.
# 2) Run: ./git-filter-repo.sh git@github.com:Macucul/Portal-fimaster-V2.git
# 3) The script will clone a mirror, run git-filter-repo --replace-text, and (optionally)
#    force-push the rewritten history to origin after confirmation.

if [ $# -ne 1 ]; then
  echo "Usage: $0 <git-remote-spec>"
  echo "Example: $0 git@github.com:Macucul/Portal-fimaster-V2.git"
  exit 1
fi

REMOTE_URL="$1"
REPO_NAME=$(basename "$REMOTE_URL" .git)
WORKDIR="${PWD}/${REPO_NAME}.git"

echo "Will operate on mirror repo path: ${WORKDIR}"

cat <<'WARN'
=== IMPORTANT WARNINGS ===
- THIS SCRIPT REWRITES GIT HISTORY. Coordinate with your team BEFORE pushing.
- REVOKE/ROTATE the exposed PAT on GitHub BEFORE performing any push.
- All collaborators will need to re-clone or reset after a forced-push.
- Run this in a secure environment (not CI) and inspect results before pushing.
WARN

read -p "Have you revoked the exposed PAT and coordinated with the team? (type YES to continue) " CONFIRM
if [ "$CONFIRM" != "YES" ]; then
  echo "Aborting. Revoke the PAT and coordinate with team, then re-run.";
  exit 2
fi

# 1) Clone mirror
if [ -d "$WORKDIR" ]; then
  echo "Mirror already exists at $WORKDIR, reusing it."
else
  echo "Cloning mirror of repository..."
  git clone --mirror "$REMOTE_URL" "$WORKDIR"
fi

cd "$WORKDIR"

echo "Mirror at: $(pwd)"

echo "Fetching latest refs..."
git remote update origin --prune

# 2) Ensure replacements.txt is available (it should be one level up from the mirror)
REPLACEMENTS_PATH="$(dirname "$WORKDIR")/replacements.txt"
if [ ! -f "$REPLACEMENTS_PATH" ]; then
  echo "replacements.txt not found at $REPLACEMENTS_PATH"
  echo "Please download replacements.txt into the parent directory and re-run."
  exit 3
fi

# 3) Run git-filter-repo
if ! command -v git-filter-repo >/dev/null 2>&1; then
  echo "git-filter-repo not found. Install it before running this script: https://github.com/newren/git-filter-repo"
  exit 4
fi

echo "Running git-filter-repo --replace-text $REPLACEMENTS_PATH ..."
# Use --force to allow repeated runs if needed
git filter-repo --replace-text "$REPLACEMENTS_PATH"

echo "Filter complete. Performing repository maintenance..."
git reflog expire --expire=now --all || true
git gc --prune=now --aggressive || true

# 4) Quick sanity checks
echo "Scanning for placeholder markers in history (REDACTED_GITHUB_PAT)"
if git grep -n "REDACTED_GITHUB_PAT" >/dev/null 2>&1; then
  echo "WARNING: REDACTED_GITHUB_PAT found in working tree — inspect before pushing."
else
  echo "No occurrences of REDACTED_GITHUB_PAT in working tree."
fi

# 5) Confirm and push
echo "=== PREPARE TO PUSH ==="
echo "You are about to force-push rewritten history to origin. This is destructive and will
require all collaborators to re-clone or reset."
read -p "Type PUSH to perform 'git push --force --all' and 'git push --force --tags': " PUSH_CONFIRM
if [ "$PUSH_CONFIRM" != "PUSH" ]; then
  echo "Push canceled. Inspect the mirror locally and push when ready.";
  exit 0
fi

echo "Pushing rewritten history to remote (force)..."
git push --force origin --all
git push --force origin --tags

echo "Push complete. NOTIFY YOUR TEAM to re-clone or reset their local clones." 

echo "DONE. Now run secrets scans (gitleaks/truffleHog) and confirm no secrets remain."
