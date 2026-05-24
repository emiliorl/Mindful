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
// Phase 0 placeholders — each will be replaced in subsequent phases
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Home screen — Phase 1 will add IntentPickerScreen + session timer.
 */
@Composable
fun HomeScreen() {
    PlaceholderScreen(
        title = "Home",
        subtitle = "Your active session will appear here.\n(Phase 1 — coming up)"
    )
}

/**
 * Apps screen — Phase 2 will add the friction-toggle list.
 */
@Composable
fun AppsScreen() {
    PlaceholderScreen(
        title = "Apps",
        subtitle = "Toggle friction for specific apps here.\n(Phase 2 — coming up)"
    )
}

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
