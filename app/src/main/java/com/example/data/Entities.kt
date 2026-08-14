package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 1, // Only one active logged-in client in this portal
    val fullName: String,
    val mt5AccountId: String,
    val passwordHash: String, // Simulating stored password
    val licenseStatus: String, // "Ativa", "Pendente", "Expirada"
    val licenseExpiryDate: String,
    val balanceMT: Double, // Current robot profit/balance indicator in Meticais (MT)
    val githubToken: String = "",
    val githubRepo: String = "",
    val githubBranch: String = "main",
    val deviceId: String = ""
)

@Entity(tableName = "refund_requests")
data class RefundRequest(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val requestDate: String,
    val amountMT: Double,
    val status: String, // "Aprovado", "Pendente", "Rejeitado"
    val paymentDate: String, // "N/A" or date if Approved
    val reason: String
)

@Entity(tableName = "ea_config")
data class EaConfigEntity(
    @PrimaryKey val mt5AccountId: String,
    
    // [ AUTENTICAÇÃO ]
    val xFF: String = "DATA DE EXPIRAÇÃO: 3 MESES",
    val SENHA: String = "123456",
    
    // [ COR ]
    val ESQUEMA_CORES_ENUM: String = "CYAN_NEON",
    val cor_de_canal: String = "#22D3EE",
    val cor_de_linhas: String = "#FF00E5",
    val corr_de_equador: String = "#FFFF00",
    
    // [ TENDÊNCIA ]
    val LINHAS_DE_EQUADOR: Boolean = false,
    val TREND: String = "UP_TREND",
    val M_equador_alta: Double = 1.2500,
    val M_equador_baixa: Double = 1.2400,
    
    // [ ESTRATÉGIA ]
    val TEMA: Boolean = false,
    val ESTRATÉGIA: String = "TRI_EXP_MOVING_AVERAGE",
    val virada_de_jogo: Boolean = false,
    val Nives: Double = 1.0,
    val Costurar: Boolean = true,
    val OperationalPeriod: String = "PERIOD_M15",
    val lot: Double = 0.01,
    
    // [ AUTOMATICO ]
    val EA_ATIVO: Boolean = true,
    val EA_AUTO: Boolean = false,
    val AUTO_PERIOD: String = "PERIOD_H1",
    val AUTO_SURFADA: Boolean = false,
    val SESSAO_ASIA_TOQUIO: Boolean = false,
    val SESSAO_LONDRES: Boolean = false,
    val SESSAO_NOVA_YORQUI: Boolean = false,
    val EXPANSAO_MINIMA: Int = 10,
    val EXPANSAO_MAXIMA: Int = 30,
    
    // [ POSIC: DE ORDEM ]
    val compra: Double = 1.2550,
    val venda: Double = 1.2500,
    val santo: Double = 20.0,
    val dedo: Int = 10,
    val posicaoTake: Boolean = false,
    val buy_take: Double = 0.0,
    val sell_take: Double = 0.0,
    
    // [ GERANC: DE CAPITAL ]
    val SALDO: Double = 1000.0,
    val GERENCIAMENTO_DE_RISCO_DIARIO: Boolean = true,
    val porcentos: Double = 1.0,
    val poercentosg: Double = 1.0,
    val GERENCIAMENTO_DE_RISCO_SEMANAL: Boolean = false,
    val PORCENTOO: Double = 2.0,
    val PORCENTOSS: Double = 2.0,
    
    // [ PARÂM: OPERACIONAIS ]
    val GMAIL: Boolean = true,
    val notific: Boolean = true,
    val ativar_ou_desativar_venda: Boolean = true,
    val ativar_ou_desativar_compra: Boolean = true,
    val Modify_Sl_For_OxO: Boolean = true,
    val condicao_De_rompimento_c: Boolean = true,
    val condicao_De_rompimento_v: Boolean = true,
    
    // [ RESULTADO ]
    val mony: String = " Meticais ",
    val CAMBIO: Double = 64.0,

    // [ HABILITAR LEITURA POR JANELA DE PARÂMETROS PARA O EA ]
    val LER_CONEXAO_LICENCA: Boolean = true,
    val LER_ESQUEMA_CORES: Boolean = true,
    val LER_PAINEL_CAMBIO: Boolean = true,
    val LER_CANAIS_TENDENCIA: Boolean = true,
    val LER_ESTRATEGIA_PRINCIPAL: Boolean = true,
    val LER_POSICIONAMENTO_ORDEM: Boolean = true,
    val LER_GESTAO_RISCO: Boolean = true,
    val LER_AUTOMACAO_SESSOES: Boolean = true,
    val LER_RESULTADOS_NOTIFICACOES: Boolean = true,
    val PERMITIR_LEITURA_PARAMETROS: Boolean = true
)

