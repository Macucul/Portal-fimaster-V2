# Pull Request: security: tighten DB rules, disable cleartext and backups, remove example secrets

## Objetivo

Corrigir problemas críticos de segurança detectados no projeto FiMaster Portal: remoção de segredos expostos, endurecimento das regras do Realtime Database, desativação de cleartext traffic e backups que podem expor dados sensíveis. Este PR aplica correções imediatas (P0) e fornece instruções para migrações e melhorias posteriores (P1/P2).

---

## Alterações incluídas neste branch

- app/src/main/AndroidManifest.xml
  - `android:allowBackup="false"`
  - `android:usesCleartextTraffic="false"`

- .env.example
  - Removido PAT de exemplo; substituído por placeholder para evitar exposição de credenciais.

- firebase_rules.json
  - Regras Realtime Database endurecidas: exigem autenticação (`auth != null`) e `auth.token.admin == true` para writes administrativas.

- SECURITY_FIXES/IMMEDIATE_FIXES_AND_MIGRATION.md
  - Guia completo com diffs, checklist, snippets de código (EncryptedSharedPreferences, OkHttp singleton, PBKDF2, habilitar R8), e comandos para aplicar as mudanças.


---

## Requisitos OBRIGATÓRIOS ANTES DO MERGE (não fazer merge sem completar)

1. Revogar/rotacionar IMEDIATAMENTE qualquer token exposto no repositório (ex.: PAT encontrado em `.env.example` no histórico). Se não rotacionar, qualquer atacante pode usar o token para modificar repositório ou acessar dados.
2. Publicar e testar `firebase_rules.json` em um projeto de staging para validação da política de leitura/escrita. Não forçar em produção sem testes.
3. Confirmar que `.env.example` não contém segredos e que nenhum outro arquivo commitado contém tokens ou secrets.
4. Verificar que `backup_rules.xml` / `data_extraction_rules.xml` excluem arquivos sensíveis do backup.
5. Executar CI builds (assembleDebug e assembleRelease) e testes automatizados locais.

---

## Testes e validação sugeridos (staging)

- Testar leitura de um usuário autenticado e recusar leitura não autenticada.
- Testar escrita de dados de usuário por usuário autenticado (apenas próprio UID) e por admin (custom claim `admin=true`).
- Testar que índices (`/dados/indices`) são lidos apenas por apps autenticados.
- Testar que parâmetros do EA (`/dados/parametros/{mt5Id}`) só podem ser escritos por admins.

---

## Comandos úteis (CLI) para criar o PR usando GitHub CLI (gh)

1. Push do branch:

```bash
git checkout hotfix/security-immediate
git push origin hotfix/security-immediate
```

2. Criar PR com corpo detalhado (usa o arquivo gerado neste branch):

```bash
gh pr create --title "security: tighten DB rules, disable cleartext and backups, remove example secrets" --body-file SECURITY_FIXES/IMMEDIATE_FIXES_AND_MIGRATION.md --base main --head hotfix/security-immediate
```

Ou crie pela UI do GitHub: Compare & pull request → cole o corpo do arquivo `SECURITY_FIXES/IMMEDIATE_FIXES_AND_MIGRATION.md` como descrição.

---

## Ações recomendadas PÓS-MERGE (prioridade P1/P2)

1. Remover do cliente todas as operações sensíveis que usam tokens (ex.: commits via GitHub API). Criar um backend seguro que faca essas operações servidor-side.
2. Integrar EncryptedSharedPreferences / Android Keystore para armazenar tokens localmente (se necessário mantê-los no app).
3. Habilitar Firebase App Check e forçar enforcement no Console (usar Play Integrity ou reCAPTCHA conforme apropriado).
4. Habilitar minify/R8 para release e ajustar `proguard-rules.pro` para manter classes necessárias do Firebase/Room/Moshi.
5. Implementar auditoria e logging de writes administrativas (quem/quando) no backend.
6. Configurar Dependabot/Snyk ou scanner SCA para monitorar vulnerabilidades em dependências.

---

## Checklist pré-merge (para reviewers)

- [ ] Token exposto revogado/rotacionado
- [ ] Regras do Firebase testadas em staging
- [ ] CI build passando (assembleDebug/assembleRelease)
- [ ] `.env.example` sem segredos e backup_rules excluem dados sensíveis
- [ ] Review de código por equipe de segurança e lead Android

---

## Observações finais

- NÃO FAZER MERGE até a revogação/rotacionamento do token exposto — é a ação mais crítica.
- Este PR aplica apenas correções P0; outras melhorias P1/P2 estão listadas e devem ser planejadas em sprints subsequentes.

Se quiser, eu posso criar o PR automaticamente (se autorizado) ou gerar um patch `.patch` com as mudanças caso prefira aplicar manualmente.