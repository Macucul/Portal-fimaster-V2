# 🤖 Nós do Firebase Realtime Database Acessados Exclusivamente pelo Robô EA (MetaTrader 5 / MQL5)

Este documento lista de forma detalhada **exclusivamente todos os nós do Firebase que o Robô EA no MetaTrader 5 (MT5)** acessa para **LEITURA (Download de configurações/comandos)** e **ESCRITA (Upload de telemetria, status e eventos)**.

---

## 📌 Visão Geral da Arquitetura de Comunicação do EA MT5

No MetaTrader 5, a comunicação com o Firebase Realtime Database é realizada via requisições HTTPS REST (`WebRequest` nativo do MQL5) apontando para os endpoints `.json`:

- **URL Base**: `https://<SEU-PROJETO-FIREBASE>-default-rtdb.firebaseio.com`
- **Autenticação**: `?auth=<FIREBASE_AUTH_KEY_OU_TOKEN>` (ou cabeçalho `Authorization: Bearer <TOKEN>`)

```
  ┌────────────────────────────────────────────────────────┐
  │                 MetaTrader 5 (Robô EA)                 │
  └───────────▲────────────────────────────────┬───────────┘
              │ 1. LEITURA                     │ 2. ESCRITA
              │ (GET Parâmetros & Comandos)    │ (PUT/PATCH/POST Telemetria & Eventos)
              │                                ▼
  ┌───────────┴────────────────────────────────────────────┐
  │              Firebase Realtime Database                │
  └────────────────────────────────────────────────────────┘
```

---

## 📥 1. Nós que o MT5 EA Acessa para LEITURA (READ / GET)

O EA consulta estes nós periodicamente (no `OnInit()`, no início de cada barra ou em timer `OnTimer()`) para saber se o usuário alterou configurações no App ou enviou comandos remotos.

### 🔹 1.1. Parâmetros Operacionais Ativos
- **Caminho do Nó**: `/dados/parametros/{ACCOUNT_LOGIN}.json`
- **Método HTTP no MQL5**: `GET`
- **Finalidade**: O EA lê as configurações e parâmetros de trading enviados pelo aplicativo ou painel administrativo para a conta logada (`ACCOUNT_LOGIN` = número da conta MT5, ex: `859423`).

#### Exemplo de Payload JSON lido pelo EA:
```json
{
  "conta_mt5": "859423",
  "ativo": "EURUSD",
  "timeframe": "M15",
  "tamanho_lote": 0.05,
  "stop_loss_pontos": 150.0,
  "take_profit_pontos": 300.0,
  "trailing_stop_ativo": true,
  "trailing_stop_distancia": 100.0,
  "trailing_stop_degrau": 20.0,
  "break_even_ativo": true,
  "break_even_gatilho": 80.0,
  "break_even_protecao": 10.0,
  "magic_number": 123456,
  "slippage_maximo": 10,
  "horario_inicio_operacao": "09:00",
  "horario_fim_operacao": "17:30",
  "horario_fechamento_forcado": "18:00",
  "maximo_ordens_dia": 5,
  "meta_lucro_diario": 250.0,
  "limite_perda_diaria": 100.0,
  "trava_fechamento_vela": true,
  "limite_pontos_vela": 50.0,
  "trava_equador_ativo": true,
  "trava_gerenciamento_diario": true,
  "trava_gerenciamento_semanal": true,
  "timestamp_envio": 1740000000
}
```

---

### 🔹 1.2. Comando de Captura de Tela Remota (Screenshot)
- **Caminho do Nó**: `/dados/eventos/{ACCOUNT_LOGIN}/capturar_tela.json`
- **Método HTTP no MQL5**: `GET`
- **Finalidade**: O EA monitora este nó para saber se o aplicativo solicitou um screenshot imediato do gráfico (`ChartScreenShot()`).
- **Gatilho**: Se `solicitado: true` ou `pendente: true`, o EA tira a foto, converte para Base64 e envia de volta para `/dados/eventos/{ACCOUNT_LOGIN}/captura_tela.json`, zerando a flag em seguida.