fun EaConfigEntity.validarParametros(): String {
    // 1. SENHA
    if (SENHA.trim().isEmpty()) {
        return "coloque a senha e volte à tentar novamente"
    }

    // 2. LINHAS DE EQUADOR
    if (LINHAS_DE_EQUADOR) {
        if (M_equador_baixa >= M_equador_alta) {
            return "verifique os preços de linhas de equador e volte à tentar novamente"
        }
        if (M_equador_alta <= 0 || M_equador_baixa <= 0) {
            return "coloque os preços de linha de equador e volte à tentar novamente"
        }
    }

    // 3. LOTE
    if (lot <= 0) {
        return "coloque o lote e volte à tentar novamente"
    }

    // 4. NÍVEIS
    if (Nives <= 0) {
        return "verifique se o nives >= 1 e volte à tentar novamente"
    }

    // 5. TAKE COMPRA
    if (posicaoTake && buy_take > 0 && buy_take < compra) {
        return "take de compra deve ser maior que preço de compra"
    }

    // 6. TAKE VENDA
    if (posicaoTake && sell_take > 0 && sell_take > venda) {
        return "take de venda deve ser menor que preço de venda"
    }

    // 7. RISCO DIÁRIO
    if (GERENCIAMENTO_DE_RISCO_DIARIO) {
        if (porcentos <= 0) {
            return "limite de perda diário inválido"
        }
        if (poercentosg <= 0) {
            return "limite de ganho diário inválido"
        }
    }

    // 8. RISCO SEMANAL
    if (GERENCIAMENTO_DE_RISCO_SEMANAL) {
        if (PORCENTOO <= 0) {
            return "limite de perda semanal inválido"
        }
        if (PORCENTOSS <= 0) {
            return "limite de ganho semanal inválido"
        }
    }

    // 9. AUTO MODE
    if (EA_AUTO) {
        if (EXPANSAO_MINIMA > EXPANSAO_MAXIMA || (EXPANSAO_MINIMA * 2 >= EXPANSAO_MAXIMA)) {
            return "expansão inválida (min deve ser < max e max >= 2x min)"
        }

        val autoPeriodUpper = AUTO_PERIOD.uppercase().trim()
        val isHora1 = autoPeriodUpper.contains("H1") || autoPeriodUpper.contains("HORA_1") || autoPeriodUpper.contains("HORA1")
        val isManual = autoPeriodUpper.contains("MANUAL")
        val isSessoes = autoPeriodUpper.contains("SESS") || autoPeriodUpper.contains("SESSION")

        if (isHora1 && AUTO_SURFADA) {
            return "auto surfada não compatível com H1"
        }
        if (isManual && AUTO_SURFADA && (compra <= 0 || venda <= 0)) {
            return "defina compra e venda manualmente"
        }
        if (isSessoes && AUTO_SURFADA && !SESSAO_ASIA_TOQUIO && !SESSAO_LONDRES && !SESSAO_NOVA_YORQUI) {
            return "habilite pelo menos uma sessão"
        }
    } else {
        if (compra <= 0) {
            return "coloque o preço de compra"
        }
        if (venda <= 0) {
            return "coloque o preço de venda"
        }
        if (compra <= venda) {
            return "compra deve ser maior que venda"
        }
        if (santo <= 0) {
            return "configure pontos de saída"
        }
        if (dedo <= 0) {
            return "configure pontos de entrada"
        }
    }

    return ""
}

