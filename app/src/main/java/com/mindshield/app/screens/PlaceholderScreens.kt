package com.mindshield.app.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// ─────────────────────────────────────────────────────────────────────────────
// Phase 0 placeholders — replaced per phase
// HomeScreen  → screens/HomeScreen.kt (Phase 1)
// AppsScreen  → screens/AppsScreen.kt (Phase 2)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Notifications screen — Phase 3 will add batching controls.
 */
@Composable
fun NotificationsScreen() {
    PlaceholderScreen(
        title = "Silence",
        subtitle = "Batch and schedule your notifications here.\n(Phase 3 — coming up)"
    )
}

/**
 * Routines screen — Phase 4 will add morning / wind-down routines.
 */
@Composable
fun RoutinesScreen() {
    PlaceholderScreen(
        title = "Routines",
        subtitle = "Set up your morning and wind-down routines here.\n(Phase 4 — coming up)"
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared placeholder layout
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PlaceholderScreen(title: String, subtitle: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
