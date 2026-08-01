package com.example.data.security

import android.content.Context
import android.provider.Settings
import java.util.UUID

class DeviceIdentityManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("fimaster_security_prefs", Context.MODE_PRIVATE)

    /**
     * Obtém ou gera um UID Silencioso e Permanente para este telemóvel
     */
    fun getSilentDeviceUid(): String {
        // 1. Tenta recuperar UID salvo previamente
        var savedUid = prefs.getString("KEY_SILENT_DEVICE_UID", null)
        if (!savedUid.isNullOrEmpty()) {
            return savedUid
        }

        // 2. Se não existir, tenta capturar o ANDROID_ID do sistema
        val androidId = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (e: Exception) {
            null
        }

        // 3. Define o ID único (se ANDROID_ID for inválido ou nulo, gera um UUID)
        savedUid = if (!androidId.isNullOrEmpty() && androidId != "9774d56d682e549c") {
            androidId
        } else {
            UUID.randomUUID().toString()
        }

        // 4. Guarda persistentemente nas SharedPreferences
        prefs.edit().putString("KEY_SILENT_DEVICE_UID", savedUid).apply()
        return savedUid
    }
}
