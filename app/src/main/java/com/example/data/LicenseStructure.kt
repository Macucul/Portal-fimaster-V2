package com.example.data

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import org.json.JSONObject

/**
 * ====================================================================
 * 📜 DINÂMICA DE LICENÇAS E ACESSOS FIMASTER (dados/indices/licenca.json)
 *
 * Estrutura unificada e dinâmica lida de: dados/indices/licenca.json
 *
 * Parâmetros por tipo de licença:
 * - templates (Booleano): Permissão para carregar e salvar templates/presets.
 * - captura de tela (Booleano): Permissão para capturas gráficas de operações MT5.
 * - grafico de património (Booleano): Permissão para exibição do gráfico de equity/capital.
 * - audio (Booleano): Permissão para alertas sonoros e voz neural no EA MT5 e App.
 * - vincular conta (Numérico): Limite de contas MT5 vinculadas permitidas (ex: 1, 2, 5).
 * - sala (Booleano): Permissão para acesso à Sala VIP de sinais e transmissões ao vivo.
 * ====================================================================
 */

data class LicensePlanConfig(
    val tierKey: String = "trial",
    val templates: Boolean = false,
    val capturaDeTela: Boolean = false,
    val graficoDePatrimonio: Boolean = false,
    val audio: Boolean = false,
    val vincularConta: Int = 1,
    val sala: Boolean = false,
    val maxLot: Double = 999.0, // Sem restrição de lote operacional
    val priceMt: Double = 0.0,
    val priceUsd: Double = 0.0,
    val durationDays: Int = 7,
    val whatsappLink: String = "https://wa.me/258840000000",
    val telegramLink: String = "https://t.me/FiMasterVipOficial",
    val qrCodeLink: String = "https://fimaster.vip/pay",
    val qrCodeBase64: String = ""
)