data class GithubUser(
    val id: String,
    val status: String,
    val origem: String,
    val numero: String,
    val nome: String,
    val idTransacao: String,
    val saldo: Double,
    val senhaHash: String,
    val salt: String,
    val tokenRecuperacao: String,
    val nivelAutorizacao: String,
    val dataRegistro: String,
    val ultimaAtualizacao: String,
    val mt5Registrado: Boolean,
    val mt5IdConta: String,
    val licencaAtiva: Boolean,
    val licencaProduto: String,
    val licencaPlano: String,
    val licencaValidade: String,
    val licencaUltimaRenovacao: String,
    val licencaTotalRenovacoes: Int,
    val licencaHistorico: List<GithubUserHistorico>,
    val reembolsoSolicitado: Boolean,
    val reembolsoStatus: String,
    val autorizacaoStatus: String,
    val autorizacaoAprovadoPor: String,
    val autorizacaoDataAprovacao: String,
    val creditoGuardado: Double,
    val auditoriaUltimoLogin: String = "",
    val auditoriaUltimoDispositivo: String = "",
    val auditoriaTentativasLogin: Int = 0,
    val sha: String = "",
    val filename: String = "" // File name on GitHub (e.g. user_12345.json)
)

data class GithubUserHistorico(
    val data: String,
    val valor: Double,
    val descricao: String
)

data class EaRobotEvent(
    val id: String = "",
    val currency: String = "",
    val event: String = "",
    val login: Long = 0L,
    val server: String = "",
    val symbol: String = "",
    val timeframe: String = "",
    val timestamp: Long = 0L,
    val sistema: String = "",
    val anterior: String = "",
    val novo: String = "",
    val descAnterior: String = "",
    val descNovo: String = "",
    // Additional fields for financial reports, sessions, equador, pings and position changes
    val data: String = "",
    val hora: String = "",
    val motivacao: String = "",
    val moeda: String = "",
    val diarioStatus: String = "",
    val diarioValor: Double = 0.0,
    val diarioPct: Double = 0.0,
    val semanalStatus: String = "",
    val semanalValor: Double = 0.0,
    val semanalPct: Double = 0.0,
    val resumo: String = "",
    val sessao: String = "",
    val horaInicio: Int = -1,
    val minutoInicio: Int = -1,
    val horaFim: Int = -1,
    val minutoFim: Int = -1,
    val msg: String = "",
    val temPosicao: String = "",
    val fusoHorario: Int = 0,
    val fusoTexto: String = "",
    val saldoDisponivel: Double = 0.0,
    val amount: Double = 0.0,
    val type: String = "",
    val tipo: String = "",
    val price: Double = 0.0,
    val volume: Double = 0.0,
    val sl: Double = 0.0,
    val tp: Double = 0.0,
    val novoSl: Double = 0.0,
    val novoTp: Double = 0.0,
    val alvoMt: Double = 0.0,
    val protecaoMt: Double = 0.0,
    val lucroPct: Double = 0.0,
    val perdaPct: Double = 0.0,
    val erroCode: Int = 0,
    val note: String = "",
    val ticket: String = "",
    val imageBase64: String = "",
    val filename: String = ""
)

fun isPosicaoEvent(evt: EaRobotEvent): Boolean {
    val fullTextLower = "${evt.event} ${evt.sistema} ${evt.novo} ${evt.descNovo} ${evt.resumo} ${evt.msg}".lowercase()
    return fullTextLower.contains("posicao") ||
            fullTextLower.contains("posição") ||
            fullTextLower.contains("posicao_alterada") ||
            evt.temPosicao.isNotBlank()
}

