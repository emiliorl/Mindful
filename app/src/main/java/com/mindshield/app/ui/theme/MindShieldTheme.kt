package com.mindshield.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Calm, intentional green palette
private val primaryLight         = Color(0xFF2E7D52)   // forest green
private val onPrimaryLight       = Color(0xFFFFFFFF)
private val primaryContainerLight= Color(0xFFB7F0CC)
private val backgroundLight      = Color(0xFFF8FBF8)
private val surfaceLight         = Color(0xFFF8FBF8)
private val onSurfaceLight       = Color(0xFF1A1C1A)

private val primaryDark          = Color(0xFF6EE0A0)
private val onPrimaryDark        = Color(0xFF003920)
private val primaryContainerDark = Color(0xFF0D5D39)
private val backgroundDark       = Color(0xFF111411)
private val surfaceDark          = Color(0xFF111411)
private val onSurfaceDark        = Color(0xFFE1E3DF)

private val LightColors = lightColorScheme(
    primary            = primaryLight,
    onPrimary          = onPrimaryLight,
    primaryContainer   = primaryContainerLight,
    background         = backgroundLight,
    surface            = surfaceLight,
    onSurface          = onSurfaceLight,
)

private val DarkColors = darkColorScheme(
    primary            = primaryDark,
    onPrimary          = onPrimaryDark,
    primaryContainer   = primaryContainerDark,
    background         = backgroundDark,
    surface            = surfaceDark,
    onSurface          = onSurfaceDark,
)

@Composable
fun MindShieldTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,   // Material You on Android 12+
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkColors
        else      -> LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography(),
        content     = content
    )
}
