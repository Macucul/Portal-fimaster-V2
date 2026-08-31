# 🛡️ Mapeamento de Nós e Regras de Segurança do Firebase

Este documento detalha todos os nós do **Firebase Realtime Database** acessados pelo aplicativo **FiMaster Portal / EA Robot**, categorizados por operações de **Leitura (READ)**, **Escrita (WRITE)** e **Remoção (DELETE)**, juntamente com o modelo de **Regras de Segurança (`security_rules.json`)** prontas para uso e sugestões de autenticação por **UID de Dispositivo (Device Auth)**.

---

## 1. 📋 Mapeamento de Nós do Firebase

### 🟢 Nós de LEITURA (READ)

| Caminho do Nó (Firebase Path) | Descrição / Finalidade | Quem Lê |
| :--- | :--- | :--- |
| `/dados/status/{mt5AccountId}` | Status em tempo real do EA (Conexão, Versão, Magic Number, Lucro do Dia, etc.) | App Android |
| `/dados/usuarios/{userId}/status` | Status do robô indexado pelo ID de usuário | App Android |
| `/dados/usuarios/{mt5AccountId}/status` | Status do robô indexado pela conta MT5 | App Android |
| `/dados/eventos/{mt5AccountId}` | Todos os eventos operacionais do robô (ordens, posições, erros, pings) | App Android |
| `/dados/eventos/{mt5AccountId}/{sub_evento}` | Sub-eventos específicos (`relatorio_financeiro`, `posicao_alterada`, `erro_ordem`, `captura_tela`, etc.) | App Android |
| `/dados/eventos/historico_patrimonio/{mt5AccountId}` | Histórico de evolução patrimonial/equity da conta | App Android |
| `/dados/usuarios/{userId}/eventos` | Eventos do robô associados à conta do usuário | App Android |
| `/dados/parametros/{mt5AccountId}` | Parâmetros e configurações operacionais ativas no MT5 | App Android & EA MQL5 |
| `/dados/usuarios/{userId}/config` | Configurações personalizadas salvas pelo usuário | App Android |
| `/dados/indices/licenca` | Configurações globais de licenças, preços, links de pagamento, WhatsApp e Telegram | App Android |
| `/dados/indices/instrucoes_admin_templates` | Templates e estratégias predefinidas criadas pelo administrador | App Android |

---

### 🔴 Nós de ESCRITA (WRITE / UPDATE)

| Caminho do Nó (Firebase Path) | Descrição / Finalidade | Quem Escreve |
| :--- | :--- | :--- |
| `/dados/parametros/{mt5AccountId}` | Envio de novos parâmetros operacionais (Lote, SL/TP, Trailing, Horários) | App Android / Admin |
| `/dados/usuarios/{userId}/config` | Atualização das preferências de configuração do usuário | App Android |
| `/dados/eventos/{mt5AccountId}/capturar_tela` | Sinal de comando remoto para solicitar screenshot do gráfico no MT5 | App Android |
| `/dados/status/{mt5AccountId}` | Telemetria contínua, status da conta e saldo | EA MQL5 (Metatrader) |
| `/dados/eventos/{mt5AccountId}/*` | Disparo de eventos de ordens, erros e relatórios | EA MQL5 (Metatrader) |
| `/dados/indices/licenca` | Atualização de planos, preços, WhatsApp, Telegram e QR Code | Painel Admin |
| `/dados/indices/instrucoes_admin_templates` | Publicação de novos templates de estratégia | Painel Admin |

---

### 🟡 Nós de EXCLUSÃO / MIGRAÇÃO (DELETE)

| Caminho do Nó | Finalidade |
| :--- | :--- |
| `/dados/parametros/{oldMt5Id}` | Limpeza de conta MT5 antiga ao desvincular ou trocar de conta |
| `/dados/status/{oldMt5Id}` | Limpeza de status de conta desvinculada |
| `/dados/eventos/{oldMt5Id}` | Limpeza de eventos da conta anterior |

---

## 2. 🔐 Modelo de Regras de Segurança do Firebase (`database.rules.json`)

Abaixo está o arquivo de regras do **Firebase Realtime Database** configurado para proteger os nós privados de cada usuário/conta e manter públicos apenas os índices necessários (como tabelas de preços de licença e templates):

```json
{
  "rules": {
    "dados": {
      // 1. Licenças, Templates e Índices:
      "indices": {
        "licenca": {
          ".read": true,
          ".write": "auth != null && auth.token.admin === true"
        },
        "instrucoes_admin_templates": {
          ".read": "auth != null",
          ".write": "auth != null && auth.token.admin === true"
        },
        "telefones": {
          ".read": "auth != null",
          ".write": "auth != null"
        },
        "mt5": {
          ".read": "auth != null",
          ".write": "auth != null"
        }
      },

      // 2. Parâmetros Operacionais por Conta MT5
      "parametros": {
        "$mt5AccountId": {
          ".read": "auth != null",
          ".write": "auth != null"
        }
      },

      // 3. Status da Conta / Robô
      "status": {
        "$mt5AccountId": {
          ".read": "auth != null",
          ".write": "auth != null"
        }
      },

      // 4. Eventos e Histórico Operacional
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
      },

      // 5. Dados Privados Isolados por Usuário
      "usuarios": {
        "$userId": {
          ".read": "auth != null && (auth.uid == $userId || auth.token.admin === true)",
          ".write": "auth != null && (auth.uid == $userId || auth.token.admin === true)"
        }
      }
    }
  }
}
```

---

## 3. 💡 Sugestões de Autenticação usando o UID do Dispositivo

Para vincular o acesso ao aparelho físico sem exigir senha complexa toda vez, existem **3 abordagens recomendadas**:

### Abordagem 1: Firebase Anonymous Auth + Device Hardware ID (Mais Simples e Recomendada)
1. Ao abrir o aplicativo, obtenha o ID único de hardware:
   ```kotlin
   val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
   ```
2. Realize o login anônimo no Firebase (`FirebaseAuth.getInstance().signInAnonymously()`):
   - O Firebase gera um `auth.uid` estável e criptográfico.
3. Salve o mapeamento `deviceId -> firebaseUid` no nó `/dados/usuarios/{firebaseUid}`.
4. **Vantagem**: Totalmente integrado às regras do Firebase (`auth.uid != null`), sem custo de servidor intermediário.

### Abordagem 2: Custom Token Authentication (Maior Controle e Segurança)
1. O App envia o `ANDROID_ID` ou UUID criptografado para uma Cloud Function / Backend seguro.
2. O Backend valida o dispositivo e gera um **Custom Token** do Firebase com `createCustomToken(deviceId, claims)`.
3. O App recebe o token e executa:
   ```kotlin
   FirebaseAuth.getInstance().signInWithCustomToken(customToken)
   ```
4. **Vantagem**: O `auth.uid` no Firebase passa a ser exatamente o identificador do dispositivo do cliente, permitindo regras estritas:
   ```json
   "usuarios": {
     "$deviceId": {
       ".read": "auth != null && auth.uid == $deviceId",
       ".write": "auth != null && auth.uid == $deviceId"
     }
   }
   ```

### Abordagem 3: Vinculação Híbrida (Device ID + Conta MT5)
- Ao registrar o número da conta MT5, armazena-se no Firebase a tupla `mt5_id + device_id`.
- O Robô EA no MetaTrader 5 só aceita comandos enviados caso a assinatura bata com a conta autorizada, evitando que terceiros enviem comandos para contas MT5 alheias.