#### Exemplo de Payload JSON lido pelo EA:
```json
{
  "solicitado": true,
  "largura": 1280,
  "altura": 720,
  "solicitado_em": "2026-08-28 10:15:00",
  "solicitado_por": "App Android"
}
```

---

## 📤 2. Nós que o MT5 EA Acessa para ESCRITA (WRITE / PUT / POST / PATCH)

O EA envia dados para o Firebase nos eventos operacionais (`OnInit()`, `OnDeinit()`, `OnTick()`, `OnTrade()`, `OnTradeTransaction()`, `OnTimer()`).

---

### 🔹 2.1. Status e Telemetria em Tempo Real (Heartbeat / Status)
- **Caminho do Nó**: `/dados/status/{ACCOUNT_LOGIN}.json`
- **Método HTTP no MQL5**: `PUT` ou `PATCH`
- **Frequência**: A cada X segundos (via `OnTimer()`) ou a cada alteração significativa de saldo/equidade.
- **Finalidade**: Informa ao App o estado atual do robô, conexão, licença, saldo e desempenho do dia.

#### Exemplo de Payload JSON enviado pelo EA:
```json
{
  "conta_mt5": "859423",
  "nome_corretora": "ICMarkets-Live",
  "nome_titular": "Cliente FiMaster",
  "servidor": "ICMarketsSC-Live01",
  "versao_ea": "3.5.0",
  "status_conexao": "ONLINE",
  "auto_trading_ativo": true,
  "dlls_permitidas": true,
  "saldo_balance": 5240.50,
  "patrimonio_equity": 5380.20,
  "margem_livre": 4980.00,
  "nivel_margem_pct": 1250.0,
  "lucro_flutuante_aberto": 139.70,
  "lucro_fechado_hoje": 85.00,
  "total_operacoes_hoje": 4,
  "total_vitorias_hoje": 3,
  "total_derrotas_hoje": 1,
  "taxa_acerto_hoje_pct": 75.0,
  "posicoes_abertas_qtd": 2,
  "magic_number": 123456,
  "ultimo_ping": "2026-08-28 14:35:10",
  "timestamp": 1740000000
}
```

---

### 🔹 2.2. Eventos Operacionais Gerais do EA
- **Caminho do Nó**: `/dados/eventos/{ACCOUNT_LOGIN}.json` ou `/dados/eventos/{ACCOUNT_LOGIN}/{TIPO_EVENTO}.json`
- **Método HTTP no MQL5**: `POST` (para histórico/lista) ou `PUT` (para último evento do tipo)

#### Sub-nós específicos de eventos gravados pelo EA:

| Sub-nó | Gatilho MQL5 | Finalidade |
| :--- | :--- | :--- |
| `/dados/eventos/{ACCOUNT_LOGIN}/ordem_executada.json` | `OnTrade()` / `OnTradeTransaction()` | Nova ordem aberta no mercado |
| `/dados/eventos/{ACCOUNT_LOGIN}/ordem_modificada.json` | `OnTradeTransaction()` | Alteração de Stop Loss ou Take Profit |
| `/dados/eventos/{ACCOUNT_LOGIN}/ordem_fechada.json` | `OnTradeTransaction()` | Posição liquidada com lucro/prejuízo |
| `/dados/eventos/{ACCOUNT_LOGIN}/ordem_nao_executada.json` | Rejeição da corretora | Ordem rejeitada por spread, margem ou mercado fechado |
| `/dados/eventos/{ACCOUNT_LOGIN}/erro_ordem.json` | `GetLastError()` | Erros de envio de ordens |
| `/dados/eventos/{ACCOUNT_LOGIN}/posicao_alterada.json` | Mudança de volume/lote | Posição aumentada ou reduzida |
| `/dados/eventos/{ACCOUNT_LOGIN}/mudanca_estado.json` | Alteração de estado do robô | Pausado, aguardando sinal, em operação |
| `/dados/eventos/{ACCOUNT_LOGIN}/mudanca_equador.json` | Linha de Equador rompida | Preço cruzou a linha média/equador |
| `/dados/eventos/{ACCOUNT_LOGIN}/sessao_inicio.json` | Horário de início atingido | Robô iniciou o ciclo de trading do dia |
| `/dados/eventos/{ACCOUNT_LOGIN}/sessao_fim.json` | Horário de fim atingido | Robô finalizou o ciclo de trading |
| `/dados/eventos/{ACCOUNT_LOGIN}/ping.json` | `OnTimer()` | Confirmação de atividade (Keep-Alive) |
| `/dados/eventos/{ACCOUNT_LOGIN}/captura_tela.json` | Após `ChartScreenShot()` | Envio da imagem do gráfico em Base64 |

