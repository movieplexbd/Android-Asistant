package com.jarvis.ceotitan.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val JarvisBlue = Color(0xFF0A84FF)
val JarvisDarkBlue = Color(0xFF003D99)
val JarvisLightBlue = Color(0xFFE8F0FE)
val JarvisSilver = Color(0xFFB0BEC5)
val JarvisWhite = Color(0xFFFFFFFF)
val JarvisBackground = Color(0xFFF5F7FF)
val JarvisCardBg = Color(0xFFFFFFFF)
val JarvisAccent = Color(0xFF00D4FF)
val JarvisGold = Color(0xFFFFB300)
val JarvisSuccess = Color(0xFF00C853)
val JarvisError = Color(0xFFFF1744)

private val LightColorScheme = lightColorScheme(
    primary = JarvisBlue,
    onPrimary = JarvisWhite,
    primaryContainer = JarvisLightBlue,
    onPrimaryContainer = JarvisDarkBlue,
    secondary = JarvisAccent,
    onSecondary = JarvisWhite,
    secondaryContainer = Color(0xFFE0F7FA),
    onSecondaryContainer = Color(0xFF006064),
    background = JarvisBackground,
    onBackground = Color(0xFF1A1C2E),
    surface = JarvisCardBg,
    onSurface = Color(0xFF1A1C2E),
    surfaceVariant = Color(0xFFEEF2FF),
    onSurfaceVariant = Color(0xFF4A4F6A),
    error = JarvisError,
    onError = JarvisWhite,
    outline = Color(0xFFCCD0E0),
    outlineVariant = Color(0xFFE8ECFF)
)

@Composable
fun JarvisCEOTitanTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = JarvisTypography,
        content = content
    )
}
