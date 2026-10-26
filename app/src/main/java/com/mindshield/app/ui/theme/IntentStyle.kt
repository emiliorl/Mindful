package com.mindshield.app.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WorkOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.mindshield.app.data.IntentType

val IntentType.icon: ImageVector
    get() = when (this) {
        IntentType.SOCIAL_MEDIA  -> Icons.Rounded.Forum
        IntentType.WORK          -> Icons.Rounded.WorkOutline
        IntentType.STUDY         -> Icons.AutoMirrored.Rounded.MenuBook
        IntentType.FITNESS       -> Icons.Rounded.FitnessCenter
        IntentType.ENTERTAINMENT -> Icons.Rounded.Movie
        IntentType.JUST_LOOKING  -> Icons.Rounded.Visibility
    }

/**
 * Theme-aware intent accent. Each value is >= 4.5:1 as text on the plain surface and on
 * surfaceContainer{Lowest,Low,}, and as text on a 12-14% accent tint of those.
 * Light theme uses darker variants, dark theme lighter ones.
 */
@Composable
@ReadOnlyComposable
fun IntentType.accent(): Color {
    // Follow the resolved scheme (also correct under dynamicColor): a light scheme has a
    // brighter background than its onBackground.
    val bg = MaterialTheme.colorScheme.background
    val fg = MaterialTheme.colorScheme.onBackground
    val light = (bg.red + bg.green + bg.blue) > (fg.red + fg.green + fg.blue)
    return if (light) when (this) {
        IntentType.SOCIAL_MEDIA  -> Color(0xFFA8231B)
        IntentType.WORK          -> Color(0xFF17548F)
        IntentType.STUDY         -> Color(0xFF1B5E28)
        IntentType.FITNESS       -> Color(0xFF7F4600)
        IntentType.ENTERTAINMENT -> Color(0xFF6F2A94)
        IntentType.JUST_LOOKING  -> Color(0xFF41596A)
    } else when (this) {
        IntentType.SOCIAL_MEDIA  -> Color(0xFFEF9A9A)
        IntentType.WORK          -> Color(0xFF64B5F6)
        IntentType.STUDY         -> Color(0xFF81C784)
        IntentType.FITNESS       -> Color(0xFFFFB74D)
        IntentType.ENTERTAINMENT -> Color(0xFFCE93D8)
        IntentType.JUST_LOOKING  -> Color(0xFF90A4AE)
    }
}
