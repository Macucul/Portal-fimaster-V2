# Correções de Segurança Imediatas e Guia de Migração

Este arquivo contém diffs e passos práticos para corrigir os problemas críticos que identifiquei: segredos expostos, regras do Realtime Database inseguras, uso de cleartext, backups, armazenamento inseguro de tokens, e outras recomendações.

Atenção: executem as ações em um branch separado e revisem antes de merge. Rotacionem qualquer token/GitHub PAT imediatamente.

---

## 1) Resumo das alterações que proponho agora (P0)
- Atualizar `AndroidManifest.xml`: desativar `usesCleartextTraffic` e `allowBackup`.
- Atualizar `.env.example`: remover token real, deixar placeholder.
- Substituir `firebase_rules.json` por regras seguras (exemplo) e orientar deploy.
- Incluir checklist e snippets para seu assistente aplicar: EncryptedSharedPreferences, OkHttp singleton, PBKDF2, habilitar minify (R8) no release.

---

## 2) Patch sugerido — AndroidManifest.xml
Substituir no arquivo `app/src/main/AndroidManifest.xml` o bloco da aplicação para garantir:
- `android:allowBackup="false"`
- `android:usesCleartextTraffic="false"`

Diff sugerido (aplicar manualmente ou via patch):

```diff
--- a/app/src/main/AndroidManifest.xml
+++ b/app/src/main/AndroidManifest.xml
@@
-    <application
-        android:allowBackup="true"
-        android:dataExtractionRules="@xml/data_extraction_rules"
-        android:fullBackupContent="@xml/backup_rules"
-        android:icon="@mipmap/ic_launcher"
-        android:label="@string/app_name"
-        android:roundIcon="@mipmap/ic_launcher_round"
-        android:supportsRtl="true"
-        android:usesCleartextTraffic="true"
-        android:theme="@style/Theme.MyApplication">
+    <application
+        android:allowBackup="false"
+        android:dataExtractionRules="@xml/data_extraction_rules"
+        android:fullBackupContent="@xml/backup_rules"
+        android:icon="@mipmap/ic_launcher"
+        android:label="@string/app_name"
+        android:roundIcon="@mipmap/ic_launcher_round"
+        android:supportsRtl="true"
+        android:usesCleartextTraffic="false"
+        android:theme="@style/Theme.MyApplication">
```

Notas:
- Ajuste `backup_rules.xml`/`data_extraction_rules.xml` para excluir arquivos sensíveis (banco, prefs) se necessário.

---

## 3) Patch sugerido — .env.example
Remover tokens reais de todos os arquivos `.env.example`. Substituir por placeholders.

Exemplo: `.env.example` atual contém uma linha com token GitHub. Substitua por:

```diff
--- a/.env.example
+++ b/.env.example
@@
-GITHUB_DEFAULT_TOKEN=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
+GITHUB_DEFAULT_TOKEN=REPLACE_WITH_GITHUB_PAT
```

Ação imediata: rotacionar (revoke) o token existente no GitHub e não reutilizar o mesmo.

---

## 4) Patch sugerido — Regras do Realtime Database (firebase_rules.json)
Substituir o conteúdo atual por regras que exijam autenticação e custom claims para ações administrativas.

Arquivo proposto: `firebase_rules.json` (exemplo — adaptar conforme estrutura de dados do projeto):

```json
{
  "rules": {
    "dados": {
      "usuarios": {
        "$userId": {
          ".read": "auth != null && (auth.uid == $userId || auth.token.admin == true)",
          ".write": "auth != null && (auth.uid == $userId || auth.token.admin == true)"
        }
      },
      "indices": {
        ".read": "auth != null",
        ".write": "auth != null && auth.token.admin == true"
      },
      "parametros": {
        "$mt5Id": {
          ".read": "auth != null && (auth.token.admin == true || root.child('dados/indices/mt5/' + $mt5Id + '/allowRead').val() == true)",
          ".write": "auth != null && auth.token.admin == true"
        }
      },
      "versao": {
        ".read": "auth != null",
        ".write": "auth != null && auth.token.admin == true"
      }
    }
  }
}
```

Notas de deploy:
- Use Firebase CLI para publicar regras: `firebase deploy --only database:rules --project <PROJECT_ID>`
- Antes de forçar a produção, testar em um projeto de staging.
- Adicionar App Check enforcement no Console Firebase (após integrar SDK nos apps).

