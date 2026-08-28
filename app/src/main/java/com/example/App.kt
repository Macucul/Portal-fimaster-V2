package com.example

import android.app.Application
import com.example.data.GitHubConfigManager

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize GitHubConfigManager token provider so HttpClient can attach Authorization headers
        GitHubConfigManager.initialize(this)

        // Optionally migrate legacy prefs on first-run (run in staging first)
        // GitHubConfigManager(this).migrateFromLegacy("fimaster_admin_prefs")
    }
}