---

### 🔹 2.3. Notificações e Travas MQL5 (Catálogo FiMaster)
- **Caminho do Nó**: `/dados/eventos/{ACCOUNT_LOGIN}/notificacao_mql5.json`
- **Método HTTP no MQL5**: `POST` ou `PUT`
- **Finalidade**: Envio das mensagens de travas disparadas pela função `enviarnotificacvao(notifica)` no código MQL5.

#### Exemplo de Payload JSON de Notificação enviado pelo EA:
```json
{
  "gid": "NOTIF_859423_1740000123",
  "conta_mt5": "859423",
  "tipo_evento": "notificacao_venda_travada_fechamento",
  "categoria": "FECHAMENTO DE VELA",
  "direcao": "SELL",
  "mensagem": "ordem de venda travado por fechamento : 12.5 > 10.0: pontos de entrada",
  "codigo_erro": 0,
  "trava_ativa": true,
  "timestamp": "2026-08-28 14:35:12"
}
```

---

### 🔹 2.4. Relatório Financeiro e Curva de Patrimônio
- **Caminho do Nó**: `/dados/eventos/historico_patrimonio/{ACCOUNT_LOGIN}.json`
- **Método HTTP no MQL5**: `POST`
- **Finalidade**: Armazenar pontos de equity e balance para montagem do gráfico de evolução patrimonial no App.

#### Exemplo de Payload JSON:
```json
{
  "timestamp": 1740000000,
  "data_formatada": "2026-08-28 14:30:00",
  "balance": 5240.50,
  "equity": 5380.20,
  "posicoes_abertas": 2
}
```

---

## 🔒 3. Regras de Segurança do Firebase Exclusivas para os Nós do EA MT5

Copie este bloco no seu **Firebase Console > Realtime Database > Regras (`database.rules.json`)** para garantir que o MT5 e o App tenham permissão de leitura e escrita:

```json
{
  "rules": {
    "dados": {
      // 1. PARÂMETROS E CONFIGURAÇÕES: App escreve -> EA MT5 lê
      "parametros": {
        "$mt5AccountId": {
          ".read": "auth != null",
          ".write": "auth != null"
        }
      },
      "config": {
        "$mt5AccountId": {
          ".read": "auth != null",
          ".write": "auth != null"
        }
      },

      // 2. STATUS EM TEMPO REAL: EA MT5 escreve -> App lê
      "status": {
        "$mt5AccountId": {
          ".read": "auth != null",
          ".write": "auth != null"
        }
      },

      // 3. EVENTOS, TRAVAS E COMANDOS: EA MT5 escreve eventos / App envia comandos
      "eventos": {
        "$mt5AccountId": {
          ".read": "auth != null",
          ".write": "auth != null",
          "capturar_tela": {
            ".read": "auth != null",
            ".write": "auth != null"
          },
          "captura_tela": {
            ".read": "auth != null",
            ".write": "auth != null"
          },
          "notificacao_mql5": {
            ".read": "auth != null",
            ".write": "auth != null"
          }
        },
        "historico_patrimonio": {
          "$mt5AccountId": {
            ".read": "auth != null",
            ".write": "auth != null"
          }
        }
      }
    }
  }
}
```

---

## 💻 4. Exemplo de Código MQL5 para Leitura e Escrita nos Nós do Firebase