data class GlobalLicenseConfig(
    val trial: LicensePlanConfig = LicensePlanConfig(
        tierKey = "trial",
        templates = false,
        capturaDeTela = false,
        graficoDePatrimonio = false,
        audio = false,
        vincularConta = 1,
        sala = false,
        maxLot = 999.0,
        priceMt = 0.0,
        priceUsd = 0.0,
        durationDays = 7,
        whatsappLink = "https://wa.me/258840000000",
        telegramLink = "https://t.me/FiMasterVipOficial",
        qrCodeLink = "https://fimaster.vip/trial",
        qrCodeBase64 = ""
    ),
    val starter: LicensePlanConfig = LicensePlanConfig(
        tierKey = "starter",
        templates = false,
        capturaDeTela = false,
        graficoDePatrimonio = false,
        audio = true,
        vincularConta = 1,
        sala = false,
        maxLot = 999.0,
        priceMt = 1500.0,
        priceUsd = 25.0,
        durationDays = 30,
        whatsappLink = "https://wa.me/258840000000",
        telegramLink = "https://t.me/FiMasterVipOficial",
        qrCodeLink = "https://fimaster.vip/starter",
        qrCodeBase64 = ""
    ),
    val pro: LicensePlanConfig = LicensePlanConfig(
        tierKey = "pro",
        templates = true,
        capturaDeTela = true,
        graficoDePatrimonio = true,
        audio = true,
        vincularConta = 2,
        sala = false,
        maxLot = 999.0,
        priceMt = 3500.0,
        priceUsd = 55.0,
        durationDays = 90,
        whatsappLink = "https://wa.me/258840000000",
        telegramLink = "https://t.me/FiMasterVipOficial",
        qrCodeLink = "https://fimaster.vip/pro",
        qrCodeBase64 = ""
    ),
    val masterVip: LicensePlanConfig = LicensePlanConfig(
        tierKey = "master_vip",
        templates = true,
        capturaDeTela = true,
        graficoDePatrimonio = true,
        audio = true,
        vincularConta = 5,
        sala = true,
        maxLot = 999.0,
        priceMt = 7500.0,
        priceUsd = 120.0,
        durationDays = 3650,
        whatsappLink = "https://wa.me/258840000000",
        telegramLink = "https://t.me/FiMasterVipOficial",
        qrCodeLink = "https://fimaster.vip/master",
        qrCodeBase64 = ""
    ),
    val rawJson: String = "",
    val lastUpdated: Long = System.currentTimeMillis()
) {
    fun getConfigForTier(tier: LicenseTier): LicensePlanConfig {
        return when (tier) {
            LicenseTier.TRIAL -> trial
            LicenseTier.STARTER -> starter
            LicenseTier.PRO -> pro
            LicenseTier.MASTER -> masterVip
        }
    }

    fun getConfigForPlanString(planoStr: String?, produtoStr: String? = null): LicensePlanConfig {
        val tier = LicenseTier.fromPlanString(planoStr, produtoStr)
        return getConfigForTier(tier)
    }

    fun toJsonString(): String {
        val root = JSONObject()
        root.put("starter", planToJson(starter))
        root.put("pro", planToJson(pro))
        root.put("master_vip", planToJson(masterVip))
        root.put("trial", planToJson(trial))
        return root.toString(2)
    }

    private fun planToJson(p: LicensePlanConfig): JSONObject {
        return JSONObject().apply {
            put("templates", p.templates)
            put("captura de tela", p.capturaDeTela)
            put("grafico de património", p.graficoDePatrimonio)
            put("audio", p.audio)
            put("vincular conta", p.vincularConta)
            put("sala", p.sala)
            put("whatsapp_link", p.whatsappLink)
            put("telegram_link", p.telegramLink)
            put("qr_code_link", p.qrCodeLink)
            put("qr_code_base64", p.qrCodeBase64)
        }
    }

    companion object {
        fun fromJson(jsonStr: String): GlobalLicenseConfig {
            if (jsonStr.isBlank()) return GlobalLicenseConfig()
            return try {
                val root = JSONObject(jsonStr)
                parseJsonObject(root, jsonStr)
            } catch (e: Exception) {
                e.printStackTrace()
                GlobalLicenseConfig()
            }
        }

        fun parseJsonObject(root: JSONObject, originalRawJson: String = ""): GlobalLicenseConfig {
            // Unwrap wrappers if present (e.g. dados -> indice -> licenca)
            var current = root
            val wrapperKeys = listOf("dados", "indice", "indices", "licenca", "licencas", "planos", "config")
            for (i in 0 until 5) {
                var foundWrapper = false
                for (k in wrapperKeys) {
                    if (current.has(k) && current.optJSONObject(k) != null) {
                        val sub = current.getJSONObject(k)
                        if (sub.has("starter") || sub.has("pro") || sub.has("master_vip") || sub.has("master") || sub.has("trial")) {
                            current = sub
                            foundWrapper = true
                            break
                        }
                    }
                }
                if (!foundWrapper) break
            }

            var trialCfg = LicensePlanConfig(tierKey = "trial", templates = false, capturaDeTela = false, graficoDePatrimonio = false, audio = false, vincularConta = 1, sala = false, maxLot = 999.0, priceMt = 0.0, priceUsd = 0.0, durationDays = 7)
            var starterCfg = LicensePlanConfig(tierKey = "starter", templates = false, capturaDeTela = false, graficoDePatrimonio = false, audio = true, vincularConta = 1, sala = false, maxLot = 999.0, priceMt = 1500.0, priceUsd = 25.0, durationDays = 30)
            var proCfg = LicensePlanConfig(tierKey = "pro", templates = true, capturaDeTela = true, graficoDePatrimonio = true, audio = true, vincularConta = 2, sala = false, maxLot = 999.0, priceMt = 3500.0, priceUsd = 55.0, durationDays = 90)
            var masterCfg = LicensePlanConfig(tierKey = "master_vip", templates = true, capturaDeTela = true, graficoDePatrimonio = true, audio = true, vincularConta = 5, sala = true, maxLot = 999.0, priceMt = 7500.0, priceUsd = 120.0, durationDays = 3650)

            val keys = current.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val planObj = current.optJSONObject(key) ?: continue
                val normKey = key.lowercase().trim()

                val parsedPlan = parseSinglePlanObject(normKey, planObj)
                when {
                    normKey.contains("trial") || normKey.contains("teste") || normKey.contains("demo") -> {
                        trialCfg = parsedPlan.copy(tierKey = "trial", maxLot = 999.0, priceMt = 0.0, priceUsd = 0.0, durationDays = 7)
                    }
                    normKey == "starter" || normKey.contains("starter") || normKey.contains("iniciante") || normKey.contains("basico") -> {
                        starterCfg = parsedPlan.copy(tierKey = "starter", maxLot = 999.0, priceMt = 1500.0, priceUsd = 25.0, durationDays = 30)
                    }
                    normKey == "pro" || normKey.contains("pro") || normKey.contains("profissional") -> {
                        proCfg = parsedPlan.copy(tierKey = "pro", maxLot = 999.0, priceMt = 3500.0, priceUsd = 55.0, durationDays = 90)
                    }
                    normKey.contains("master") || normKey.contains("vip") || normKey.contains("vitalicio") -> {
                        masterCfg = parsedPlan.copy(tierKey = "master_vip", maxLot = 999.0, priceMt = 7500.0, priceUsd = 120.0, durationDays = 3650)
                    }
                }
            }

            return GlobalLicenseConfig(
                trial = trialCfg,
                starter = starterCfg,
                pro = proCfg,
                masterVip = masterCfg,
                rawJson = originalRawJson,
                lastUpdated = System.currentTimeMillis()
            )
        }

        private fun parseSinglePlanObject(tierKey: String, obj: JSONObject): LicensePlanConfig {
            val templates = getBoolFlexible(obj, listOf("templates", "template", "presets", "carregar_templates", "carregar templates"))
            val capturaDeTela = getBoolFlexible(obj, listOf("captura de tela", "captura_de_tela", "captura de telas", "captura_tela", "screenshot", "screenshots", "capturar_tela", "capturar tela"))
            val graficoDePatrimonio = getBoolFlexible(obj, listOf("grafico de património", "grafico de patrimonio", "grafico_de_patrimonio", "grafico_patrimonio", "patrimonio", "grafico", "equity_chart", "grafico_equity"))
            val audio = getBoolFlexible(obj, listOf("audio", "som", "alerta_sonoro", "alertas_sonoros", "audio_alerts", "voz", "notificacao_sonora", "notificacao sonora"))
            val vincularConta = getIntFlexible(obj, listOf("vincular conta", "vincular_conta", "vincular_contas", "limite_contas", "contas", "max_contas", "vincular", "contas_mt5"), defaultVal = if (tierKey.contains("master") || tierKey.contains("vip")) 5 else if (tierKey.contains("pro")) 2 else 1)
            val sala = getBoolFlexible(obj, listOf("sala", "sala_vip", "sala_sinais", "vip_room", "sala de sinais", "sala_transmissoes", "transmissoes"))
            val whatsappLink = getStringFlexible(obj, listOf("whatsapp_link", "whatsapp", "whatsapp_url", "wa_link", "contato_whatsapp", "link_whatsapp", "wa"), defaultVal = "https://wa.me/258840000000")
            val telegramLink = getStringFlexible(obj, listOf("telegram_link", "telegram", "telegram_url", "tg_link", "canal_telegram", "link_telegram", "sala_telegram", "sala_link", "tg"), defaultVal = "https://t.me/FiMasterVipOficial")
            val qrCodeLink = getStringFlexible(obj, listOf("qr_code_link", "qr_code", "qrcode_link", "qrcode", "chave_pix", "mpesa_link", "payment_link", "link_qrcode", "qr_link"), defaultVal = "https://fimaster.vip/pay")
            val qrCodeBase64 = getStringFlexible(obj, listOf("qr_code_base64", "qrcode_base64", "qr_base64", "qr_image_base64", "base64_qr", "imagem_qrcode", "qr_image", "qr_code_b64"), defaultVal = "")

            return LicensePlanConfig(
                tierKey = tierKey,
                templates = templates,
                capturaDeTela = capturaDeTela,
                graficoDePatrimonio = graficoDePatrimonio,
                audio = audio,
                vincularConta = vincularConta,
                sala = sala,
                whatsappLink = whatsappLink,
                telegramLink = telegramLink,
                qrCodeLink = qrCodeLink,
                qrCodeBase64 = qrCodeBase64
            )
        }

        private fun getStringFlexible(obj: JSONObject, possibleKeys: List<String>, defaultVal: String = ""): String {
            for (k in possibleKeys) {
                if (obj.has(k) && !obj.isNull(k)) {
                    val s = obj.optString(k, "").trim()
                    if (s.isNotBlank()) return s
                }
            }
            val keys = obj.keys()
            while (keys.hasNext()) {
                val origK = keys.next()
                val cleanK = normalizeKey(origK)
                for (p in possibleKeys) {
                    if (cleanK == normalizeKey(p)) {
                        val s = obj.optString(origK, "").trim()
                        if (s.isNotBlank()) return s
                    }
                }
            }
            return defaultVal
        }

        private fun getBoolFlexible(obj: JSONObject, possibleKeys: List<String>): Boolean {
            for (k in possibleKeys) {
                if (obj.has(k)) {
                    return obj.optBoolean(k, false)
                }
            }
            val keys = obj.keys()
            while (keys.hasNext()) {
                val origK = keys.next()
                val cleanK = normalizeKey(origK)
                for (p in possibleKeys) {
                    if (cleanK == normalizeKey(p)) {
                        return obj.optBoolean(origK, false)
                    }
                }
            }
            return false
        }

        private fun getIntFlexible(obj: JSONObject, possibleKeys: List<String>, defaultVal: Int): Int {
            for (k in possibleKeys) {
                if (obj.has(k)) {
                    return obj.optInt(k, defaultVal)
                }
            }
            val keys = obj.keys()
            while (keys.hasNext()) {
                val origK = keys.next()
                val cleanK = normalizeKey(origK)
                for (p in possibleKeys) {
                    if (cleanK == normalizeKey(p)) {
                        return obj.optInt(origK, defaultVal)
                    }
                }
            }
            return defaultVal
        }

        private fun normalizeKey(str: String): String {
            return str.lowercase().trim()
                .replace("ó", "o")
                .replace("õ", "o")
                .replace("ô", "o")
                .replace("á", "a")
                .replace("ã", "a")
                .replace("â", "a")
                .replace("é", "e")
                .replace("ê", "e")
                .replace("í", "i")
                .replace("ú", "u")
                .replace("ç", "c")
                .replace("_", " ")
                .replace("-", " ")
        }
    }
}

