package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.data.AppDatabase
import com.example.data.PortalRepository
import com.example.ui.PortalApp
import com.example.ui.PortalViewModel
import com.example.ui.PortalViewModelFactory
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    
    // Initialize Database and Repository
    val database = AppDatabase.getDatabase(this)
    val repository = PortalRepository(database.userProfileDao(), database.refundRequestDao(), database.eaConfigDao())
    
    // Create ViewModel using Factory
    val factory = PortalViewModelFactory(application, repository)
    val viewModel = ViewModelProvider(this, factory)[PortalViewModel::class.java]

    setContent {
      MyApplicationTheme {
        PortalApp(viewModel = viewModel)
      }
    }
  }
}
