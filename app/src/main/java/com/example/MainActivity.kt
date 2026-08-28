package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.data.AppDatabase
import com.example.data.PortalRepository
import com.example.service.EaEventMonitorService
import com.example.ui.PortalApp
import com.example.ui.PortalViewModel
import com.example.ui.PortalViewModelFactory
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Enable screen lock features so app functions on lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Request POST_NOTIFICATIONS on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Initialize Database and Repository
        com.example.data.NotificationStateManager.initIfNeeded(this)
        handleIntentGid(intent)
        val database = AppDatabase.getDatabase(this)
        val repository = PortalRepository(
            database.userProfileDao(),
            database.refundRequestDao(),
            database.eaConfigDao(),
            database.eaRobotEventDao(),
            database.syncMetadataDao()
        )

        // Create ViewModel using Factory
        val factory = PortalViewModelFactory(application, repository)
        val viewModel = ViewModelProvider(this, factory)[PortalViewModel::class.java]

        setContent {
            MyApplicationTheme {
                PortalApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntentGid(intent)
    }

    private fun handleIntentGid(intent: android.content.Intent?) {
        val eventGid = intent?.getStringExtra("EVENT_GID")
        if (!eventGid.isNullOrBlank()) {
            com.example.data.NotificationStateManager.setFocusedEventGid(eventGid)
        }
    }
}
