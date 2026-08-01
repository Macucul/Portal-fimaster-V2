# 🗺️ Mapa Completo de Fluxo, Arquitetura e Nós (App + Robô EA MT5)

Este documento descreve detalhadamente toda a estrutura de nós do Firebase Realtime Database, a sincronização bidirecional entre o **Aplicativo Android (Portal Fimaster)** e o **Robô EA MetaTrader 5 (`fimaster_notificar.mql5`)**, bem como todas as regras de segurança, autenticação e fluxos operacionais.

---

## 📂 1. Estrutura Unificada de Nós no Firebase Realtime Database

Toda a persistência e comunicação em tempo real é estruturada sob a raiz `/dados` do Firebase Realtime Database:

```text
/dados
├── /usuarios
│   └── /{userId}                     --> Dados do utilizador (Perfil, Licença, Senha Hash, Auditoria)
│       ├── /config                   --> Cópia sincronizada da configuração do robô
│       ├── /status                   --> Cópia sincronizada da telemetria do robô
│       └── /eventos                  --> Histórico de eventos e ordens do utilizador
│
├── /indices
│   ├── /telefones
│   │   └── /{numeroLimpo}            --> Mapeamento por Telefone: { "usuario": "{userId}", "mt5": "{ACCOUNT_LOGIN}", "status": "ATIVO" }
│   └── /mt5
│       └── /{ACCOUNT_LOGIN}          --> Índice de posse da conta MT5: { "usuario": "{userId}", "telefone": "{numero}", "nome": "{nome}" }
│
├── /parametros
│   └── /{ACCOUNT_LOGIN}              --> Configurações e parâmetros do Robô EA gravados pelo App (.json)
│
├── /config
│   └── /{ACCOUNT_LOGIN}              --> Espelho ativo de configurações vinculadas à conta MT5
│
├── /status
│   └── /{ACCOUNT_LOGIN}              --> Telemetria em tempo real, estado online/offline e saldo gravados pelo EA
│
└── /eventos
    └── /{ACCOUNT_LOGIN}              --> Histórico de eventos, pings, ordens e transições de estado do EA
```

---

## 🤖 2. Fluxo do Robô EA (`fimaster_notificar.mql5` / MetaTrader 5)

O módulo `fimaster_notificar.mql5` opera de forma **100% automatizada** e extrai os seus nós diretamente do número da conta MT5 ativa no terminal.

### A. Extração Automática do Número da Conta (`ACCOUNT_LOGIN`)
- O robô **não exige** digitação manual do ID da conta ou do utilizador nos inputs.
- O número da conta é extraído nativamente via MQL5:
  ```mql5
  string ACCOUNT_LOGIN_STR = IntegerToString((int)AccountInfoInteger(ACCOUNT_LOGIN));
  ```
- **Inputs necessários no MT5**:
  1. `server_url`: URL Base do Firebase (ex: `https://fimaster-sms-gateway-default-rtdb.firebaseio.com`).
  2. `auth_key`: Senha/Token de Autenticação do Firebase (se configurado).
  3. `GMAIL` / `notific` / `enviar_http`: Opções operacionais de alerta e notificação.

### B. Mapeamento Dinâmico de Endpoints no EA
Com base no `ACCOUNT_LOGIN`, o EA constrói diretamente as URLs do Firebase:
- **Parâmetros**: `https://{server_url}/dados/parametros/{ACCOUNT_LOGIN}.json?auth={auth_key}`
- **Status / Telemetria**: `https://{server_url}/dados/status/{ACCOUNT_LOGIN}.json?auth={auth_key}`
- **Eventos**: `https://{server_url}/dados/eventos/{ACCOUNT_LOGIN}.json?auth={auth_key}`

### C. Ciclo Operacional em Tempo Real (`OnTimer`)
```text
[ Terminal MetaTrader 5 Inicializado (OnInit) ]
          │
          ├──> Extrai ACCOUNT_LOGIN
          ├──> Ativa Timer de 1 Segundo
          └──> Dispara 1º Ping Online e Sincronização
          │
          ▼
[ Loop OnTimer - Executado a cada Segundo ]
          │
          ├──> [ A cada 5 Segundos ] ──> HTTP GET no nó /dados/parametros/{ACCOUNT_LOGIN}.json
          │                              │
          │                              ├──> Lê "EA_ATIVO" (Ativa/Desativa o Robô pelo App)
          │                              └──> Responde gravando status em /dados/status/{ACCOUNT_LOGIN}.json
          │
          ├──> [ A cada 60 Segundos ] ─> Heartbeat / Ping Online via HTTP PUT em /dados/status/{ACCOUNT_LOGIN}.json
          │                              └──> Registra evento "ping" em /dados/eventos/{ACCOUNT_LOGIN}.json
          │
          └──> [ Deteção de Ordens ] ──> Se posição aberta mudar:
                                         └──> Publica evento "posicao_alterada" em /dados/eventos/{ACCOUNT_LOGIN}.json
```

