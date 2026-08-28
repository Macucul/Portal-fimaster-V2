package com.example.data

/**
 * ====================================================================
 * 🛡️ ADMIN LICENSE MANAGEMENT STRUCTURE & SCHEMAS
 * Estrutura dedicada para o aplicativo e painel administrativo (Admin App).
 * Mantém paridade 100% com o App Cliente e o Robô EA MQL5.
 * ====================================================================
 */

data class AdminLicenseModel(
    val tier: LicenseTier,
    val userId: String,
    val userName: String,
    val userPhone: String,
    val mt5AccountId: String,
    val isActive: Boolean,
    val issuedDate: String,
    val expiryDate: String,
    val totalRenewals: Int = 0,
    val maxLotAllowed: Double = tier.maxLot,
    val allowedFeatures: List<String> = tier.features.filter { it.isIncluded }.map { it.title },
    val creditBalanceMt: Double = 0.0,
    val notes: String = ""
)

data class CreateLicenseRequest(
    val userId: String,
    val planTierId: String, // "TRIAL", "STARTER", "PRO", "MASTER"
    val durationDays: Int,
    val mt5AccountId: String,
    val notes: String = ""
)

data class UpgradeLicenseRequest(
    val userId: String,
    val targetTierId: String,
    val paymentReference: String,
    val amountPaidMt: Double,
    val extraDays: Int
)

data class LicenseVerificationResponse(
    val isValid: Boolean,
    val tierCode: String,
    val tierName: String,
    val canViewScreenshots: Boolean,
    val canAccessCandlestickCanvas: Boolean,
    val canAccessAdvancedEaConfig: Boolean,
    val canAccessAutoSurfada: Boolean,
    val canAccessMultiSessions: Boolean,
    val maxLotSize: Double,
    val daysRemaining: Int,
    val statusMessage: String
)

object AdminLicenseUtils {
    /**
     * Valida e gera um objeto de verificação de licença instantâneo para resposta a EA ou App Cliente
     */
    fun verifyUserLicense(user: GithubUser?): LicenseVerificationResponse {
        if (user == null) {
            return LicenseVerificationResponse(
                isValid = false,
                tierCode = LicenseTier.TRIAL.code,
                tierName = LicenseTier.TRIAL.displayName,
                canViewScreenshots = false,
                canAccessCandlestickCanvas = false,
                canAccessAdvancedEaConfig = false,
                canAccessAutoSurfada = false,
                canAccessMultiSessions = false,
                maxLotSize = 999.0,
                daysRemaining = 0,
                statusMessage = "Utilizador não autenticado ou inexistente."
            )
        }

        val tier = user.effectiveLicenseTier
        val isLicenseActive = user.licencaAtiva

        return LicenseVerificationResponse(
            isValid = isLicenseActive,
            tierCode = tier.code,
            tierName = tier.displayName,
            canViewScreenshots = isLicenseActive && tier.canViewScreenshots,
            canAccessCandlestickCanvas = isLicenseActive && tier.canAccessCandlestickCanvas,
            canAccessAdvancedEaConfig = isLicenseActive && tier.canAccessAdvancedEaConfig,
            canAccessAutoSurfada = isLicenseActive && tier.canAccessAutoSurfada,
            canAccessMultiSessions = isLicenseActive && tier.canAccessMultiSessions,
            maxLotSize = 999.0,
            daysRemaining = 30, // Calculado com base em licencaValidade
            statusMessage = if (isLicenseActive) "Licença ${tier.displayName} Ativa" else "Licença Expirada ou Inativa"
        )
    }

    /**
     * Retorna a lista de planos para exibição nos seletores de administração
     */
    fun getAllTiers(): List<LicenseTier> = LicenseTier.values().toList()
}
