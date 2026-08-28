# Tarefas para o Assistente: Aplicar todas as correções e hardening de segurança

Objetivo: listar todas as alterações, patches e comandos operacionais que o seu assistente deve executar para corrigir e mitigar os problemas detectados (P0→P2). Este documento é um roteiro "paste-and-run" — cada passo tem o comando exato, arquivos a editar e snippets de código para aplicar.

IMPORTANTE (antes de começar)
- Revogar/rotacionar o token GitHub exposto IMEDIATAMENTE (Settings → Developer settings → Personal access tokens). Faça isto agora.
- Trabalhe em branch `hotfix/security-immediate` (já criado). Não mescle até todos os pré-requisitos e validações estarem OK.

Resumo das ações que este script/assistente aplicará
1. Rotacionar e revogar tokens expostos (instrução manual).  
2. Purga do histórico Git com git-filter-repo usando `SECURITY_FIXES/replacements.txt`.  
3. Aplicar patches críticos (manifest, .env.example, firebase_rules.json) — já aplicados no branch, mas validar e completar.  
4. Migrar armazenamento inseguro para EncryptedSharedPreferences (GitHubConfigManager, ConfigManager, Secure storage migration).  
5. Substituir SHA-256 simples por PBKDF2 para senhas (SecurityUtils, GithubUserParser, locais de verificação e armazenagem).  
6. Centralizar OkHttpClient (singleton) e remover criações repetidas.  
7. Ajustar PortalRepository/SmsGatewayRepository para não expor PATs no logs e reduzir scope de logs.  
8. Habilitar minify (R8) no release build e preparar proguard-rules.pro.  
9. Integrar gitleaks como verificação pre-commit e pipeline SCA.  
10. Recomendações EA/MT5: remover auth_key da configuração do EA e migrar para backend de proxy.  
11. Habilitar Firebase App Check (orientações e pontos de integração).  

Detalhamento passo-a-passo para o assistente (executar nesta ordem)

A — Pré-trabalho (faça manualmente agora)
1. Revogar token exposto. (ação humana)  
   - URL: https://github.com/settings/tokens
   - Revogar `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`
2. Gerar novo PAT (se necessário) com scope mínimo. Armazenar em GitHub Secrets -> `GITHUB_PAT_PROD`.

B — Purga do histórico Git (executar localmente no servidor/PC do assistente)
1. Baixar replacements.txt do branch:
   curl -o replacements.txt "https://raw.githubusercontent.com/Macucul/Portal-fimaster-V2/hotfix/security-immediate/SECURITY_FIXES/replacements.txt"
2. Executar no host seguro:
   git clone --mirror git@github.com:Macucul/Portal-fimaster-V2.git
   cd Portal-fimaster-V2.git
   git filter-repo --replace-text ../replacements.txt
   git reflog expire --expire=now --all
   git gc --prune=now --aggressive
   # Revisar localmente: git log --all --grep REDACTED_GITHUB_PAT
   # Quando validado, push forçado (coordene com equipe):
   git push --force origin --all
   git push --force origin --tags
3. Repetir para o repo Sms_gatwey_fimaster_admin se houver segredos (clone e replacements.txt adaptado).

C — Aplicar patches (se ainda não aplicados localmente)
Os arquivos abaixo já foram modificados no branch `hotfix/security-immediate`, mas valide/complete os conteúdos locais:

1. app/src/main/AndroidManifest.xml
   - Verificar e garantir:
     android:allowBackup="false"
     android:usesCleartextTraffic="false"

2. .env.example
   - Garantir que não há tokens reais; manter placeholders.

3. firebase_rules.json
   - Verificar regras endurecidas no branch; ajuste conforme paths de produção.
   - Deploy em staging: `firebase deploy --only database:rules --project <STAGING_PROJECT_ID>`

D — Migração de armazenamento de tokens para EncryptedSharedPreferences
Objetivo: remover armazenamento plain SharedPreferences/Room para tokens (githubToken, fastApiToken, fastApiToken, BuildConfig fallback) e usar o MasterKey + EncryptedSharedPreferences.

