package com.example.data

/**
 * Catálogo Oficial de Eventos e Notificações MQL5 do Robô EA Fimaster.
 * Este ficheiro reúne todos os eventos de trava, controle de gerenciamento,
 * linha de equador, fechamento de vela e parâmetros do robô MetaTrader 5.
 */
data class EaNotificationDefinition(
    val id: String,
    val title: String,
    val category: String,
    val eventType: String,
    val isBuy: Boolean,
    val isSell: Boolean,
    val mql5CodeSnippet: String,
    val defaultMessage: String,
    val ttsSpeechText: String,
    val description: String
)

object EaNotificationEventsCatalog {

    val ALL_NOTIFICATIONS = listOf(
        // ==========================================
        // EVENTOS DE ORDEM DE VENDA (SELL LOCKS)
        // ==========================================
        EaNotificationDefinition(
            id = "venda_desativada",
            title = "ORDEM DE VENDA DESATIVADA",
            category = "PARÂMETRO / TRAVA",
            eventType = "notificacao_venda_desativada",
            isBuy = false,
            isSell = true,
            mql5CodeSnippet = """
                if(!g_param_ativar_venda)
                {
                   Print(" ordem de venda desativado :");
                   notifica = " ordem de venda desativado :" ;
                   enviarnotificacvao(notifica);
                   return false;
                }
            """.trimIndent(),
            defaultMessage = "ordem de venda desativado :",
            ttsSpeechText = "Alerta do Robô Fimaster: Ordem de venda desativada nas configurações do robô.",
            description = "O parâmetro g_param_ativar_venda está desativado no MetaTrader 5, impedindo a abertura de ordens de venda."
        ),
        EaNotificationDefinition(
            id = "venda_travada_fechamento",
            title = "TRAVA DE VENDA - FECHAMENTO DE VELA",
            category = "FECHAMENTO DE VELA",
            eventType = "notificacao_venda_travada_fechamento",
            isBuy = false,
            isSell = true,
            mql5CodeSnippet = """
                if(!condicao)
                {
                   Print(" ordem de venda travado por fechamento :", pntosDEvelaV, " > ", pontsDEentrada, ": pontos de entrada");
                   notifica = " ordem de venda travado por fechamento :" + DoubleToString(pntosDEvelaV, 1) + " > " + DoubleToString(pontsDEentrada, 1) + ": pontos de entrada";
                   enviarnotificacvao(notifica);
                   return false;
                }
            """.trimIndent(),
            defaultMessage = "ordem de venda travado por fechamento : 12.5 > 10.0: pontos de entrada",
            ttsSpeechText = "Aviso Fimaster: Ordem de venda travada por fechamento de vela superior aos pontos de entrada.",
            description = "A quantidade de pontos da vela de venda excede o limite estabelecido para a entrada na operação."
        ),
        EaNotificationDefinition(
            id = "venda_travada_equador_tendencia",
            title = "TRAVA DE VENDA - EQUADOR / TENDÊNCIA",
            category = "EQUADOR & TENDÊNCIA",
            eventType = "notificacao_venda_travada_equador",
            isBuy = false,
            isSell = true,
            mql5CodeSnippet = """
                if(!comando_venda)
                {
                   Print(" ordem de venda travdo por linha de equador  :", comando_venda);
                   notifica = " ordem de venda trvado por linha de equador ou por tendência :" + (!comando_venda ? "verdade" : "falso");
                   enviarnotificacvao(notifica);
                   return false;
                }
            """.trimIndent(),
            defaultMessage = "ordem de venda trvado por linha de equador ou por tendência : verdade",
            ttsSpeechText = "Atenção: Ordem de venda travada por alinhamento da linha de equador ou tendência oposta.",
            description = "Comando de venda bloqueado devido ao posicionamento do preço em relação à linha do equador central ou tendência do ativo."
        ),
        EaNotificationDefinition(
            id = "venda_travada_gerenciamento",
            title = "TRAVA DE VENDA - GERENCIAMENTO DE RISCO",
            category = "GERENCIAMENTO DE RISCO",
            eventType = "notificacao_venda_travada_gerenciamento",
            isBuy = false,
            isSell = true,
            mql5CodeSnippet = """
                if(!contol_de_gerenciamento && !contol_de_gerenciamento_semanal)
                {
                   Print(" ordem de venda travdo por contol_de_gerenciamento. DIARIO  :", contol_de_gerenciamento, ". SEMANAL :", contol_de_gerenciamento_semanal);
                   notifica = "ordem de venda trvado por contol_de_gerenciamento DIARIO  :" + (!contol_de_gerenciamento ? "SIM" : "NÂO :") + " SEMANAL :" + (!contol_de_gerenciamento_semanal ? "SIM" : "NÃO");
                   enviarnotificacvao(notifica);
                   return false;
                }
            """.trimIndent(),
            defaultMessage = "ordem de venda trvado por contol_de_gerenciamento DIARIO  : SIM SEMANAL : NÃO",
            ttsSpeechText = "Alerta de Risco: Ordem de venda travada pelo controle de gerenciamento diário ou semanal.",
            description = "Bloqueio de ordens de venda ativado devido ao limite de metas ou drawdown configurado para o dia ou semana."
        ),

        // ==========================================
        // EVENTOS DE ORDEM DE COMPRA (BUY LOCKS)
        // ==========================================
        EaNotificationDefinition(
            id = "compra_desativada",
            title = "ORDEM DE COMPRA DESATIVADA",
            category = "PARÂMETRO / TRAVA",
            eventType = "notificacao_compra_desativada",
            isBuy = true,
            isSell = false,
            mql5CodeSnippet = """
                if(!g_param_ativar_compra)
                {
                   Print(" ordem de compra desativado");
                   notifica = " ordem de compra desativado";
                   enviarnotificacvao(notifica);
                   return false;
                }
            """.trimIndent(),
            defaultMessage = "ordem de compra desativado",
            ttsSpeechText = "Alerta do Robô Fimaster: Ordem de compra desativada nas configurações do robô.",
            description = "O parâmetro g_param_ativar_compra está desativado no MetaTrader 5, impedindo novas posições compradas."
        ),
        EaNotificationDefinition(
            id = "compra_travada_fechamento",
            title = "TRAVA DE COMPRA - FECHAMENTO DE VELA",
            category = "FECHAMENTO DE VELA",
            eventType = "notificacao_compra_travada_fechamento",
            isBuy = true,
            isSell = false,
            mql5CodeSnippet = """
                if(!condicao2)
                {
                   Print(" ordem de compra travado por fechamento :", pntosDEvelaC, " > ", pontsDEentrada, ": pontos de entrada");
                   notifica = " ordem de compra travado por fechamento :" + DoubleToString(pntosDEvelaC, 1) + " > " + DoubleToString(pontsDEentrada, 1) + ": pontos de entrada";
                   enviarnotificacvao(notifica);
                   return false;
                }
            """.trimIndent(),
            defaultMessage = "ordem de compra travado por fechamento : 14.8 > 10.0: pontos de entrada",
            ttsSpeechText = "Aviso Fimaster: Ordem de compra travada por fechamento de vela superior aos pontos de entrada.",
            description = "Pontos da vela compradora ultrapassaram o tamanho máximo tolerável estipulado no gerenciamento de entrada."
        ),
        EaNotificationDefinition(
            id = "compra_travada_equador_tendencia",
            title = "TRAVA DE COMPRA - EQUADOR / TENDÊNCIA",
            category = "EQUADOR & TENDÊNCIA",
            eventType = "notificacao_compra_travada_equador",
            isBuy = true,
            isSell = false,
            mql5CodeSnippet = """
                if(!comando_compra)
                {
                   Print(" ordem de compra trvado por linha de equador ou por tendência :", comando_compra);
                   notifica = " ordem de compra trvado por linha de equador ou por tendência :" + (!comando_compra ? "verdade" : "falso");
                   enviarnotificacvao(notifica);
                   return false;
                }
            """.trimIndent(),
            defaultMessage = "ordem de compra trvado por linha de equador ou por tendência : verdade",
            ttsSpeechText = "Atenção: Ordem de compra travada por linha de equador ou tendência do mercado.",
            description = "Operação de compra travada por filtro do equador ou divergência da tendência principal do canal."
        ),
        EaNotificationDefinition(
            id = "compra_travada_gerenciamento",
            title = "TRAVA DE COMPRA - GERENCIAMENTO DE RISCO",
            category = "GERENCIAMENTO DE RISCO",
            eventType = "notificacao_compra_travada_gerenciamento",
            isBuy = true,
            isSell = false,
            mql5CodeSnippet = """
                if(!contol_de_gerenciamento || !contol_de_gerenciamento_semanal)
                {
                   Print("ordem de compra trvado por contol_de_gerenciamento DIARIO  :", contol_de_gerenciamento, ". SEMANAL :", contol_de_gerenciamento_semanal);
                   notifica = "ordem de compra trvado por contol_de_gerenciamento DIARIO  :" + (!contol_de_gerenciamento ? "SIM" : "NÂO :") + " SEMANAL :" + (!contol_de_gerenciamento_semanal ? "SIM" : "NÃO");
                   enviarnotificacvao(notifica);
                   return false;
                }
            """.trimIndent(),
            defaultMessage = "ordem de compra trvado por contol_de_gerenciamento DIARIO  : SIM SEMANAL : NÃO",
            ttsSpeechText = "Alerta de Risco: Ordem de compra travada por controle de gerenciamento diário ou semanal.",
            description = "Bloqueio de ordens de compra devido às diretrizes de controle de risco diário ou semanal atingidas."
        )
    )

