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
    val lJJ: String = "⬛⬛⬛⬛⬛⬛⬛[ AUTENTICAÇÃO ]⬛⬛⬛⬛⬛⬛⬛",
    val xFF: String = "DATA DE EXPIRAÇÃO: 3 MESES",
    val SENHA: String = "123456",
    
    // [ COR ]
    val aYY: String = "⬛⬛⬛⬛⬛⬛⬛⬛⬛⬛⬛[ COR ]⬛⬛⬛⬛⬛⬛⬛⬛⬛⬛⬛",
    val ESQUEMA_CORES_ENUM: String = "CYAN_NEON",
    val cor_de_canal: String = "#22D3EE",
    val cor_de_linhas: String = "#FF00E5",
    val corr_de_equador: String = "#FFFF00",
    
    // [ TENDÊNCIA ]
    val sJJ: String = "⬛⬛⬛⬛⬛⬛⬛⬛⬛[ TENDÊNCIA ]⬛⬛⬛⬛⬛⬛⬛⬛⬛",
    val LINHAS_DE_EQUADOR: Boolean = false,
    val TREND: String = "UP_TREND",
    val M_equador_alta: Double = 1.2500,
    val M_equador_baixa: Double = 1.2400,
    
    // [ ESTRATÉGIA ]
    val xxx: String = "⬛⬛⬛⬛⬛⬛⬛⬛[ ESTRATÉGIA ]⬛⬛⬛⬛⬛⬛⬛⬛⬛",
    val TEMA: Boolean = false,
    val ESTRATÉGIA: String = "TRI_EXP_MOVING_AVERAGE",
    val virada_de_jogo: Boolean = false,
    val Nives: Double = 1.0,
    val Costurar: Boolean = true,
    val OperationalPeriod: String = "PERIOD_M15",
    val lot: Double = 0.01,
    
    // [ AUTOMATICO ]
    val dS: String = "⬛⬛⬛⬛⬛⬛⬛⬛⬛[ AUTOMATICO ]⬛⬛⬛⬛⬛⬛⬛⬛",
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
    val dSS: String = "⬛⬛⬛⬛⬛⬛⬛[ POSIC: DE ORDEM ]⬛⬛⬛⬛⬛⬛⬛",
    val compra: Double = 1.2550,
    val venda: Double = 1.2500,
    val santo: Double = 20.0,
    val dedo: Int = 10,
    val posicaoTake: Boolean = false,
    val buy_take: Double = 0.0,
    val sell_take: Double = 0.0,
    
    // [ GERANC: DE CAPITAL ]
    val fDD: String = "⬛⬛⬛⬛⬛⬛[ GERANC: DE CAPITAL ]⬛⬛⬛⬛⬛⬛",
    val SALDO: Double = 1000.0,
    val GERENCIAMENTO_DE_RISCO_DIARIO: Boolean = true,
    val porcentos: Double = 1.0,
    val poercentosg: Double = 1.0,
    val GERENCIAMENTO_DE_RISCO_SEMANAL: Boolean = false,
    val PORCENTOO: Double = 2.0,
    val PORCENTOSS: Double = 2.0,
    
    // [ PARÂM: OPERACIONAIS ]
    val gG: String = "⬛⬛⬛⬛⬛[ PARÂM: OPERACIONAIS ]⬛⬛⬛⬛⬛",
    val GMAIL: Boolean = true,
    val notific: Boolean = true,
    val ativar_ou_desativar_venda: Boolean = true,
    val ativar_ou_desativar_compra: Boolean = true,
    val Modify_Sl_For_OxO: Boolean = true,
    val condicao_De_rompimento_c: Boolean = true,
    val condicao_De_rompimento_v: Boolean = true,
    
    // [ RESULTADO ]
    val hFF: String = "⬛⬛⬛⬛⬛⬛⬛⬛⬛[ RESULTADO ]⬛⬛⬛⬛⬛⬛⬛⬛⬛",
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
    val saldoDisponivel: Double = 0.0
)


