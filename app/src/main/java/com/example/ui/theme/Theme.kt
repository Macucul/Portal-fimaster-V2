package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val CosmicSlateColorScheme = darkColorScheme(
    primary = CosmicPrimary,
    onPrimary = CosmicOnPrimary,
    secondary = CosmicSecondary,
    tertiary = CosmicTertiary,
    background = CosmicBackground,
    surface = CosmicSurface,
    surfaceVariant = CosmicSurfaceVariant,
    error = CosmicError,
    onBackground = CosmicOnBackground,
    onSurface = CosmicOnSurface,
    onSurfaceVariant = CosmicOnSurfaceVariant
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force dark theme for the Cosmic Slate aesthetic
    dynamicColor: Boolean = false, // Disable dynamic colors to preserve our brand identity
    content: @Composable () -> Unit,
) {
    val colorScheme = CosmicSlateColorScheme

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