enum class LicenseTier(
    val id: String,
    val code: String,
    val displayName: String,
    val subtitle: String,
    val badgeLabel: String,
    val priceMt: Double,
    val priceUsd: Double,
    val durationLabel: String,
    val durationDays: Int,
    val accentColor: Color,
    val maxLot: Double,
    val canViewScreenshots: Boolean,
    val canAccessCandlestickCanvas: Boolean,
    val canAccessAdvancedEaConfig: Boolean,
    val canAccessAutoSurfada: Boolean,
    val canAccessMultiSessions: Boolean,
    val isPrioritySupport: Boolean,
    val features: List<LicenseFeatureItem>
) {
    TRIAL(
        id = "TRIAL",
        code = "TRIAL",
        displayName = "Plano Trial",
        subtitle = "Demonstração & Teste Operacional",
        badgeLabel = "7 DIAS GRÁTIS",
        priceMt = 0.0,
        priceUsd = 0.0,
        durationLabel = "7 Dias",
        durationDays = 7,
        accentColor = Color(0xFF94A3B8), // Slate Silver
        maxLot = 999.0,
        canViewScreenshots = false,
        canAccessCandlestickCanvas = false,
        canAccessAdvancedEaConfig = false,
        canAccessAutoSurfada = false,
        canAccessMultiSessions = false,
        isPrioritySupport = false,
        features = listOf(
            LicenseFeatureItem("Lote Operacional ILIMITADO (Sem restrições)", isIncluded = true),
            LicenseFeatureItem("Limite de 1 Conta MT5 vinculada", isIncluded = true),
            LicenseFeatureItem("Carregamento de Templates & Presets (Bloqueado)", isIncluded = false, isTrialLocked = true),
            LicenseFeatureItem("Capturas Gráficas de Tela MT5 (Bloqueado)", isIncluded = false, isTrialLocked = true),
            LicenseFeatureItem("Gráfico de Evolução de Patrimônio / Equity (Bloqueado)", isIncluded = false, isTrialLocked = true),
            LicenseFeatureItem("Alertas Sonoros & Voz Neural no EA (Bloqueado)", isIncluded = false, isTrialLocked = true),
            LicenseFeatureItem("Sala VIP de Sinais & Transmissões (Bloqueado)", isIncluded = false, isTrialLocked = true),
            LicenseFeatureItem("Suporte Prioritário VIP", isIncluded = false)
        )
    ),

    STARTER(
        id = "STARTER",
        code = "STARTER",
        displayName = "Plano Starter",
        subtitle = "Iniciante & Traders Individuais",
        badgeLabel = "MAIS ACESSÍVEL",
        priceMt = 1500.0,
        priceUsd = 25.0,
        durationLabel = "30 Dias (Mensal)",
        durationDays = 30,
        accentColor = Color(0xFF38BDF8), // Bright Sky Blue
        maxLot = 999.0,
        canViewScreenshots = false,
        canAccessCandlestickCanvas = false,
        canAccessAdvancedEaConfig = false,
        canAccessAutoSurfada = false,
        canAccessMultiSessions = false,
        isPrioritySupport = false,
        features = listOf(
            LicenseFeatureItem("Lote Operacional ILIMITADO (Sem restrições)", isIncluded = true),
            LicenseFeatureItem("Limite de 1 Conta MT5 vinculada", isIncluded = true),
            LicenseFeatureItem("Alertas Sonoros & Voz Neural no EA Liberados", isIncluded = true),
            LicenseFeatureItem("Sincronização Nuvem e GitHub Automática", isIncluded = true),
            LicenseFeatureItem("Carregamento de Templates & Presets (Bloqueado)", isIncluded = false),
            LicenseFeatureItem("Capturas Gráficas de Tela MT5 (Bloqueado)", isIncluded = false),
            LicenseFeatureItem("Gráfico de Evolução de Patrimônio / Equity (Bloqueado)", isIncluded = false),
            LicenseFeatureItem("Sala VIP de Sinais & Transmissões (Bloqueado)", isIncluded = false)
        )
    ),

    PRO(
        id = "PRO",
        code = "PRO",
        displayName = "Plano Pro",
        subtitle = "Profissional & Alta Performance",
        badgeLabel = "MAIS POPULAR 🔥",
        priceMt = 3500.0,
        priceUsd = 55.0,
        durationLabel = "90 Dias (Trimestral)",
        durationDays = 90,
        accentColor = Color(0xFF10B981), // Emerald Green
        maxLot = 999.0,
        canViewScreenshots = true,
        canAccessCandlestickCanvas = true,
        canAccessAdvancedEaConfig = true,
        canAccessAutoSurfada = true,
        canAccessMultiSessions = true,
        isPrioritySupport = true,
        features = listOf(
            LicenseFeatureItem("Lote Operacional ILIMITADO (Sem restrições)", isIncluded = true),
            LicenseFeatureItem("Limite de até 2 Contas MT5 vinculadas", isIncluded = true),
            LicenseFeatureItem("Carregamento de Templates & Presets 100% Liberado", isIncluded = true),
            LicenseFeatureItem("Capturas Gráficas de Tela em Alta Resolução", isIncluded = true),
            LicenseFeatureItem("Gráfico de Evolução de Patrimônio / Candlesticks Completo", isIncluded = true),
            LicenseFeatureItem("Alertas Sonoros & Voz Neural de Alta Performance", isIncluded = true),
            LicenseFeatureItem("Auto-Surfada & Parâmetros Avançados", isIncluded = true),
            LicenseFeatureItem("Sala VIP de Sinais (Exclusivo Master VIP)", isIncluded = false)
        )
    ),

    MASTER(
        id = "MASTER",
        code = "MASTER",
        displayName = "Plano Master VIP",
        subtitle = "Acesso Total & Vitalício",
        badgeLabel = "VITALÍCIO 👑",
        priceMt = 7500.0,
        priceUsd = 120.0,
        durationLabel = "Vitalício (Sem Expiração)",
        durationDays = 3650,
        accentColor = Color(0xFFA855F7), // Royal Purple
        maxLot = 999.0, // Ilimitado
        canViewScreenshots = true,
        canAccessCandlestickCanvas = true,
        canAccessAdvancedEaConfig = true,
        canAccessAutoSurfada = true,
        canAccessMultiSessions = true,
        isPrioritySupport = true,
        features = listOf(
            LicenseFeatureItem("Lote Operacional ILIMITADO (Sem restrições)", isIncluded = true),
            LicenseFeatureItem("Limite Expandido de até 5 Contas MT5 vinculadas", isIncluded = true),
            LicenseFeatureItem("Acesso Exclusivo à SALA VIP de Sinais & Transmissões", isIncluded = true),
            LicenseFeatureItem("Carregamento e Criação de Templates & Presets VIP", isIncluded = true),
            LicenseFeatureItem("Capturas Gráficas de Tela MT5 Ilimitadas", isIncluded = true),
            LicenseFeatureItem("Gráfico de Evolução de Patrimônio & Candlesticks Completo", isIncluded = true),
            LicenseFeatureItem("Alertas Sonoros, Voz Neural & Notificações VIP", isIncluded = true),
            LicenseFeatureItem("Suporte Direto VIP 24/7 com Desenvolvedores", isIncluded = true)
        )
    );

    fun buildDynamicFeatures(planConfig: LicensePlanConfig): List<LicenseFeatureItem> {
        return listOf(
            LicenseFeatureItem(
                title = "Lote Operacional: ILIMITADO (Sem restrições)",
                isIncluded = true
            ),
            LicenseFeatureItem(
                title = "Limite de Contas MT5: ${planConfig.vincularConta} conta(s) vinculada(s)",
                isIncluded = true
            ),
            LicenseFeatureItem(
                title = if (planConfig.templates) "Carregamento de Templates & Presets: LIBERADO" else "Carregamento de Templates & Presets: BLOQUEADO",
                isIncluded = planConfig.templates,
                isTrialLocked = !planConfig.templates
            ),
            LicenseFeatureItem(
                title = if (planConfig.capturaDeTela) "Capturas Gráficas de Tela MT5: LIBERADO" else "Capturas Gráficas de Tela MT5: BLOQUEADO",
                isIncluded = planConfig.capturaDeTela,
                isTrialLocked = !planConfig.capturaDeTela
            ),
            LicenseFeatureItem(
                title = if (planConfig.graficoDePatrimonio) "Gráfico de Evolução de Patrimônio / Equity: LIBERADO" else "Gráfico de Evolução de Patrimônio: BLOQUEADO",
                isIncluded = planConfig.graficoDePatrimonio,
                isTrialLocked = !planConfig.graficoDePatrimonio
            ),
            LicenseFeatureItem(
                title = if (planConfig.audio) "Alertas Sonoros & Voz Neural no EA: LIBERADO" else "Alertas Sonoros & Voz Neural: BLOQUEADO",
                isIncluded = planConfig.audio,
                isTrialLocked = !planConfig.audio
            ),
            LicenseFeatureItem(
                title = if (planConfig.sala) "Sala VIP de Sinais & Transmissões: LIBERADA 👑" else "Sala VIP de Sinais & Transmissões: BLOQUEADA",
                isIncluded = planConfig.sala,
                isTrialLocked = !planConfig.sala
            ),
            LicenseFeatureItem(
                title = if (this == MASTER) "Suporte VIP 24/7 Direto" else "Suporte Padrão Portal",
                isIncluded = this == MASTER || this == PRO
            )
        )
    }

    companion object {
        fun fromPlanString(planoStr: String?, produtoStr: String? = null): LicenseTier {
            val cleanPlan = planoStr?.lowercase()?.trim().orEmpty()
            val cleanProd = produtoStr?.lowercase()?.trim().orEmpty()

            // 1. Processar explicitamente o campo 'plano' se informado
            if (cleanPlan.isNotBlank()) {
                val planWithoutBrand = cleanPlan.replace("fimaster", "").trim()
                val planTokens = cleanPlan.split(Regex("[^a-záàâãéèêíïóôõöúç0-9]+")).filter { it.isNotBlank() }

                // Verificação de MASTER / VIP
                val isMaster = planTokens.contains("master") ||
                        planTokens.contains("vip") ||
                        planTokens.contains("master_vip") ||
                        planWithoutBrand.contains("master") ||
                        planWithoutBrand.contains("vip") ||
                        cleanPlan.contains("vitalicio") ||
                        cleanPlan.contains("vitalício") ||
                        cleanPlan.contains("ilimitado") ||
                        cleanPlan.contains("lifetime")
                if (isMaster) return MASTER

                // Verificação de PRO
                val isPro = planTokens.contains("pro") ||
                        planTokens.contains("profissional") ||
                        planWithoutBrand.contains("pro") ||
                        cleanPlan.contains("trimestral") ||
                        cleanPlan.contains("advanced") ||
                        cleanPlan.contains("avancado") ||
                        cleanPlan.contains("avançado")
                if (isPro) return PRO

                // Verificação de STARTER
                val isStarter = planTokens.contains("starter") ||
                        planTokens.contains("iniciante") ||
                        planTokens.contains("basico") ||
                        planTokens.contains("básico") ||
                        planTokens.contains("mensal") ||
                        planTokens.contains("semestral") ||
                        planTokens.contains("anual") ||
                        planWithoutBrand.contains("starter") ||
                        cleanPlan.contains("iniciante") ||
                        cleanPlan.contains("basico") ||
                        cleanPlan.contains("básico")
                if (isStarter) return STARTER

                // Verificação de TRIAL
                val isTrial = planTokens.contains("trial") ||
                        planTokens.contains("teste") ||
                        planTokens.contains("demo") ||
                        planTokens.contains("gratis") ||
                        planTokens.contains("grátis") ||
                        cleanPlan.contains("trial") ||
                        cleanPlan.contains("teste") ||
                        cleanPlan.contains("demo") ||
                        cleanPlan.contains("demonstracao") ||
                        cleanPlan.contains("demonstração") ||
                        cleanPlan.contains("gratis") ||
                        cleanPlan.contains("grátis")
                if (isTrial) return TRIAL
            }

            // 2. Se o campo 'plano' não definiu, analisar 'produto' (removendo o nome da marca 'fimaster' e 'ea')
            val cleanProdWithoutBrand = cleanProd.replace("fimaster", "").replace("ea", "").trim()
            if (cleanProdWithoutBrand.isNotBlank()) {
                val prodTokens = cleanProdWithoutBrand.split(Regex("[^a-záàâãéèêíïóôõöúç0-9]+")).filter { it.isNotBlank() }
                if (prodTokens.contains("master") || prodTokens.contains("vip") || cleanProdWithoutBrand.contains("vitalicio") || cleanProdWithoutBrand.contains("vitalício")) return MASTER
                if (prodTokens.contains("pro") || prodTokens.contains("profissional")) return PRO
                if (prodTokens.contains("starter") || prodTokens.contains("iniciante")) return STARTER
                if (prodTokens.contains("trial") || prodTokens.contains("demo") || prodTokens.contains("teste")) return TRIAL
            }

            // 3. Fallback: Se não há plano especificado, o plano inicial padrão é TRIAL
            return if (planoStr.isNullOrBlank()) TRIAL else STARTER
        }
    }
}