fun isPingOrStatusEvent(evt: EaRobotEvent): Boolean {
    val eventLower = evt.event.trim().lowercase()
    val msgLower = evt.msg.trim().lowercase()
    return eventLower == "ping" ||
            eventLower == "status" ||
            msgLower.contains("ea fimaster online e ativo") ||
            msgLower.contains("heartbeat ping")
}

fun resolveOfficialStateDescription(rawStateKey: String): String? {
    val key = rawStateKey.uppercase().trim()
    if (key.isBlank()) return null

    return when {
        // --- ESTADO_SICLO / CICLO DE CANAL ---
        key.contains("SICLO_DE_CANAL_INICIAL") || key.contains("CICLO_DE_CANAL_INICIAL") ->
            "Trader, A estrutura operacional encontra-se concluída e estou preparado para iniciar um novo ciclo de acompanhamento. Neste momento aguardo o primeiro rompimento válido que permita dar início a uma operação. Dependendo do comportamento dos preços, poderei entrar num ciclo de compra ou num ciclo de venda. O objetivo desta fase é aguardar apenas sinais com elevada probabilidade de sucesso. Continuarei a monitorizar o mercado até à confirmação de uma oportunidade."

        key.contains("SICLO_DE_CANAL_COMPRA_FINALIZADO") || key.contains("CICLO_DE_CANAL_COMPRA_FINALIZADO") ->
            "Trader, O ciclo de compra foi concluído e as estruturas associadas à operação foram encerradas. Neste momento estou a limpar referências internas, variáveis temporárias e informações utilizadas durante a gestão da posição. Após a conclusão deste processo estarei preparado para iniciar um novo ciclo operacional. O objetivo desta fase é garantir que futuras decisões não sejam influenciadas por dados já utilizados. Continuarei a monitorizar o mercado em busca de novas oportunidades."

        key.contains("SICLO_DE_CANAL_VENDA_FINALIZADO") || key.contains("CICLO_DE_CANAL_VENDA_FINALIZADO") ->
            "Trader, O ciclo de venda foi concluído e a operação já não requer acompanhamento adicional. Neste momento estou a remover todas as referências utilizadas durante a gestão da posição. Após esta limpeza estarei novamente disponível para iniciar um novo ciclo operacional. O objetivo desta fase é manter a consistência dos dados utilizados pela estratégia. Continuarei a monitorizar o mercado e a aguardar novas oportunidades."

        key.contains("SICLO_DE_CANAL_COMPRA") || key.contains("CICLO_DE_CANAL_COMPRA") ->
            "Trader, Encontro-me atualmente num ciclo de compra ativo. A operação já foi iniciada e estou a acompanhar o comportamento dos preços para avaliar a evolução da posição. O mercado poderá continuar a favor da tendência, atingir o Break Even ou regressar contra a posição. O objetivo desta fase é gerir a operação da forma mais eficiente possível enquanto o movimento permanece válido. Continuarei a monitorizar cada movimento do mercado e a proteger o capital sempre que necessário."

        key.contains("SICLO_DE_CANAL_VENDA") || key.contains("CICLO_DE_CANAL_VENDA") ->
            "Trader, Encontro-me atualmente num ciclo de venda ativo. A operação foi executada e estou a acompanhar continuamente a evolução do mercado. O preço poderá continuar a descer, atingir zonas de proteção ou invalidar parcialmente o movimento atual. O objetivo desta fase é maximizar o potencial da operação mantendo o controlo do risco. Continuarei a monitorizar cada alteração do mercado em tempo real."

        // --- ESTADO_ROBO / ESTADO_DE_EXECUCAO ---
        key.contains("EXECUCAO_COMPRA_INICIAL") ->
            "Trader, Foi confirmado um rompimento comprador compatível com todos os parâmetros definidos pela estratégia. Após a validação das condições de mercado, a oportunidade foi considerada legítima para entrada compradora. Neste momento estou a executar a operação de compra e a preparar a gestão automática da posição. A partir deste ponto acompanharei atentamente a evolução dos preços para identificar oportunidades de proteção, otimização e continuidade da tendência. O objetivo desta ação é posicionar a operação numa fase inicial do movimento comprador e aproveitar o potencial desenvolvimento do mercado. Continuarei a monitorizar cada variação dos preços para garantir uma gestão eficiente da posição."

        key.contains("EXECUCAO_VENDA_INICIAL") ->
            "Trader, Foi confirmado um rompimento vendedor compatível com todos os parâmetros definidos pela estratégia. Após a validação das condições de mercado, a oportunidade foi considerada legítima para entrada vendedora. Neste momento estou a executar a operação de venda e a preparar a gestão automática da posição. A partir deste ponto acompanharei atentamente a evolução dos preços para identificar oportunidades de proteção, otimização e continuidade da tendência. O objetivo desta ação é posicionar a operação numa fase inicial do movimento vendedor e aproveitar o potencial desenvolvimento do mercado. Continuarei a monitorizar cada variação dos preços para garantir uma gestão eficiente da posição."

        key.contains("EXECUCAO_COMPRA_POSICAO") ->
            "Trader, Existe uma posição de compra ativa sob gestão. Neste momento acompanho a evolução do mercado e avalio possíveis extensões do movimento. Poderei proteger ou otimizar a operação conforme necessário. Continuarei a monitorizar o mercado em tempo real."

        key.contains("EXECUCAO_VENDA_POSICAO") ->
            "Trader, Existe uma posição de venda ativa sob gestão. Neste momento monitorizo o comportamento do mercado e possíveis extensões do movimento. Poderei ajustar a proteção ou otimizar a operação conforme necessário. Continuarei a monitorizar o mercado em tempo real."

        key.contains("EXECUCAO_MODIFICAR_COMPRA") ->
            "Trader, Estou a acompanhar a sua posição de compra e tudo decorre dentro dos parâmetros definidos pela estratégia. Neste momento o mercado alcançou a zona de Break Even. Modifiquei automaticamente a ordem para proteger a operação contra possíveis reversões. O objetivo desta ação é transformar uma operação exposta ao risco numa operação mais segura, preservando o capital e os ganhos já obtidos. Até lá, continuarei a monitorizar cada movimento do mercado e a avaliar novas oportunidades de otimização da posição."

        key.contains("EXECUCAO_MODIFICAR_VENDA") ->
            "Trader, Estou a acompanhar a sua posição de venda e tudo decorre dentro dos parâmetros definidos pela estratégia. Neste momento o mercado alcançou a zona de Break Even. Modifiquei automaticamente a ordem para proteger a operação contra possíveis reversões. O objetivo desta ação é transformar uma operação exposta ao risco numa operação mais segura, preservando o capital e os ganhos já obtidos. Até lá, continuarei a monitorizar cada movimento do mercado e a avaliar novas oportunidades de otimização da posição."

        key.contains("EXECUCAO_MODIFICAR") ->
            "Trader, Estou a analisar a posição aberta para possível otimização. Neste momento verifico condições de risco, proteção e melhoria da gestão. Caso os critérios sejam atingidos, irei modificar a operação automaticamente. Continuarei a monitorizar o mercado continuamente."

        key.contains("EXECUCAO_REPOUSO") ->
            "Trader, Neste momento não existem ações pendentes nem operações que exijam intervenção imediata. Todas as verificações programadas foram concluídas e o sistema encontra-se em modo de observação. Caso surjam novas oportunidades compatíveis com a estratégia, retomarei automaticamente as atividades operacionais. O objetivo desta fase é manter vigilância constante sem assumir riscos desnecessários. Continuarei a monitorizar o mercado e a aguardar novos eventos relevantes."

        key.contains("EXECUCAO_INICIAL") ->
            "Trader, Estou a monitorizar continuamente o mercado à procura de condições compatíveis com a estratégia configurada. Neste momento avalio rompimentos, tendências e critérios operacionais antes de considerar qualquer execução. Caso seja identificada uma oportunidade válida, poderei iniciar uma operação de compra ou venda. O objetivo desta fase é garantir que apenas sinais qualificados sejam considerados. Continuarei a monitorizar o mercado em tempo real."

        // --- ESTADO_DE_PRECOS ---
        key.contains("NOVO_CICLO") ->
            "Trader, Iniciei um novo ciclo de organização dos preços. Todas as referências utilizadas no ciclo anterior foram analisadas e as estruturas antigas estão a ser substituídas por novos dados do mercado. Neste momento estou a preparar o ambiente para construir uma nova estrutura operacional, recolhendo informações que servirão de base para a formação do próximo canal. O próximo passo será verificar se os preços atuais apresentam características compatíveis com os parâmetros definidos pela estratégia. O objetivo desta fase é garantir que todas as decisões futuras sejam tomadas com base em dados atualizados e relevantes. Continuarei a monitorizar o mercado e a organizar as informações necessárias para a próxima etapa."

        key.contains("EXPANSAO_RANGE") || key.contains("EXPANSAO") ->
            "Trader, Estou a analisar a expansão atual dos preços para verificar se o mercado apresenta condições adequadas para a construção da estrutura operacional. Neste momento avalio a amplitude do movimento, a consistência da expansão e o respeito pelos parâmetros definidos pela estratégia. Caso os critérios sejam satisfeitos, avançarei para a validação do canal. Se forem identificadas inconsistências, realizarei os ajustes necessários antes de prosseguir. O objetivo desta análise é garantir que a estrutura seja construída sobre movimentos legítimos e não sobre oscilações aleatórias. Continuarei a acompanhar a evolução dos preços até obter uma confirmação segura."

        key.contains("AJUSTE_CANAL") || key.contains("AJUSTE") ->
            "Trader, Identifiquei que a estrutura atual necessita de ajustes antes de poder ser utilizada operacionalmente. Neste momento estou a recalcular os limites do canal, analisando suportes, resistências e zonas de expansão para obter uma configuração mais precisa. Após a conclusão destes ajustes, o canal poderá ser validado e preparedo para utilização. O objetivo desta fase é aumentar a qualidade da leitura do mercado e reduzir a probabilidade de sinais incorretos. Continuarei a monitorizar os preços até concluir todos os ajustes necessários. Configuração válida."

        key.contains("CANAL_PRONTO") ->
            "Trader, Concluí com sucesso a construção da estrutura de preços atual. Neste momento o canal encontra-se validado e pronto para utilização operacional. Os níveis de suporte, resistência e expansão já foram calculados e armazenados. Agora permanecerei em observação aguardando um rompimento confirmado para cima ou para baixo. Se ocorrer um rompimento superior poderei iniciar uma operação de compra. Caso ocorra um rompimento inferior poderei iniciar uma operação de venda. Continuarei a monitorizar o mercado em tempo real até que uma destas condições seja satisfieda."

        key.contains("ROMPIMENTO_CIMA") ->
            "Trader, Foi confirmado um rompimento acima da resistência principal da estrutura atual. Neste momento estou a validar as condições finais necessárias para a execução de uma operação compradora. Se o movimento mantiver a sua consistência, avançarei para o processo de entrada em compra. O objetivo desta fase é garantir que o rompimento representa uma oportunidade real e não apenas um movimento temporário do mercado. Continuarei a acompanhar o comportamento dos preços antes da execução definitiva."

        key.contains("ROMPIMENTO_BAIXO") ->
            "Trader, Foi confirmado um rompimento abaixo do suporte principal da estrutura atual. Neste momento estou a validar as condições finais necessárias para a execução de uma operação vendedora. Se o movimento mantiver a sua consistência, avançarei para o processo de entrada em venda. O objetivo desta fase é garantir que o rompimento representa uma oportunidade legítima e compatível com a estratégia. Continuarei a monitorizar o mercado antes de concluir a execução."

        key.contains("PRECOS_CONCLUIDO") || key.contains("DE_PRECOS_CONCLUIDO") ->
            "Trader, Concluí todas as etapas relacionadas com a organização e distribuição da estrutura de preços. Neste momento os dados necessários para a tomada de decisão encontram-se disponíveis para os restantes módulos do sistema. A estrutura foi validada, processada e preparada para utilização operacional. O próximo passo será acompanhar os eventos de mercado e executar as ações correspondentes sempre que as condições forem satisfeitas. Continuarei a monitorizar continuamente a evolução do mercado."

        else -> null
    }
}