---

## 📱 3. Fluxo do Aplicativo Android (Portal App)

O aplicativo Android gerencia a conta MT5, lê a telemetria gravada pelo robô e altera os seus parâmetros.

### A. Autenticação e Licenciamento
1. **Login de Acesso**: O utilizador insere telefone e senha.
2. **Validação de Hash**: O app valida contra a senha guardada no nó `/dados/usuarios/{userId}` (com suporte a senha mestra `fimaster2026`).
3. **Verificação de Licença**: Confirma se a licença está **ATIVA** e dentro da validade.

### B. Vinculação e Migração da Conta MT5
1. **Validação de Posse**:
   - O app consulta `/dados/indices/mt5/{novoMt5Id}.json`.
   - Se já pertencer a outro utilizador, a alteração é bloqueada.
2. **Migração Completa de Nós ao Trocar MT5**:
   - Ao trocar a conta de `oldMt5Id` para `newMt5Id`, o aplicativo migra os dados em todos os nós:
     - `/dados/parametros/{oldMt5Id}` ──> `/dados/parametros/{newMt5Id}`
     - `/dados/config/{oldMt5Id}` ──> `/dados/config/{newMt5Id}`
     - `/dados/status/{oldMt5Id}` ──> `/dados/status/{newMt5Id}`
     - `/dados/eventos/{oldMt5Id}` ──> `/dados/eventos/{newMt5Id}`
     - Remapeia `/dados/indices/mt5/{newMt5Id}` e remove `{oldMt5Id}`.

### C. Alteração de Parâmetros e Telemetria
1. **Envio de Parâmetros**:
   - Ao alterar configurações no App, o app grava o objeto JSON em `/dados/parametros/{ACCOUNT_LOGIN}.json` e `/dados/config/{ACCOUNT_LOGIN}.json`.
2. **Leitura de Status**:
   - O app efetua polling em `/dados/status/{ACCOUNT_LOGIN}.json`.
   - Exibe em tempo real: **Status Online**, **Robô Ativo/Inativo**, **Saldo Disponível**, **Ativo Operado**, **Servidor** e **Último Ping**.
3. **Histórico de Eventos**:
   - O app lê os logs de `/dados/eventos/{ACCOUNT_LOGIN}.json` e exibe os registros de operações e transições de estado.

---

## 🔄 4. Matriz de Equivalência e Sem Divergência nos Nós

| Caminho no Firebase Database | Gravado Por | Lido Por | Conteúdo Principal / Mapeamento |
| :--- | :--- | :--- | :--- |
| `/dados/indices/mt5/{ACCOUNT_LOGIN}` | App Android | App / Validação | `{ "usuario": "{userId}", "telefone": "{numero}", "nome": "{nome}" }` |
| `/dados/parametros/{ACCOUNT_LOGIN}` | App Android | Robô EA MT5 | `{ "EA_AUTO": true, "lote": 0.01, "buy_take": 100, ... }` |
| `/dados/config/{ACCOUNT_LOGIN}` | App Android | Robô EA / Backup | Objeto com os parâmetros ativos do robô |
| `/dados/status/{ACCOUNT_LOGIN}` | Robô EA MT5 | App Android | `{ "online": true, "ea_ativo": true, "login": "{ACCOUNT_LOGIN}", "saldo_disponivel": 1500.0, ... }` |
| `/dados/eventos/{ACCOUNT_LOGIN}` | Robô EA MT5 | App Android | Feed de eventos `{ "event": "ping" / "posicao_alterada" / "mudanca_estado", ... }` |
| `/dados/usuarios/{userId}` | App Android | App Android | Perfil do cliente, senha hash SHA-256, status da licença e auditoria |

---

*Documentação atualizada e sincronizada sem nenhuma divergência entre o aplicativo Android e o Robô EA MetaTrader 5.*
