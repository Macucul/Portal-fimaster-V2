# GUIA DE DESIGN SYSTEM E ESPECIFICAÇÃO DE CARTÕES DE EVENTOS
> **Manual Visual e Guia de Estilo para Réplica no App Admin**  
> *Versão: 2.0 • Tema: Cyber-Trading Dark / Modern Financial UI*

---

## 🎨 1. PALETA DE CORES E MATERIAL VISUAL

O visual do aplicativo é construído sobre um conceito **Dark Cyber-Trading**, utilizando tons escuros profundos e frios com acentos neon brilhantes e semitransparências para garantir alta legibilidade de dados financeiros.

### 1.1 Cores Base de Fundo e Superfície
- **Background Geral do App (`BgDark`)**: `#090D16` ou `#0B0F19` (Preto azulado profundo)
- **Cartão Base / Container (`CardBg`)**: `#0F172A` (Slate 900) com borda suave `#1E293B`
- **Superfície Interna / Bloco Conteúdo (`InnerSurfaceBg`)**: `#1E293B` com 80% de opacidade (`alpha = 0.8f`)
- **Divisores / Borda Suave (`BorderDefault`)**: `#334155` com 40% a 60% de opacidade

### 1.2 Cores Semânticas de Acento (Estados e Eventos)
- **Azul Ciano (Tecnologia / Estado / Informação)**: `#38BDF8` (Sky 400) ou `#22D3EE` (Cyan 400)
- **Verde Esmeralda (Sucesso / Compra / Lucro)**: `#10B981` (Emerald 500)
- **Vermelho Neon (Erro / Venda / Prejuízo / Perigo)**: `#EF4444` (Red 500)
- **Âmbar / Laranja (Alerta / Trava / Posição)**: `#F59E0B` (Amber 500)
- **Roxo / Violeta (Ordens neutras / Execuções)**: `#8B5CF6` (Violet 500)
- **Texto Principal**: `#FFFFFF` (Branco Puro)
- **Texto Secundário / Labels**: `#94A3B8` (Slate 400) / `#CBD5E1` (Slate 300)

---

## 📐 2. TIPOGRAFIA, BORDAS E FORMAS (SHAPES)

### 2.1 Tipografia (Jetpack Compose Typography)
- **Título do Cartão**: `titleSmall` • `FontWeight.Bold` • `14.sp` • `letterSpacing = 0.5.sp` (Em caixa alta)
- **Texto em Destaque (Valores/Labels)**: `labelSmall` / `bodyMedium` • `FontWeight.Bold` ou `Black`
- **Corpo do Texto / Mensagens**: `bodySmall` • `FontWeight.Normal` • `12.sp` / `13.sp` • `lineHeight = 18.sp`

### 2.2 Arredondamentos (Corner Radius)
- **Cartão Principal (`OuterCardShape`)**: `12.dp` ou `16.dp`
- **Bloco Interno de Dados (`InnerBlockShape`)**: `8.dp` ou `10.dp`
- **Pílulas / Badges de Ativo/Ticket (`TagShape`)**: `6.dp` ou `20.dp` (totalmente arredondado)

### 2.3 Estilo de Bordas e Transparências (Glassmorphism Sutil)
- Todas as pílulas e caixas usam fundo semitransparente (`alpha = 0.15f` a `0.2f`) combinado com uma **Borda de 1.dp** na mesma cor com opacidade mais alta (`alpha = 0.4f` a `0.6f`).

---

## 🎴 3. ESPECIFICAÇÃO DOS CARTÕES DE EVENTOS (`ClassicEventCard`)

Cada evento possui uma identidade visual única no topo (título + ícone + cor de badge) e uma estrutura interna personalizada para o seu tipo de payload.

---

### 3.1 Cartão: `mudanca_estado` (Mudança de Estado do Robô)
Usado para transições de inteligência, preços, canal e execução.

- **Título**: `MUDANÇA DE ESTADO: <CATEGORIA_DO_SISTEMA>`
- **Ícone**: `Icons.Default.Tune`
- **Cor do Acento**: `#22D3EE` (Ciano)
- **Estrutura Interna**:
  1. **Linha de Transição**: Pílula indicando `DE: <ESTADO_ANTERIOR>` ➔ `PARA: <ESTADO_NOVO>`.
  2. **Quadro de Destaque "DESCRIÇÃO DO ESTADO"** *(Apenas quando existir `descNovo`)*:
     - Fundo: `#0284C7` com `alpha = 0.18f`
     - Borda: `1.dp` na cor `#38BDF8` (`alpha = 0.5f`)
     - Canto arredondado: `8.dp`
     - Cabeçalho interno: Ícone de documento (`Icons.Default.Description`) + Texto em caixa alta **`DESCRIÇÃO DO ESTADO:`** em `#38BDF8` (`FontWeight.Black`, `letterSpacing = 0.5.sp`).
     - Conteúdo: Descrição sanitizada e traduzida do estado em branco (`#FFFFFF`), `FontWeight.Bold`, `lineHeight = 20.sp`.
  3. **Rodapé**: Ativo, Timeframe e data/hora.

---

### 3.2 Cartão: `ordem_executada` (Ordem de Entrada / Fechamento MT5)
Usado para ordens efetuadas de compra ou venda.