fun resolveEventStateDescription(evt: EaRobotEvent): String {
    resolveOfficialStateDescription(evt.novo)?.let { return it }
    resolveOfficialStateDescription(evt.descNovo)?.let { return it }
    resolveOfficialStateDescription(evt.event)?.let { return it }
    resolveOfficialStateDescription(evt.msg)?.let { return it }
    resolveOfficialStateDescription(evt.sistema)?.let { return it }
    resolveOfficialStateDescription(evt.descAnterior)?.let { return it }
    resolveOfficialStateDescription(evt.anterior)?.let { return it }
    return evt.descNovo.ifEmpty { evt.msg }
}

fun isAllowedEvent(evt: EaRobotEvent): Boolean {
    val eventLower = evt.event.trim().lowercase()
    val msgLower = evt.msg.trim().lowercase()

    val isPing = eventLower == "ping" ||
            eventLower == "status" ||
            msgLower.contains("ea fimaster online e ativo") ||
            msgLower.contains("heartbeat ping")

    if (isPing && !eventLower.contains("mudanca") && !eventLower.contains("relatorio") && !eventLower.contains("ordem") && !eventLower.contains("sessao") && !eventLower.contains("equador") && !eventLower.contains("posicao") && !eventLower.contains("inicializ") && !eventLower.contains("captura")) {
        return false
    }

    return true
}

