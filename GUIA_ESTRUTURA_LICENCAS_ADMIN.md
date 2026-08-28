# 📋 GUIA OFICIAL DE ESTRUTURA DE LICENÇAS FIMASTER (ADMIN & APP PORTAL)

Este documento estabelece o **padrão unificado, arquitetura de dados e divisão de responsabilidades** do ecossistema FiMaster. Todos os módulos (Aplicativo Administrador, Aplicativo Portal Cliente Android e Robô EA MQL5) devem seguir rigorosamente esta especificação.

---

## 🏛️ 1. Divisão Clara de Responsabilidades

| Módulo | Responsabilidade Principal | Ações Específicas |
|---|---|---|
| **App Admin** *(Administrador / Backoffice)* | **Gestão, Escrita & Emissão** | • Adiciona, emite, renova e suspende licenças.<br>• Registra o tipo de licença adquirida e o pagamento.<br>• Atualiza os nós JSON/Database (`licenca`, `mt5`, `saldo`, `credito_guardado`, `historico`).<br>• Define datas de validade e vínculos de conta MT5. |
| **App Portal FiMaster** *(App Cliente Android)* | **Leitura, Verificação & Aplicação de Condições** | • Consome e valida o status e validade da licença (`effectiveLicenseTier`).<br>• Aplica as restrições visuais do plano (ex: desfoque/blur no Trial).<br>• Desbloqueia ferramentas gráficas (Candlesticks, Auto-Surfada, Sessões).<br>• Exibe contagem regressiva, status da licença e opções de upgrade. |
| **Robô EA MQL5** *(MetaTrader 5)* | **Execução Operacional & Limites** | • Valida se o lote da ordem respeita o limite do plano ativo.<br>• Habilita/desabilita algoritmos como Auto-Surfada e Equador.<br>• Envia eventos e capturas auditadas para o App Portal. |

---

## 💰 2. Tabela Oficial de Planos, Preços e Parâmetros

Ao cadastrar ou atualizar uma licença no **App Admin**, utilize exatamente os seguintes valores e códigos de plano:

| Nível / Tier | Código no Nó (`plano`) | Preço (MT) | Preço (USD) | Duração Padrão | Lote Operacional Máx. | Recursos & Condições |
|---|---|---|---|---|---|---|
| **Trial** | `"Trial"` | **0 MT** | **$0.00** | 7 Dias | **0.01** | • Acesso básico para testes<br>• Capturas MT5 desfocadas (Blur)<br>• Gráfico Candlestick bloqueado<br>• Sem Auto-Surfada |
| **Starter** | `"Starter"` | **1.500 MT** | **$25.00** | 30 Dias (Mensal) | **0.05** | • Capturas MT5 100% nítidas<br>• Gráfico Candlestick liberado<br>• Sincronização em nuvem ativa<br>• 1 Sessão operacional |
| **Pro** | `"Pro"` | **3.500 MT** | **$55.00** | 90 Dias (Trimestral) | **0.50** | • Todos os recursos do Starter<br>• Módulo Auto-Surfada liberado<br>• Multi-Sessões Forex (Tokyo, London, NY)<br>• Suporte rápido prioritário |
| **Master VIP** | `"Master VIP"` ou `"Master"` | **7.500 MT** | **$120.00** | **Vitalício** *(Sem Expiração)* | **ILIMITADO** *(999.0)* | • Todos os recursos liberados sem restrições<br>• Acesso perpétuo sem mensalidades<br>• Range dinâmico de expansão total<br>• Suporte VIP 24/7 direto |

---

## 🗄️ 3. Estrutura Completa de Nós que o App Admin Deve Gravar

O App Admin deve gravar o arquivo de usuário (`<user_id>.json`) com a seguinte árvore de nós:

```json
{
  "usuario_id_exemplo": {
    "status": "ATIVO",
    "origem": "sms_fimaster",
    "numero": "258841234567",
    "nome": "Nome do Cliente",
    "id_transacao": "TRX_MPESA_98765",
    "saldo": 0.0,
    "credito_guardado": 0.0,
    "nivel_autorizacao": "CLIENTE",
    "data_registro": "2026-08-16 20:00:00",
    "ultima_atualizacao": "2026-08-16 22:30:00",

    "mt5": {
      "registrado": true,
      "id_conta": "88429105"
    },

    "licenca": {
      "ativa": true,
      "produto": "FiMaster EA Pro",
      "plano": "Starter",
      "validade": "2026-09-15 23:59:59",
      "ultima_renovacao": "2026-08-16 22:30:00",
      "total_renovacoes": 1,
      "historico": [
        {
          "data": "2026-08-16 22:30:00",
          "valor": 1500.0,
          "descricao": "Ativação Plano Starter (30 Dias) via M-Pesa"
        }
      ]
    },

    "autorizacao": {
      "status": "APROVADO",
      "aprovado_por": "Admin Principal",
      "data_aprovacao": "2026-08-16 22:30:00"
    },

    "reembolso": {
      "solicitado": false,
      "status": "NENHUM"
    },

    "auditoria": {
      "ultimo_login": "2026-08-16 22:15:00",
      "ultimo_dispositivo": "Samsung Galaxy S23",
      "tentativas_login": 0
    }
  }
}
```

---

## 🔍 4. Detalhamento Campo a Campo para o App Admin

### 🏷️ Bloco `licenca` (Obrigatório para o Licenciamento)

1. **`licenca.ativa`** (`Boolean`):
   - `true`: Licença válida e em operação.
   - `false`: Licença suspensa ou expirada (o App Portal exibirá aviso de expiração).

