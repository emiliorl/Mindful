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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// Calm forest-green palette. Every M3 role is defined so nothing falls back to the
// stock purple baseline. Seed #1E6B47, warm amber tertiary.

private val LightColors = lightColorScheme(
    primary                 = Color(0xFF1E6B47),
    onPrimary               = Color(0xFFFFFFFF),
    primaryContainer        = Color(0xFFD7F0E0),
    onPrimaryContainer      = Color(0xFF07241A),
    inversePrimary          = Color(0xFF52D18D),
    secondary               = Color(0xFF4C6356),
    onSecondary             = Color(0xFFFFFFFF),
    secondaryContainer      = Color(0xFFCFE8D8),
    onSecondaryContainer    = Color(0xFF0A2015),
    tertiary                = Color(0xFF8A5100),
    onTertiary              = Color(0xFFFFFFFF),
    tertiaryContainer       = Color(0xFFFFDDB8),
    onTertiaryContainer     = Color(0xFF2B1700),
    background              = Color(0xFFF8FAF8),
    onBackground            = Color(0xFF191C1A),
    surface                 = Color(0xFFF8FAF8),
    onSurface               = Color(0xFF191C1A),
    surfaceVariant          = Color(0xFFDDE5DF),
    onSurfaceVariant        = Color(0xFF404943),
    surfaceTint             = Color(0xFF1E6B47),
    inverseSurface          = Color(0xFF2E312F),
    inverseOnSurface        = Color(0xFFF0F1EE),
    error                   = Color(0xFFBA1A1A),
    onError                 = Color(0xFFFFFFFF),
    errorContainer          = Color(0xFFFFDAD6),
    onErrorContainer        = Color(0xFF410002),
    outline                 = Color(0xFF707972),
    outlineVariant          = Color(0xFFC0C9C2),
    scrim                   = Color(0xFF000000),
    surfaceBright           = Color(0xFFF8FAF8),
    surfaceDim              = Color(0xFFD8DBD8),
    surfaceContainerLowest  = Color(0xFFFFFFFF),
    surfaceContainerLow     = Color(0xFFF1F5F1),
    surfaceContainer        = Color(0xFFEBF0EB),
    surfaceContainerHigh    = Color(0xFFE5EAE5),
    surfaceContainerHighest = Color(0xFFDFE4DF),
)

private val DarkColors = darkColorScheme(
    primary                 = Color(0xFF52D18D),
    onPrimary               = Color(0xFF00391F),
    primaryContainer        = Color(0xFF1C2E24),
    onPrimaryContainer      = Color(0xFFC4EDD3),
    inversePrimary          = Color(0xFF1E6B47),
    secondary               = Color(0xFFB2CCBB),
    onSecondary             = Color(0xFF1F352A),
    secondaryContainer      = Color(0xFF35493D),
    onSecondaryContainer    = Color(0xFFCFE8D8),
    tertiary                = Color(0xFFFFB95C),
    onTertiary              = Color(0xFF452B00),
    tertiaryContainer       = Color(0xFF4A2C00),
    onTertiaryContainer     = Color(0xFFFFDDB8),
    background              = Color(0xFF111412),
    onBackground            = Color(0xFFE2E3E0),
    surface                 = Color(0xFF111412),
    onSurface               = Color(0xFFE2E3E0),
    surfaceVariant          = Color(0xFF3F4942),
    onSurfaceVariant        = Color(0xFFBFC9C2),
    surfaceTint             = Color(0xFF52D18D),
    inverseSurface          = Color(0xFFE2E3E0),
    inverseOnSurface        = Color(0xFF2E312F),
    error                   = Color(0xFFFFB4AB),
    onError                 = Color(0xFF690005),
    errorContainer          = Color(0xFF93000A),
    onErrorContainer        = Color(0xFFFFDAD6),
    outline                 = Color(0xFF8A938C),
    outlineVariant          = Color(0xFF3F4942),
    scrim                   = Color(0xFF000000),
    surfaceBright           = Color(0xFF373A37),
    surfaceDim              = Color(0xFF111412),
    surfaceContainerLowest  = Color(0xFF0C0F0D),
    surfaceContainerLow     = Color(0xFF191C1A),
    surfaceContainer        = Color(0xFF1D201E),
    surfaceContainerHigh    = Color(0xFF282B29),
    surfaceContainerHighest = Color(0xFF323634),
)

private val BaseTypography = Typography()

private val MindShieldTypography = Typography(
    headlineLarge  = BaseTypography.headlineLarge.copy(letterSpacing = (-0.5).sp),
    headlineMedium = BaseTypography.headlineMedium.copy(letterSpacing = (-0.25).sp),
    headlineSmall  = BaseTypography.headlineSmall.copy(letterSpacing = (-0.1).sp),
    titleLarge     = BaseTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium    = BaseTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall     = BaseTypography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
)

@Composable
fun MindShieldTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,   // brand palette by default; Material You is opt-in
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
        typography  = MindShieldTypography,
        content     = content
    )
}