Arquivos para modificar:
- app/src/main/java/com/example/data/GitHubConfigManager.kt
- app/src/main/java/com/example/data/local/ConfigManager.kt
- app/src/main/java/com/example/data/PortalRepository.kt (leitura/uso do token)
- app/src/main/java/com/example/ui/PortalViewModel.kt (onde token é lido ou escrito para UI)

Tarefas concretas (substituições):
1. Adicionar dependência (já presente via libs): androidx.security:security-crypto.
2. Criar utilitário central `SecurePrefs.kt`:
```kotlin
// app/src/main/java/com/example/data/SecurePrefs.kt
object SecurePrefs {
  fun getEncryptedPrefs(context: Context): SharedPreferences {
    val masterKey = MasterKey.Builder(context)
       .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
       .build()
    return EncryptedSharedPreferences.create(
      context,
      "secure_prefs",
      masterKey,
      EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
      EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
  }
}
```
3. Em GitHubConfigManager.kt substituir os métodos encrypt/decrypt e uso de prefs para salvar `github_token` por SecurePrefs. Exemplo (após injeção de Context):
```kotlin
fun saveConfig(config: GitHubAdminConfig) {
  val secure = SecurePrefs.getEncryptedPrefs(appContext)
  secure.edit().apply {
    putString("github_token", config.token.trim())
    putString("github_repo", config.repository.trim())
    putString("github_branch", config.branch.trim())
    putString("github_path", config.path.trim())
    apply()
  }
}

fun getConfig(): GitHubAdminConfig {
  val secure = SecurePrefs.getEncryptedPrefs(appContext)
  val token = secure.getString("github_token", "") ?: ""
  val repo = secure.getString("github_repo", "Macucul/fimaster") ?: "Macucul/fimaster"
  ...
}
```
4. Migrar dados existentes: criar uma rotina de migração (executar uma vez na inicialização) que lê os valores antigos em `context.getSharedPreferences("fimaster_admin_prefs")`, move para secure prefs e limpa os valores antigos.

E — Substituir SHA-256 simples por PBKDF2 (senha segura)
Arquivos:
- app/src/main/java/com/example/util/SecurityUtils.kt (ou similar)
- app/src/main/java/com/example/data/GithubUserParser.kt (onde sha256 era usado)
- todos os lugares onde `hashSha256`/GithubUserParser.sha256 é usado para senhas (PortalViewModel, SmsGatewayRepository, login flows)

Tarefas concretas:
1. Implementar função PBKDF2 no SecurityUtils.kt:
```kotlin
fun hashPasswordPBKDF2(password: CharArray, salt: ByteArray, iterations: Int = 100_000, keyLength: Int = 256): String {
  val spec = PBEKeySpec(password, salt, iterations, keyLength)
  val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
  val key = skf.generateSecret(spec).encoded
  return Base64.encodeToString(key, Base64.NO_WRAP)
}

fun generateSalt(size: Int = 16): ByteArray { val salt = ByteArray(size); SecureRandom().nextBytes(salt); return salt }
```
2. Ao criar/atualizar senha: gerar salt, criar hash pbkdf2, armazenar como `version1:$iterations:$saltBase64:$hashBase64` ou similar. Atualizar DB local (migration incremental) para suportar hashes antigos e novos.
3. Ao verificar senha: detectar formato da hash e aplicar algoritmo correto (compatibilidade com antigo SHA256 até migração completa).

F — OkHttp singleton + reduzir logs sensíveis
Arquivos:
- Criar `app/src/main/java/com/example/network/HttpClient.kt` com OkHttp singleton (timeouts, interceptors para adicionar Authorization header quando necessário — use tokenProvider). Remover instâncias de OkHttpClient criadas por chamada.

Exemplo:
```kotlin
object HttpClient {
  val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .build()
}
```
- Refatorar PortalRepository/SmsGatewayRepository para usar HttpClient.client.
- Remover printStackTrace e evitar log de tokens (mask tokens if logging). Replace logs that print headers/payloads with structured logs without secrets.

G — Habilitar minify/R8 para release
1. Editar `app/build.gradle.kts` (ambos repositórios) e set `isMinifyEnabled = true` in release.
2. Adicionar proguard rules para Firebase/Room/Moshi:
```
-keep class com.google.firebase.** { *; }
-keep class androidx.room.** { *; }
-keep class com.squareup.moshi.** { *; }
```
(ajustar conforme problema durante testing)