- **Título**: `ORDEM DE COMPRA EXECUTADA` / `ORDEM DE VENDA EXECUTADA` / `POSIÇÃO ENCERRADA`
- **Ícone**: `Icons.Default.TrendingUp` (Compra) / `Icons.Default.TrendingDown` (Venda) / `Icons.Default.SwapHoriz`
- **Cor do Acento**: `#10B981` (Verde para Compra) / `#EF4444` (Vermelho para Venda)
- **Estrutura Interna**:
  1. Pílula com o tipo de ordem (ex: `ENTRADA DE POSIÇÃO`, `COMPRA`, `VENDA`).
  2. Pílula com o Número do Ticket / Bilhete (ex: `Bilhete #191429491`).
  3. Pílula com o Ativo (ex: `USDJPYM`).
  4. Bloco de detalhes: Volume (`Volume: 0.01 Lotes`), Preço de Execução, Stop Loss (SL) e Take Profit (TP).

---

### 3.3 Cartão: `ordem_modificada` (Ajuste de SL/TP)
Usado para modificação de Stop Loss e Take Profit em tempo real.

- **Título**: `ORDEM MODIFICADA (SL/TP)`
- **Ícone**: `Icons.Default.Tune`
- **Cor do Acento**: `#38BDF8` (Azul Ciano)
- **Estrutura Interna**:
  1. Identificação da Ordem / Ticket alterado.
  2. Tabela comparativa com novos valores de SL e TP.

---

### 3.4 Cartão: `erro_ordem` / `ordem_não_executada` (Falha MQL5)
Usado para rejeição de ordens pela corretora ou erros MQL5.

- **Título**: `ERRO DE ORDEM MT5` / `ORDEM NÃO EXECUTADA`
- **Ícone**: `Icons.Default.Error` / `Icons.Default.Warning`
- **Cor do Acento**: `#EF4444` (Vermelho Alerta)
- **Estrutura Interna**:
  1. Fundo do bloco em tom avermelhado suave (`#EF4444` com `alpha = 0.1f`).
  2. Código do Erro MQL5 e descrição detalhada do motivo da rejeição.

---

### 3.5 Cartão: `relatorio_financeiro` (Painel Financeiro)
Usado para resumos de saldo, lucro diário e semanal.

- **Título**: `RELATÓRIO FINANCEIRO EA`
- **Ícone**: `Icons.Default.TrendingUp`
- **Cor do Acento**: `#10B981` (Verde Esmeralda)
- **Estrutura Interna**:
  1. Métricas principais divididas em colunas: Lucro Diário e Lucro Semanal em destaque numérico.
  2. Status da Meta e Saldo Disponível da Conta.

---

### 3.6 Cartão: `captura` (Captura de Tela do Gráfico MT5)
Usado para prints e análises visuais enviadas pelo robô.

- **Título**: `CAPTURA DE TELA CONCLUÍDA`
- **Ícone**: `Icons.Default.CameraAlt`
- **Cor do Acento**: `#38BDF8` (Azul Ciano)
- **Estrutura Interna**:
  1. Imagem em Base64 decodificada em tela cheia com bordas arredondadas (`8.dp`) e `ContentScale.Fit`.
  2. Nome do arquivo da captura (`Arquivo: captura_grafico_187046768.png`).
  3. Badge de confirmação `CONCLUÍDO`.

---

### 3.7 Cartão: `sessao_inicio` / `sessao_fim` (Sessão Forex)
Usado para abertura e encerramento de mercados (Londres, New York, Tokyo).

- **Título**: `SESSÃO FOREX INICIADA` / `SESSÃO FOREX ENCERRADA`
- **Ícone**: `Icons.Default.Schedule`
- **Cor do Acento**: `#38BDF8` (Azul Ciano)
- **Estrutura Interna**:
  1. Nome da Sessão (ex: `SESSÃO MERCADO: TOKYO`).
  2. Horário da sessão destacado.

---

### 3.8 Cartão: `inicializacao` (Start/Boot do EA)
Usado quando o robô é inicializado no gráfico MT5.

- **Título**: `INICIALIZAÇÃO DO ROBÔ`
- **Ícone**: `Icons.Default.PlayArrow`
- **Cor do Acento**: `#10B981` (Verde Esmeralda)
- **Estrutura Interna**:
  1. Informações de Inicialização, Servidor e Licença do Robô.

---

## 🛠️ 4. ESTRUTURA DE CÓDIGO JETPACK COMPOSE (MODELO DE REFERÊNCIA)

Abaixo está o modelo exato do container do cartão com o cabeçalho e a caixa de destaque da **Descrição do Estado** para uso no App Admin:

```kotlin
@Composable
fun AdminEventCardExample(
    cardTitle: String,
    badgeIcon: ImageVector,
    badgeColor: Color,
    descEstadoText: String? = null,
    timestamp: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        border = BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // 1. Cabeçalho do Cartão
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(badgeColor.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = badgeIcon,
                            contentDescription = null,
                            tint = badgeColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = cardTitle,
                        style = MaterialTheme.typography.titleSmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Quadro Destaque: DESCRIÇÃO DO ESTADO (se houver)
            if (!descEstadoText.isNullOrBlank()) {
                Surface(
                    color = Color(0xFF0284C7).copy(alpha = 0.18f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "DESCRIÇÃO DO ESTADO:",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color(0xFF38BDF8),
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.5.sp
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = descEstadoText,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 20.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 3. Rodapé do Cartão
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = timestamp,
                    style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF64748B))
                )
            }
        }
    }
}
```

---
*Fim do Guia de Design. Este ficheiro pode ser copiado e utilizado diretamente como referência técnica para réplica de UI.*
