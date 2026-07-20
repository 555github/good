package com.example.chatimage.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.example.chatimage.data.model.AppearanceSettings
import com.example.chatimage.data.model.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF176B5B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB7F0DD),
    onPrimaryContainer = Color(0xFF002019),
    secondary = Color(0xFF5A5F70),
    surface = Color(0xFFF9FAF7),
    surfaceVariant = Color(0xFFE2E4E0),
    background = Color(0xFFF9FAF7)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9BD3C2),
    onPrimary = Color(0xFF00382E),
    primaryContainer = Color(0xFF005143),
    onPrimaryContainer = Color(0xFFB7F0DD),
    secondary = Color(0xFFC2C6D6),
    surface = Color(0xFF111412),
    surfaceVariant = Color(0xFF424844),
    background = Color(0xFF111412)
)

@Composable
fun ChatImageTheme(
    appearance: AppearanceSettings,
    content: @Composable () -> Unit
) {
    val dark = when (appearance.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val density = LocalDensity.current
    val fontScale = appearance.fontScale.coerceIn(0.7f, 2f)

    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors
    ) {
        CompositionLocalProvider(
            LocalDensity provides Density(
                density = density.density,
                fontScale = density.fontScale * fontScale
            ),
            content = content
        )
    }
}
