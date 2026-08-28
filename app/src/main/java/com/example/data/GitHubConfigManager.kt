package com.example.data

import android.content.Context
import com.example.network.HttpClient

data class GitHubAdminConfig(
    val token: String = "",
    val repository: String = "Macucul/fimaster",
    val branch: String = "main",
    val path: String = "dados/usuarios/"
)

class GitHubConfigManager(private val context: Context) {
    private val securePrefs by lazy { SecurePrefs.getEncryptedPrefs(context.applicationContext) }

    fun saveConfig(config: GitHubAdminConfig) {
        securePrefs.edit().apply {
            putString("github_token", config.token.trim())
            putString("github_repo", config.repository.trim())
            putString("github_branch", config.branch.trim())
            putString("github_path", config.path.trim().let {
                var p = it
                if (p.isNotEmpty() && !p.endsWith("/")) p += "/"
                p
            })
            apply()
        }
    }

    fun getConfig(): GitHubAdminConfig {
        val token = securePrefs.getString("github_token", "") ?: ""
        var repo = securePrefs.getString("github_repo", "") ?: ""
        if (repo.isBlank()) repo = "Macucul/fimaster"
        val branch = securePrefs.getString("github_branch", "main") ?: "main"
        val path = securePrefs.getString("github_path", "dados/usuarios/") ?: "dados/usuarios/"
        return GitHubAdminConfig(token, repo, branch, path)
    }

    fun isConfigured(): Boolean {
        val cfg = getConfig()
        return cfg.token.isNotBlank() && cfg.repository.isNotBlank()
    }

    fun getDataSourceMode(): String {
        return securePrefs.getString("data_source_mode", "HYBRID_FIREBASE_PRIMARY") ?: "HYBRID_FIREBASE_PRIMARY"
    }

    fun setDataSourceMode(mode: String) {
        securePrefs.edit().putString("data_source_mode", mode.trim()).apply()
    }

    fun getFirebaseUrl(): String {
        return securePrefs.getString("firebase_url", "https://fimaster-default-rtdb.firebaseio.com") ?: "https://fimaster-default-rtdb.firebaseio.com"
    }

    fun setFirebaseUrl(url: String) {
        securePrefs.edit().putString("firebase_url", url.trim()).apply()
    }

    fun migrateFromLegacy(oldPrefsName: String) {
        SecurePrefs.migrateFromOldPrefs(context, oldPrefsName)
    }

    fun getTokenOrNull(): String? {
        val t = securePrefs.getString("github_token", "")
        return if (t.isNullOrBlank()) null else t
    }

    companion object {
        /**
         * Initialize global wiring: set HttpClient.tokenProvider to read from SecurePrefs via GitHubConfigManager.
         * Call this once from Application.onCreate() or other early bootstrap (staging first).
         */
        @JvmStatic
        fun initialize(appContext: Context) {
            val manager = GitHubConfigManager(appContext.applicationContext)
            HttpClient.tokenProvider = object : HttpClient.TokenProvider {
                override fun getToken(): String? = manager.getTokenOrNull()
            }
        }
    }
}