---

## 5) Checklist passo-a-passo para seu assistente aplicar essas correções
1. Criar um branch "hotfix/security-immediate" a partir de `main`.
   - git checkout -b hotfix/security-immediate
2. Aplicar as substituições no AndroidManifest.xml e .env.example e commitar:
   - git add app/src/main/AndroidManifest.xml .env.example
   - git commit -m "security: disable cleartext and backups; remove example secrets"
3. Substituir `firebase_rules.json` com o arquivo proposto e commitar.
   - git add firebase_rules.json
   - git commit -m "security: tighten realtime database rules (require auth + admin claims)"
4. Abrir PR para revisão e não fazer merge sem coordenação do time. Somente depois de rotacionar tokens.
5. Rotacionar token GitHub exposto (instrua o dono do token a revogar no GitHub).
6. Atualizar CI/CD secrets com novas credenciais, não adicionar tokens a arquivos versionados.

---

## 6) Snippets de código seguros (para implementar após as correções P0)

A. EncryptedSharedPreferences (armazenar token seguro)

```kotlin
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

fun createSecurePrefs(context: Context) = run {
  val masterKey = MasterKey.Builder(context)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
    .build()

  EncryptedSharedPreferences.create(
    context,
    "secure_prefs",
    masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
  )
}

// Exemplo de uso:
// val prefs = createSecurePrefs(appContext)
// prefs.edit().putString("GITHUB_TOKEN", token).apply()
```

B. OkHttpClient singleton (reusar conexões e centralizar configuração)

```kotlin
object HttpClient {
  val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .build()
}
```

C. PBKDF2 para derivação de senha (substituir SHA-256 simples)

```kotlin
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

fun generateSalt(size: Int = 16): ByteArray {
  val salt = ByteArray(size)
  SecureRandom().nextBytes(salt)
  return salt
}

fun hashPasswordPBKDF2(password: CharArray, salt: ByteArray, iterations: Int = 100_000, keyLength: Int = 256): String {
  val spec = PBEKeySpec(password, salt, iterations, keyLength)
  val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
  val key = skf.generateSecret(spec).encoded
  return android.util.Base64.encodeToString(key, android.util.Base64.NO_WRAP)
}
```

D. Habilitar minify/proguard para release (app/build.gradle.kts)

```diff
--- a/app/build.gradle.kts
+++ b/app/build.gradle.kts
@@
-    release {
-      isCrunchPngs = false
-      isMinifyEnabled = false
-      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
-      signingConfig = signingConfigs.getByName("release")
-    }
+    release {
+      isCrunchPngs = false
+      isMinifyEnabled = true
+      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
+      signingConfig = signingConfigs.getByName("release")
+    }
```

Observação: testar cuidadosamente para garantir que nada crítico seja ofuscado indevidamente (adicionar regras -keep conforme necessidade para Firebase, Room e Moshi codegen).

---

## 7) Tarefas adicionais (P1/P2) — a serem executadas após P0
- Implementar backend seguro que faz commits no GitHub e atualiza Firebase (mover lógica sensível para servidor). O app deve chamar o backend via HTTPS autenticado.
- Habilitar Firebase App Check e forçar enforcement (requer integração SDK em apps e EAs — ou adaptar EA para comunicar via backend)
- Revisar e migrar armazenamento local de tokens para Keystore/EncryptedSharedPreferences.
- Substituir SHA-256 puro por PBKDF2/Argon2 para senhas.
- Remover qualquer fallback que use deviceUid como UID lever for auth decisions.
- Ativar Dependabot/Snyk e corrigir dependências vulneráveis.

---

## 8) Instruções para aplicar automaticamente (opcional)
Se seu assistente preferir aplicar automaticamente as alterações acima via script, posso gerar um patch `git apply` e instruções detalhadas. Posso criar os arquivos de patch aqui no repositório se autorizar.

---

## 9) Contato e próximos passos
Se quiser, gero:
- O patch (.patch) que altera os três arquivos imediatamente.
- Um PR com as mudanças aplicadas (precisa de autorização para criar branch/PR).
- Um script de migração para mover tokens do Room para EncryptedSharedPreferences.

Diga qual opção prefere: "gerar patch" ou "criar PR" (posso só gerar os arquivos e instruções; criar PR requer permissão adicional do repositório).