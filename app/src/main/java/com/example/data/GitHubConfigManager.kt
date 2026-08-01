package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig

data class GitHubAdminConfig(
    val token: String = "",
    val repository: String = "Macucul/fimaster",
    val branch: String = "main",
    val path: String = "dados/usuarios/"
)

class GitHubConfigManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("fimaster_admin_prefs", Context.MODE_PRIVATE)
    private val appContext: Context = context.applicationContext

    private fun encrypt(value: String): String {
        if (value.isBlank()) return ""
        return try {
            val key = "fimaster_security_salt_2026"
            val xorBytes = value.toByteArray(Charsets.UTF_8).mapIndexed { index, byte ->
                (byte.toInt() xor key[index % key.length].toInt()).toByte()
            }.toByteArray()
            android.util.Base64.encodeToString(xorBytes, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            value
        }
    }

    private fun decrypt(value: String): String {
        if (value.isBlank()) return ""
        return try {
            val key = "fimaster_security_salt_2026"
            val decoded = android.util.Base64.decode(value, android.util.Base64.NO_WRAP)
            val decryptedBytes = decoded.mapIndexed { index, byte ->
                (byte.toInt() xor key[index % key.length].toInt()).toByte()
            }.toByteArray()
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            value
        }
    }

    fun saveConfig(config: GitHubAdminConfig) {
        prefs.edit().apply {
            putString("github_token", encrypt(config.token.trim()))
            putString("github_repo", config.repository.trim())
            putString("github_branch", config.branch.trim())
            putString("github_path", config.path.trim().let { 
                var p = it
                if (p.isNotEmpty() && !p.endsWith("/")) {
                    p += "/"
                }
                p
            })
            apply()
        }
    }

    fun getConfig(): GitHubAdminConfig {
        var token = decrypt(prefs.getString("github_token", "") ?: "")
        var repo = prefs.getString("github_repo", "") ?: ""
        if (token.isBlank()) {
            token = BuildConfig.GITHUB_DEFAULT_TOKEN
        }
        if (repo.isBlank()) {
            repo = "Macucul/fimaster"
        }
        val branch = prefs.getString("github_branch", "main") ?: "main"
        val path = prefs.getString("github_path", "dados/usuarios/") ?: "dados/usuarios/"
        return GitHubAdminConfig(token, repo, branch, path)
    }

    fun isConfigured(): Boolean {
        val config = getConfig()
        return config.token.isNotBlank() && config.repository.isNotBlank()
    }

    // New Data Source Mode support (GITHUB or FIREBASE)
    fun getDataSourceMode(): String {
        return prefs.getString("data_source_mode", "GITHUB") ?: "GITHUB"
    }

    fun setDataSourceMode(mode: String) {
        prefs.edit().putString("data_source_mode", mode).apply()
    }

    // Firebase Realtime Database URL configurations
    fun getFirebaseUrl(): String {
        val saved = prefs.getString("firebase_url", "") ?: ""
        if (saved.isNotBlank()) return saved

        // Fallback to automatically generated Firebase string if available
        return try {
            val resId = appContext.resources.getIdentifier("firebase_database_url", "string", appContext.packageName)
            if (resId != 0) {
                appContext.getString(resId)
            } else {
                "https://fimaster-sms-gateway-default-rtdb.firebaseio.com"
            }
        } catch (e: Exception) {
            "https://fimaster-sms-gateway-default-rtdb.firebaseio.com"
        }
    }

    fun setFirebaseUrl(url: String) {
        prefs.edit().putString("firebase_url", url.trim().removeSuffix("/")).apply()
    }
}