### 📥 4.1. Função MQL5 para Ler Parâmetros (GET)
```mql5
string LerParametrosFirebase(string firebaseUrl, string authKey, long contaMt5)
{
   string url = firebaseUrl + "/dados/parametros/" + IntegerToString(contaMt5) + ".json";
   if(StringLen(authKey) > 0)
      url += "?auth=" + authKey;
      
   char post[], result[];
   string result_headers;
   int res = WebRequest("GET", url, "", 5000, post, result, result_headers);
   
   if(res == 200)
   {
      return CharArrayToString(result, 0, WHOLE_ARRAY, CP_UTF8);
   }
   else
   {
      Print("Erro ao ler parametros do Firebase. HTTP Code: ", res, " Erro: ", GetLastError());
      return "";
   }
}
```

### 📤 4.2. Função MQL5 para Enviar Status (PUT/PATCH)
```mql5
bool EnviarStatusFirebase(string firebaseUrl, string authKey, long contaMt5, string jsonStatus)
{
   string url = firebaseUrl + "/dados/status/" + IntegerToString(contaMt5) + ".json";
   if(StringLen(authKey) > 0)
      url += "?auth=" + authKey;
      
   char post[], result[];
   StringToCharArray(jsonStatus, post, 0, StringLen(jsonStatus), CP_UTF8);
   
   string headers = "Content-Type: application/json\r\n";
   string result_headers;
   
   int res = WebRequest("PUT", url, headers, 5000, post, result, result_headers);
   if(res == 200)
   {
      return true;
   }
   else
   {
      Print("Erro ao enviar status para o Firebase. HTTP Code: ", res, " Erro: ", GetLastError());
      return false;
   }
}
```

### 📤 4.3. Função MQL5 para Disparar Notificação / Evento (POST)
```mql5
bool EnviarEventoFirebase(string firebaseUrl, string authKey, long contaMt5, string subTipo, string jsonEvento)
{
   string url = firebaseUrl + "/dados/eventos/" + IntegerToString(contaMt5) + "/" + subTipo + ".json";
   if(StringLen(authKey) > 0)
      url += "?auth=" + authKey;
      
   char post[], result[];
   StringToCharArray(jsonEvento, post, 0, StringLen(jsonEvento), CP_UTF8);
   
   string headers = "Content-Type: application/json\r\n";
   string result_headers;
   
   int res = WebRequest("PUT", url, headers, 5000, post, result, result_headers);
   return (res == 200);
}
```

---

## 📋 Resumo Rápido em Tabela dos Nós do MT5

| Nó no Firebase | Operação do MT5 EA | Finalidade | Frequência |
| :--- | :--- | :--- | :--- |
| `/dados/parametros/{ACCOUNT_LOGIN}.json` | **LEITURA (GET)** | Baixar Lote, SL/TP, Trailing, Horários e Travas | Início / Timer |
| `/dados/config/{ACCOUNT_LOGIN}.json` | **LEITURA (GET)** | Espelho de configurações operacionais | Início / Timer |
| `/dados/eventos/{ACCOUNT_LOGIN}/capturar_tela.json` | **LEITURA (GET)** | Checar se o App pediu screenshot remoto | Timer (segundos) |
| `/dados/status/{ACCOUNT_LOGIN}.json` | **ESCRITA (PUT)** | Saldo, Equidade, Conexão, Lucro e Versão | Timer (1 a 5s) |
| `/dados/eventos/{ACCOUNT_LOGIN}/ordem_executada.json` | **ESCRITA (PUT/POST)** | Nova posição/ordem aberta | No disparo da ordem |
| `/dados/eventos/{ACCOUNT_LOGIN}/ordem_fechada.json` | **ESCRITA (PUT/POST)** | Fechamento de posição com lucro/prejuízo | No fechamento |
| `/dados/eventos/{ACCOUNT_LOGIN}/notificacao_mql5.json` | **ESCRITA (PUT/POST)** | Travas de venda/compra, equador, gerenciamento | No bloqueio de sinal |
| `/dados/eventos/{ACCOUNT_LOGIN}/captura_tela.json` | **ESCRITA (PUT)** | Envio da imagem Base64 do gráfico | Após solicitação |
| `/dados/eventos/historico_patrimonio/{ACCOUNT_LOGIN}.json` | **ESCRITA (POST)** | Histórico de pontos de curva de patrimônio | Periódico |