H — Pre-commit / CI scanning (gitleaks)
1. Adicionar gitleaks config e pre-commit hook: `.gitleaks.toml` e `.husky/pre-commit` (ou simple git hook script).  
2. CI pipeline: adicionar step que roda `gitleaks detect --source . --exit-code 1` e falha o build se segredos forem detectados.

I — Firebase App Check e regras
1. Integrar App Check SDK nos apps (Android): use Play Integrity ou SafetyNet / reCAPTCHA v3 se needed.  
2. Forçar enforcement no Firebase Console após integrar e testar.  
3. Usar custom claims (admin=true) para isolar admin vs client em regras (já aplicado em firebase_rules.json).  

J — EA/MT5 (MQL5) recomendações (não aplicar automaticamente no código do EA sem validação)
1. NÃO inserir long-lived auth_key no EA: ao invés disso, fazer EA chamar um backend autenticado (ex: com mTLS or HMAC short-lived token) que então escreve no Firebase/gera comandos.  
2. Se for manter auth_key, rotacionar chaves e limitar privilégios (escrita apenas em path permitido).  

K — Testes e validação (staging → production)
1. Deploy firebase_rules.json no staging e testar: leitura e escrita por usuário autenticado e admin; negar acessos indevidos.  
2. Testar app behavior: login, sync, EA parameter read.  
3. Run gitleaks/truffleHog and SCA scans.  

L — Pós-merge tarefas (P1)
- Mover todos os GitHub write flows para um backend seguro (Node/Express + GitHub App or Actions using GitHub App installation token) e substituir client direct calls by backend API.  
- Implement AppCheck enforcement, rotate keys periodically, implement auditing backend logs.

M — Arquivo de referência com patches/commits que o assistente deve aplicar
1. Criar commits separados e atômicos:
   - commit: security: rotate secrets and remove examples (manual step + commit .env.example change)
   - commit: security: migrate GitHub token storage to EncryptedSharedPreferences
   - commit: security: migrate password hashing to PBKDF2 and add migration logic
   - commit: refactor: OkHttp singleton and remove per-call clients
   - commit: ci: add gitleaks scan and pre-commit hook
   - commit: ci: add dependency scan config (Dependabot/Snyk)

N — Permissões e backups
1. Confirmar `android:allowBackup="false"` e revisar any backup_rules.xml/data_extraction_rules to exclude DBs and prefs.

O — Scripts e arquivos que já criei no branch (para referência)
- SECURITY_FIXES/IMMEDIATE_FIXES_AND_MIGRATION.md
- SECURITY_FIXES/PR_BODY.md
- SECURITY_FIXES/replacements.txt
- firebase_rules.json (endurecido)

P — Checklist final para o assistente (para tick-off antes do PR)
- [ ] Token exposto revogado e novo token armazenado em secrets
- [ ] Reescrita de histórico aplicada (git-filter-repo) e push forçado completado
- [ ] Substituições de armazenamento aplicadas (EncryptedSharedPreferences) e migração verificada
- [ ] Hashing de senha substituído e migração compatível implementada
- [ ] OkHttp singleton implementado e código refatorado
- [ ] Logs sensíveis removidos ou mascarados
- [ ] firebase_rules.json testada e publicada em staging
- [ ] App Check integrado e enforcement planejado
- [ ] gitleaks integrado ao pre-commit e CI
- [ ] R8/minify habilitado e build/testes OK
- [ ] PR criado com descrição, reviewers e checklist preenchido

---

Notas finais
- Faça cada alteração em commits separados e bem documentados. Teste cada commit em staging antes de progredir.  
- Coordene a reescrita do histórico com a equipe — ela afeta todos os clones.  

Se quiser, eu posso criar a maioria dos commits automáticos neste branch (implementação de EncryptedSharedPreferences, OkHttp, SecurityUtils PBKDF2) e abrir o PR; confirme se autoriza que eu aplique essas alterações de código no branch `hotfix/security-immediate`.