2. **`licenca.plano`** (`String`):
   - Valores aceitos: `"Trial"`, `"Starter"`, `"Pro"`, `"Master VIP"`.
   - *Nota:* O App Portal faz parsing flexível através de `LicenseTier.fromPlanString()`, identificando palavras-chave como `starter`, `pro`, `master`, `vitalicio`, `trial`.

3. **`licenca.produto`** (`String`):
   - Exemplo: `"FiMaster EA"`, `"EA PRO MAX v4.2"`.

4. **`licenca.validade`** (`String`):
   - Para planos temporais (**Trial**, **Starter**, **Pro**): data no formato `"YYYY-MM-DD HH:mm:ss"` ou `"YYYY-MM-DD"`.
   - Para o plano **Master VIP**: `"2099-12-31 23:59:59"` ou `"VITALICIO"`.

5. **`licenca.ultima_renovacao`** (`String`):
   - Data e hora do momento em que o Admin aprovou/renovou a licença.

6. **`licenca.total_renovacoes`** (`Integer`):
   - Contador incremental de renovações realizadas pelo usuário (inicia em `0` ou `1`).

7. **`licenca.historico`** (`Array de Objetos`):
   - Registro de transações para auditoria e histórico do cliente:
     ```json
     {
       "data": "2026-08-16 22:30:00",
       "valor": 3500.0,
       "descricao": "Upgrade para Plano Pro (90 Dias)"
     }
     ```

---

### 🖥️ Bloco `mt5` (Vinculação da Conta de Trading)

1. **`mt5.registrado`** (`Boolean`):
   - `true`: O usuário já informou a conta MT5 e foi validada.
   - `false`: Conta ainda não cadastrada.

2. **`mt5.id_conta`** (`String`):
   - Número da conta MetaTrader 5 do usuário (ex: `"88429105"`).
   - O EA MQL5 compara esta conta com `AccountInfoInteger(ACCOUNT_LOGIN)`.

---

## ⚡ 5. Como o App Portal FiMaster Interpreta e Aplica as Condições

O **App Portal FiMaster** executa as seguintes regras de negócio com base no nó `licenca` recebido:

```
                  ┌─────────────────────────────────┐
                  │ Leitura do Nó 'licenca.plano'   │
                  │   e 'licenca.ativa' via JSON    │
                  └────────────────┬────────────────┘
                                   │
                   ┌───────────────┴───────────────┐
                   ▼                               ▼
       Se licenca.ativa == false       Se licenca.ativa == true
       ┌───────────────────────┐       ┌────────────────────────┐
       │ Exibe Banner de Aviso │       │ Mapeia para            │
       │ "LICENÇA EXPIRADA"    │       │ LicenseTier:           │
       │ e Bloqueia Operações  │       │ TRIAL, STARTER, PRO,   │
       └───────────────────────┘       │ MASTER                 │
                                       └───────────┬────────────┘
                                                   │
     ┌──────────────────────┬──────────────────────┼─────────────────────┐
     ▼                      ▼                      ▼                     ▼
┌──────────────┐      ┌──────────────┐      ┌──────────────┐      ┌──────────────┐
│    TRIAL     │      │   STARTER    │      │     PRO      │      │  MASTER VIP  │
├──────────────┤      ├──────────────┤      ├──────────────┤      ├──────────────┤
│• Lote: 0.01  │      │• Lote: 0.05  │      │• Lote: 0.50  │      │• Lote: LIVRE │
│• Blur MT5 🔒 │      │• Capturas OK │      │• Capturas HD │      │• Acesso Total│
│• Gráfico 🔒  │      │• Gráficos OK │      │• Auto-Surf OK│      │• Vitalício 👑│
│• Sem Surfada │      │• 1 Sessão    │      │• 3 Sessões   │      │• Sem Expiração
└──────────────┘      └──────────────┘      └──────────────┘      └──────────────┘
```

---

## 🛠️ 6. Exemplos Rápidos de Payload para o App Admin Salvar

### A) Ativação do Plano STARTER (1.500 MT / 30 Dias)
```json
"licenca": {
  "ativa": true,
  "produto": "FiMaster EA",
  "plano": "Starter",
  "validade": "2026-09-15 23:59:59",
  "ultima_renovacao": "2026-08-16 22:30:00",
  "total_renovacoes": 1,
  "historico": [
    {
      "data": "2026-08-16 22:30:00",
      "valor": 1500.0,
      "descricao": "Assinatura Starter Mensal"
    }
  ]
}
```

### B) Ativação do Plano PRO (3.500 MT / 90 Dias)
```json
"licenca": {
  "ativa": true,
  "produto": "FiMaster EA Pro",
  "plano": "Pro",
  "validade": "2026-11-14 23:59:59",
  "ultima_renovacao": "2026-08-16 22:30:00",
  "total_renovacoes": 1,
  "historico": [
    {
      "data": "2026-08-16 22:30:00",
      "valor": 3500.0,
      "descricao": "Assinatura Trimestral Pro"
    }
  ]
}
```

### C) Ativação do Plano MASTER VIP (7.500 MT / Vitalício)
```json
"licenca": {
  "ativa": true,
  "produto": "FiMaster EA Master VIP",
  "plano": "Master VIP",
  "validade": "2099-12-31 23:59:59",
  "ultima_renovacao": "2026-08-16 22:30:00",
  "total_renovacoes": 1,
  "historico": [
    {
      "data": "2026-08-16 22:30:00",
      "valor": 7500.0,
      "descricao": "Licença Vitalícia Master VIP sem Expiração"
    }
  ]
}
```