    /**
     * Converte uma notificação MQL5 recebida em um [EaRobotEvent] estruturado para exibição no aplicativo.
     */
    fun createRobotEventFromNotification(
        definition: EaNotificationDefinition,
        customMsg: String? = null,
        account: Long = 859423L,
        symbol: String = "XAUUSD",
        timeframe: String = "M15"
    ): EaRobotEvent {
        val nowSec = System.currentTimeMillis() / 1000L
        val dateStr = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date())
        val timeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())

        val msgText = customMsg ?: definition.defaultMessage

        return EaRobotEvent(
            id = account.toString(),
            currency = "USD",
            event = definition.eventType,
            login = account,
            server = "ICMarkets-Live01",
            symbol = symbol,
            timeframe = timeframe,
            timestamp = nowSec,
            sistema = "SISTEMA DE NOTIFICACOES E TRAVAS MQL5",
            anterior = if (definition.isSell) "TENTATIVA_VENDA" else "TENTATIVA_COMPRA",
            novo = "BLOQUEADO",
            descNovo = definition.description,
            msg = msgText,
            resumo = "${definition.title}: ${definition.category}",
            data = dateStr,
            hora = timeStr
        )
    }

    /**
     * Gera todos os eventos de exemplo das notificações fornecidas pelo utilizador para o simulador.
     */
    fun generateNotificationEventsList(account: Long = 859423L, symbol: String = "XAUUSD"): List<EaRobotEvent> {
        val nowSec = System.currentTimeMillis() / 1000L
        return ALL_NOTIFICATIONS.mapIndexed { index, def ->
            val timestampOffset = (ALL_NOTIFICATIONS.size - index) * 120L
            val dateStr = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date((nowSec - timestampOffset) * 1000L))
            val timeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date((nowSec - timestampOffset) * 1000L))

            EaRobotEvent(
                id = account.toString(),
                currency = "USD",
                event = def.eventType,
                login = account,
                server = "ICMarkets-Live01",
                symbol = symbol,
                timeframe = "M15",
                timestamp = nowSec - timestampOffset,
                sistema = "SISTEMA DE NOTIFICACOES E TRAVAS MQL5",
                anterior = if (def.isSell) "ANALISE_VENDA" else "ANALISE_COMPRA",
                novo = "TRAVADO",
                descNovo = def.description,
                msg = def.defaultMessage,
                resumo = def.title,
                data = dateStr,
                hora = timeStr
            )
        }
    }
}
