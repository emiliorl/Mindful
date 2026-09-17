package com.mindshield.app.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.eventFlow
import com.mindshield.app.util.PermissionStatus
import kotlinx.coroutines.flow.filterIsInstance

// ─────────────────────────────────────────────────────────────────────────────
// Permission row model
// ─────────────────────────────────────────────────────────────────────────────

private enum class PermissionKind(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val settingsAction: String
) {
    ACCESSIBILITY(
        title = "Accessibility service",
        description = "Detects when you open a friction-enabled app.",
        icon = Icons.Outlined.Accessibility,
        settingsAction = Settings.ACTION_ACCESSIBILITY_SETTINGS
    ),
    NOTIFICATIONS(
        title = "Notification access",
        description = "Lets MindShield hold and batch notifications.",
        icon = Icons.Outlined.Notifications,
        settingsAction = Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
    ),
    BATTERY(
        title = "Battery optimisation",
        description = "Keeps sessions and routines running reliably in the background.",
        icon = Icons.Outlined.BatterySaver,
        settingsAction = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
    ),
    OVERLAY(
        title = "Display over other apps",
        description = "Lets MindShield show the pause screen before an app opens.",
        icon = Icons.Outlined.Layers,
        settingsAction = Settings.ACTION_MANAGE_OVERLAY_PERMISSION
    );
}

// ─────────────────────────────────────────────────────────────────────────────
// Entry point
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    fun currentStatus(kind: PermissionKind): Boolean = when (kind) {
        PermissionKind.ACCESSIBILITY -> PermissionStatus.isAccessibilityEnabled(context)
        PermissionKind.NOTIFICATIONS -> PermissionStatus.isNotificationListenerEnabled(context)
        PermissionKind.BATTERY       -> PermissionStatus.isIgnoringBatteryOptimizations(context)
        PermissionKind.OVERLAY       -> PermissionStatus.canDrawOverlays(context)
    }

    var statuses by remember {
        mutableStateOf(PermissionKind.entries.associateWith(::currentStatus))
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.eventFlow
            .filterIsInstance<Lifecycle.Event>()
            .collect { event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    statuses = PermissionKind.entries.associateWith(::currentStatus)
                }
            }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Permissions MindShield needs to keep working. Android can revoke these on its own — fix any that fall out of grant here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HorizontalDivider()

        PermissionKind.entries.forEach { kind ->
            PermissionStatusRow(
                kind = kind,
                granted = statuses[kind] == true,
                onFix = {
                    val intent = when (kind) {
                        PermissionKind.BATTERY, PermissionKind.OVERLAY ->
                            Intent(kind.settingsAction, Uri.parse("package:${context.packageName}"))
                        else -> Intent(kind.settingsAction)
                    }
                    context.startActivity(intent)
                }
            )
            HorizontalDivider()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Permission row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PermissionStatusRow(
    kind: PermissionKind,
    granted: Boolean,
    onFix: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = kind.icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(kind.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                kind.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.width(12.dp))

        if (granted) {
            Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = "Granted",
                tint = MaterialTheme.colorScheme.primary
            )
        } else {
            Button(onClick = onFix) { Text("Fix") }
        }
    }
}