data class AdminEaTemplate(
    val id: String = "",
    val titulo: String = "",
    val descricao: String = "",
    val autor: String = "Admin Master",
    val dataPublicacao: String = "",
    val validoAte: String = "",
    val disponivel: Boolean = true,
    val versaoMinimaEa: String = "v3.2",
    val pontosAtivo: String = "",
    val paridade: String = "",
    val config: EaConfigEntity = EaConfigEntity(mt5AccountId = "TEMPLATE")
)

fun AdminEaTemplate.isTemplateValido(): Boolean {
    if (!disponivel) return false
    if (validoAte.isBlank()) return true
    
    return try {
        val parts = validoAte.trim().split("/", "-", ".")
        if (parts.size == 3) {
            val year = if (parts[0].length == 4) parts[0].toInt() else parts[2].toInt()
            val month = parts[1].toInt()
            val day = if (parts[0].length == 4) parts[2].toInt() else parts[0].toInt()
            
            val calendar = java.util.Calendar.getInstance()
            val currentYear = calendar.get(java.util.Calendar.YEAR)
            val currentMonth = calendar.get(java.util.Calendar.MONTH) + 1
            val currentDay = calendar.get(java.util.Calendar.DAY_OF_MONTH)
            
            if (year > currentYear) return true
            if (year < currentYear) return false
            if (month > currentMonth) return true
            if (month < currentMonth) return false
            day >= currentDay
        } else {
            true
        }
    } catch (e: Exception) {
        disponivel
    }
}


