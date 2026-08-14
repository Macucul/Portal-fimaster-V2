# Mapa de Eventos e Status do Aplicativo (MetaTrader 5 / EA Fimaster)

Este documento descreve detalhadamente a estrutura de todos os eventos e atualizações de status transmitidos pelo robô EA Fimaster (MQL5) para o Firebase e como eles são processados, exibidos em cartões ou silenciados no aplicativo Android.

---

## 📌 Estrutura Geral dos Nós no Firebase Realtime Database

- **Eventos:** `/dados/eventos/{ACCOUNT_LOGIN}.json`
- **Status de Conectividade do EA:** `/dados/status/{ACCOUNT_LOGIN}.json`

---

## 📂 1. Nós e Eventos de Notificação (Aba de Eventos)

Os eventos abaixo geram **Cartões Visuais** na Aba de Eventos e acionam leitura por sintetizador de voz (TTS) quando configurado.

### 1.1 `ordem_executada`
- **Nó Firebase:** `/dados/eventos/{ACCOUNT_LOGIN}/ordem_executada.json`
- **Descrição:** Disparado no momento em que uma ordem de Compra ou Venda é executada com sucesso no MT5.
- **Payload JSON:**
```json
{
  "event": "ordem_executada",
  "tipo": "COMPRA", // ou "VENDA"
  "symbol": "EURUSD",
  "ticket": 12345678,
  "price": 1.08500,
  "volume": 0.10,
  "sl": 1.08200,
  "tp": 1.09000,
  "alvo_mt": 500.00,
  "protecao_mt": 300.00,
  "lucro_pct": 1.50,
  "perda_pct": 0.80,
  "login": "10987654",
  "timestamp": 1723280000,
  "msg": "📈 Ordem de Compra executada! Bilhete #12345678"
}
```
- **Descrição no Cartão Visual:**
  - **Badge Tipo:** Tag verde para `COMPRA` com ícone 📈 ou vermelha para `VENDA` com ícone 📉.
  - **Cabeçalho:** Bilhete `#ticket` + Ativo (`symbol`).
  - **Detalhes:** Preço de Entrada, Volume (Lote), Stop Loss (SL), Take Profit (TP), Metas de Lucro/Proteção e porcentagens.

---

### 1.2 `ordem_modificada`
- **Nó Firebase:** `/dados/eventos/{ACCOUNT_LOGIN}/ordem_modificada.json`
- **Descrição:** Disparado quando o Stop Loss ou Take Profit de uma ordem aberta é ajustado pelo EA ou Break-even/Trailing Stop.
- **Payload JSON:**
```json
{
  "event": "ordem_modificada",
  "tipo": "COMPRA", // ou "VENDA"
  "symbol": "EURUSD",
  "ticket": 12345678,
  "novo_sl": 1.08350,
  "novo_tp": 1.09000,
  "login": "10987654",
  "timestamp": 1723280000,
  "msg": "✅ Ordem Compra #12345678 modificada! Novo SL: 1.08350"
}
```
- **Descrição no Cartão Visual:**
  - **Badge Tipo:** Tag indicadora de modificação de parâmetros de risco.
  - **Conteúdo:** Bilhete alterado, Novo SL e Novo TP destacados com indicação de reajuste do trailing/proteção.

---

### 1.3 `erro_ordem` /
- **Nó Firebase:** `/dados/eventos/{ACCOUNT_LOGIN}/erro_ordem.json`
- **Descrição:** Registra falhas, erros de margem ou rejeições do servidor ao tentar enviar/modificar uma ordem no MT5.
- **Payload JSON:**
```json
{
  "event": "erro_ordem",
  "tipo": "COMPRA", // ou "VENDA"
  "symbol": "EURUSD",
  "erro_code": 10013,
  "login": "10987654",
  "timestamp": 1723280000,
  "msg": "❌ Falha ao enviar ordem de compra. Erro: 10013"
}
```
- **Descrição no Cartão Visual:**
  - **Estilo:** Cartão com destaque em tom de alerta/erro vermelho.
  - **Conteúdo:** Código de Erro MQL5, Operação afetada e mensagem descritiva do motivo da falha.

---