data class LicenseFeatureItem(
    val title: String,
    val isIncluded: Boolean,
    val isTrialLocked: Boolean = false
)

fun LicenseTier.buildDynamicFeatures(config: LicensePlanConfig): List<LicenseFeatureItem> {
    return listOf(
        LicenseFeatureItem(
            title = "Lote Operacional ILIMITADO (Sem restrições)",
            isIncluded = true
        ),
        LicenseFeatureItem(
            title = "Contas MT5 Vinculadas: ${config.vincularConta} conta(s)",
            isIncluded = true
        ),
        LicenseFeatureItem(
            title = if (config.templates) "Carregamento de Templates & Presets Liberado" else "Carregamento de Templates & Presets (Bloqueado)",
            isIncluded = config.templates,
            isTrialLocked = !config.templates && this == LicenseTier.TRIAL
        ),
        LicenseFeatureItem(
            title = if (config.capturaDeTela) "Capturas Gráficas de Tela MT5 em Alta Resolução" else "Capturas Gráficas de Tela MT5 (Bloqueado)",
            isIncluded = config.capturaDeTela,
            isTrialLocked = !config.capturaDeTela && this == LicenseTier.TRIAL
        ),
        LicenseFeatureItem(
            title = if (config.graficoDePatrimonio) "Gráfico de Evolução Patrimonial / Equity Interativo" else "Gráfico de Evolução Patrimonial (Bloqueado)",
            isIncluded = config.graficoDePatrimonio,
            isTrialLocked = !config.graficoDePatrimonio && this == LicenseTier.TRIAL
        ),
        LicenseFeatureItem(
            title = if (config.audio) "Alertas Sonoros & Voz Neural em Tempo Real" else "Alertas Sonoros & Voz Neural (Bloqueado)",
            isIncluded = config.audio,
            isTrialLocked = !config.audio && this == LicenseTier.TRIAL
        ),
        LicenseFeatureItem(
            title = if (config.sala) "Acesso VIP Exclusivo à Sala de Sinais & Transmissões" else "Sala VIP de Sinais & Transmissões (Bloqueado)",
            isIncluded = config.sala,
            isTrialLocked = !config.sala && this == LicenseTier.TRIAL
        ),
        LicenseFeatureItem(
            title = if (this == LicenseTier.MASTER) "Suporte Prioritário VIP & Atualizações Vitalícias" else "Suporte Padrão via Portal",
            isIncluded = true
        )
    )
}

/**
 * CompositionLocals para acesso nos Composables
 */
val LocalLicenseTier = staticCompositionLocalOf { LicenseTier.TRIAL }
val LocalLicensePlanConfig = staticCompositionLocalOf { LicensePlanConfig() }
val LocalGlobalLicenseConfig = staticCompositionLocalOf { GlobalLicenseConfig() }

val GithubUser?.effectiveLicenseTier: LicenseTier
    get() = if (this == null) LicenseTier.TRIAL else LicenseTier.fromPlanString(this.licencaPlano, this.licencaProduto)
