#!/usr/bin/env bash
set -euo pipefail

# Local helper to run gitleaks and truffleHog and produce simple reports.
# Requires: gitleaks (https://github.com/zricethezav/gitleaks) and truffleHog (pip install truffleHog)

REPO_DIR=${1:-$(pwd)}
OUT_DIR=${2:-"$(pwd)/scans"}
mkdir -p "$OUT_DIR"

echo "Running gitleaks on $REPO_DIR..."
gitleaks detect --source "$REPO_DIR" --report-path "$OUT_DIR/gitleaks-report.json" || true

if command -v trufflehog >/dev/null 2>&1; then
  echo "Running truffleHog on $REPO_DIR..."
  trufflehog --json file://"$REPO_DIR" > "$OUT_DIR/trufflehog.json" || true
else
  echo "truffleHog not installed; skipping truffleHog run. Install with: pip install truffleHog"
fi

echo "Reports saved to $OUT_DIR"