### 1.4 `relatorio_financeiro`
- **Nó Firebase:** `/dados/eventos/{ACCOUNT_LOGIN}/relatorio_financeiro.json`
- **Descrição:** Envia resumo diário e semanal do desempenho financeiro, lucros, perdas e metas.
- **Payload JSON:**
```json
{
  "event": "relatorio_financeiro",
  "symbol": "EURUSD",
  "data": "10/08/2026",
  "hora": "18:00:00",
  "motivacao": "Meta Diária Atingida",
  "moeda": "USD",
  "diario_status": "POSITIVO",
  "diario_valor": 150.25,
  "diario_pct": 1.50,
  "semanal_status": "POSITIVO",
  "semanal_valor": 620.00,
  "semanal_pct": 6.20,
  "resumo": "Excelente desempenho mantendo o risco sob controle.",
  "timestamp": 1723280000
}
```
- **Descrição no Cartão Visual:**
  - **Design:** Painel executivo financeiro com métricas diárias e semanais.
  - **Conteúdo:** Saldo ganho/perdido no dia e na semana ($ e %), status financeiro, texto motivacional e resumo descritivo.

---

### 1.5 `inicializacao`
- **Nó Firebase:** `/dados/eventos/{ACCOUNT_LOGIN}/inicializacao.json`
- **Descrição:** Registrado no boot ou re-inicialização do EA no terminal MetaTrader 5.
- **Payload JSON:**
```json
{
  "event": "inicializacao",
  "symbol": "EURUSD",
  "login": "10987654",
  "server": "ICMarkets-Live01",
  "timeframe": "M15",
  "currency": "USD",
  "timestamp": 1723280000
}
```
- **Descrição no Cartão Visual:**
  - **Conteúdo:** Informa a inicialização do robô, Servidor da Corretora, Ativo, Timeframe e Moeda da conta.

---

### 1.6 `sessao_inicio` e `sessao_fim`
- **Nó Firebase:** `/dados/eventos/{ACCOUNT_LOGIN}/sessao_inicio.json`
- **Descrição:** Marca a abertura e o encerramento de janelas operacionais/sessões no mercado.
- **Payload JSON (Início):**
```json
{
  "event": "sessao_inicio",
  "sessao": "Sessão Londres/NY",
  "symbol": "EURUSD",
  "hora_inicio": "09:00",
  "hora_fim": "17:00",
  "timestamp": 1723280000
}
```
- **Payload JSON (Fim):**
- **Nó Firebase:** `/dados/eventos/{ACCOUNT_LOGIN}/sessao_fim.json`

```json
{
  "event": "sessao_fim",
  "sessao": "Sessão Londres/NY",
  "symbol": "EURUSD",
  "hora_fim": 17,
  "minuto_fim": 0,
  "timestamp": 1723280000
}
```
- **Descrição no Cartão Visual:**
  - **Conteúdo:** Nome da sessão, horários programados de início/término e indicação de janela operacional ativa ou encerrada.

---

### 1.7 `mudanca_estado`
- **Nó Firebase:** `/dados/eventos/{ACCOUNT_LOGIN}/mudanca_estado.json`
- **Descrição:** Transição de estado de inteligência/execução do robô (Ex: ESTADO DE EXECUCAO, ESTADO DE CANAL, ESTADO DE PRECOS).
- **Payload JSON:**
```json
{
  "event": "mudanca_estado",
  "sistema": "ESTADO DE EXECUCAO",
  "login": "10987654",
  "symbol": "EURUSD",
  "anterior": "NEUTRO",
  "novo": "TENDENCIA_ALTA",
  "discriçao": "descNovo",
  "timestamp": 1723280000
}
```
- **Descrição no Cartão Visual:**
  - **Conteúdo:** Nome do Sistema, Estado Anterior vs. Novo Estado com direcionamento gráfico de transição.

---

