package com.example.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Centralized secure preferences helper using EncryptedSharedPreferences.
 */
object SecurePrefs {
    private const val FILE_NAME = "secure_prefs"

    fun getEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Migrate values from an old SharedPreferences file into encrypted prefs.
     * Use once during app upgrade and then delete the old sensitive values.
     */
    fun migrateFromOldPrefs(context: Context, oldPrefsName: String) {
        try {
            val plain = context.getSharedPreferences(oldPrefsName, Context.MODE_PRIVATE)
            val secure = getEncryptedPrefs(context)
            val editor = secure.edit()

            for ((key, value) in plain.all) {
                when (value) {
                    is String -> editor.putString(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    else -> { /* ignore */ }
                }
            }
            editor.apply()

            // Remove sensitive keys from old prefs
            val oldEditor = plain.edit()
            for (sensitiveKey in listOf("github_token", "fastapi_token", "github_token_encrypted")) {
                if (plain.contains(sensitiveKey)) oldEditor.remove(sensitiveKey)
            }
            oldEditor.apply()
        } catch (e: Exception) {
            // Avoid logging secrets; print stack for debug in staging only
            e.printStackTrace()
        }
    }
}