### 1.8 `mudanca_equador`
- **Nó Firebase:** `/dados/eventos/{ACCOUNT_LOGIN}/.json`
- **Descrição:** Alerta quando a linha ou zona de Equador do gráfico é alterada.
- **Payload JSON:**
```json
{
  "event": "mudanca_equador",
  "symbol": "EURUSD",
  "anterior": "ZONA_COMPRA",
  "novo": "ZONA_NEUTRA",
  "msg": "Equador alterado de ZONA_COMPRA para ZONA_NEUTRA",
  "timestamp": 1723280000
}
```
- **Descrição no Cartão Visual:**
  - **Conteúdo:** Alteração de zona Equador, nível anterior e novo nível do gráfico.

---

### 1.9 `posicao_alterada`
- **Nó Firebase:** `/dados/eventos/{ACCOUNT_LOGIN}/posicao_alterada.json`
- **Descrição:** Informa alterações de posições em aberto na conta MT5.
- **Payload JSON:**
```json
{
  "event": "posicao_alterada",
  "login": "10987654",
  "symbol": "EURUSD",
  "tem_posicao": "true",
  "msg": "Posição mantida em EURUSD",
  "timestamp": 1723280000
}
```
- **Tratamento e Exibição no App:**
  - O aplicativo retém **apenas o evento mais recente** de posição alterada por Conta + Símbolo, evitando poluição na lista.

---

### 1.10 `ordem_não_executada`
- **Nó Firebase:** `/dados/eventos/{ACCOUNT_LOGIN}/ordem_não_executada.json`
- **Descrição:** Notificações gerais e avisos personalizados enviados do script MQL5.
- **Payload JSON:**
```json
{
  "event": "ordem_não_executada",
  "login": 10987654,
  "symbol": "EURUSD",
  "msg": "Aviso do sistema MQL5: volatilidade elevada detectada.",
  "timestamp": 1723280000
}
```
-- **Descrição no Cartão Visual:**
  - **Estilo:** Cartão com destaque em tom de alerta/erro vermelho.
  - **Conteúdo:** Código de Erro MQL5, Operação afetada e mensagem descritiva do motivo da falha.

---

## 🔕 2. Eventos Silenciados (Sem Cartão / Sem Sintetizador de Voz)

### 2.1 `ping`
- **Nó Firebase:** `/dados/eventos/{ACCOUNT_LOGIN}.json`
- **Descrição:** Evento periódico transmitido pelo EA a cada minuto para registrar atividade no banco de dados.
- **Payload JSON:**
```json
{
  "event": "ping",
  "symbol": "EURUSD",
  "login": "10987654",
  "msg": "EA Fimaster online e ativo.",
  "ea_ativo": "true",
  "tem_posicao": "false",
  "fuso_horario": -3,
  "fuso_texto": "UTC-3",
  "servidor": "ICMarkets-Live01",
  "saldo_disponivel": 5000.00,
  "timestamp": 1723280000
}
```
- **Comportamento no App:**
  - 🤫 **Silenciado:** **NÃO exibe cartão** na aba de eventos.
  - 🔇 **Sem áudio:** Ignorado pelo motor de síntese de voz (TTS).
  - 🔄 **Fluxo Interno:** Atualiza apenas os dados de conectividade em tempo real.

---

## 🔄 3. Nó de Status em Tempo Real

### 3.1 Status do EA / Conectividade
- **Nó Firebase:** `/dados/status/{ACCOUNT_LOGIN}.json`
- **Descrição:** Nó atualizado continuamente para indicar se o robô está Online, Saldo Disponível, Servidor e Fuso Horário.
- **Payload JSON:**
```json
{
  "online": true,
  "ea_ativo": true,
  "config_sync": true,
  "last_config_sync": 1723280000,
  "last_ping": 1723280000,
  "fuso_horario": -3,
  "fuso_texto": "UTC-3",
  "symbol": "EURUSD",
  "tem_posicao": false,
  "servidor": "ICMarkets-Live01",
  "login": "10987654",
  "saldo_disponivel": 5000.00,
  "msg": "EA Operacional em tempo real",
  "timestamp": 1723280000
}
```
- **Exibição na Interface:**
  - **Badge no Cabeçalho do App:** Badge Verde "ONLINE" se `last_ping` for recente (≤ 60 segundos) ou Cinza "OFFLINE" se o sinal falhar por mais de 1 minuto.
  - **Resumo do Painel:** Saldo disponível, indicador de posição aberta e servidor da corretora.





