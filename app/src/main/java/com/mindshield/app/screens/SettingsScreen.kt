package com.mindshield.app.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.WbTwilight
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.eventFlow
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mindshield.app.data.AccountabilityPartnerStore
import com.mindshield.app.data.MorningConfig
import com.mindshield.app.data.WindDownConfig
import com.mindshield.app.ui.components.SectionHeader
import com.mindshield.app.util.PermissionStatus
import com.mindshield.app.viewmodel.RoutinesViewModel
import kotlinx.coroutines.flow.filterIsInstance
import java.time.format.DateTimeFormatter

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
fun SettingsScreen(onOpenRoutines: () -> Unit = {}) {
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

    val partnerEmail by AccountabilityPartnerStore.partnerEmail.collectAsState()
    val partnerEnabled by AccountabilityPartnerStore.enabled.collectAsState()
    val triggerWeeklyDigest by AccountabilityPartnerStore.triggerWeeklyDigest.collectAsState()
    val triggerSessionEnd by AccountabilityPartnerStore.triggerSessionEnd.collectAsState()
    val triggerViolationAlert by AccountabilityPartnerStore.triggerViolationAlert.collectAsState()
    val pendingShareLabel by AccountabilityPartnerStore.pendingShareLabel.collectAsState()
    val pendingShareText by AccountabilityPartnerStore.pendingShareText.collectAsState()

    var showSetupSheet by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }

    val routinesVm: RoutinesViewModel = viewModel()
    val morning by routinesVm.morning.collectAsStateWithLifecycle()
    val windDown by routinesVm.windDown.collectAsStateWithLifecycle()
    val morningStreak by routinesVm.morningStreak.collectAsStateWithLifecycle()
    val windDownStreak by routinesVm.windDownStreak.collectAsStateWithLifecycle()

    val needAttention = statuses.values.count { !it }

    val accountabilitySubtitle = when {
        pendingShareLabel != null -> "$pendingShareLabel ready to send"
        partnerEmail.isBlank()    -> "Not set up"
        else                      -> partnerEmail
    }

    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        // ── Permissions & health ────────────────────────────────────────────
        SectionHeader("Permissions & health")
        Text(
            text = if (needAttention == 0) "All set" else "$needAttention need attention",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (needAttention == 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp)
        )
        Text(
            text = "Android can revoke these on its own. Fix any that fall out of grant here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
        )

        PermissionKind.entries.forEachIndexed { index, kind ->
            if (index > 0) HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
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
        }

        // ── Daily routines ──────────────────────────────────────────────────
        Spacer(Modifier.height(8.dp))
        SectionHeader("Daily routines")
        SettingsNavRow(
            icon = Icons.Rounded.WbTwilight,
            title = "Morning & wind-down",
            subtitle = routinesSummary(morning, windDown, maxOf(morningStreak, windDownStreak)),
            onClick = onOpenRoutines
        )

        // ── Accountability Partner ──────────────────────────────────────────
        Spacer(Modifier.height(8.dp))
        SectionHeader("Accountability Partner")
        SettingsNavRow(
            icon = Icons.Rounded.Group,
            title = "Accountability Partner",
            subtitle = accountabilitySubtitle,
            highlighted = pendingShareLabel != null,
            onClick = {
                if (pendingShareLabel != null) showShareSheet = true else showSetupSheet = true
            }
        )

        // ── About ───────────────────────────────────────────────────────────
        Spacer(Modifier.height(8.dp))
        SectionHeader("About")
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text(
                text = "MindShield" + (versionName?.let { " $it" } ?: ""),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "MindShield adds a moment of intention before you open an app. " +
                    "It never blocks you; it simply asks you to pause and choose. " +
                    "Everything it knows stays on this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showSetupSheet) {
        AccountabilityPartnerSetupSheet(
            partnerEmail           = partnerEmail,
            enabled                = partnerEnabled,
            triggerWeeklyDigest    = triggerWeeklyDigest,
            triggerSessionEnd      = triggerSessionEnd,
            triggerViolationAlert  = triggerViolationAlert,
            onSave = { email, enabled, weekly, sessionEnd, violation ->
                AccountabilityPartnerStore.setPartner(context, email, enabled, weekly, sessionEnd, violation)
            },
            onDismiss = { showSetupSheet = false }
        )
    }

    if (showShareSheet && pendingShareLabel != null && pendingShareText != null) {
        AccountabilityShareSheet(
            label        = pendingShareLabel ?: "",
            initialText  = pendingShareText ?: "",
            partnerEmail = partnerEmail,
            onSend = { editedText ->
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:")
                    putExtra(Intent.EXTRA_EMAIL, arrayOf(partnerEmail))
                    putExtra(Intent.EXTRA_SUBJECT, "MindShield update — $pendingShareLabel")
                    putExtra(Intent.EXTRA_TEXT, editedText)
                }
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                } else {
                    Toast.makeText(context, "No email app found", Toast.LENGTH_SHORT).show()
                }
                AccountabilityPartnerStore.clearPendingShare(context)
            },
            onDiscard = {
                AccountabilityPartnerStore.clearPendingShare(context)
            },
            onEditSettings = {
                showShareSheet = false
                showSetupSheet = true
            },
            onDismiss = { showShareSheet = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Rows
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun IconCircle(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun PermissionStatusRow(
    kind: PermissionKind,
    granted: Boolean,
    onFix: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconCircle(kind.icon)

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
                Icons.Rounded.Check,
                contentDescription = "Granted",
                tint = MaterialTheme.colorScheme.primary
            )
        } else {
            FilledTonalButton(onClick = onFix) { Text("Fix") }
        }
    }
}

/** Clickable row with a leading icon circle, title, subtitle and trailing chevron. */
@Composable
private fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    highlighted: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconCircle(icon)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (highlighted) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal
            )
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun routinesSummary(morning: MorningConfig, windDown: WindDownConfig, streak: Int): String {
    val fmt = DateTimeFormatter.ofPattern("h:mm a")
    val parts = buildList {
        if (morning.enabled) add("Morning ${morning.wakeTime.format(fmt)}")
        if (windDown.enabled) add("Wind-down ${windDown.startTime.format(fmt)}")
    }
    val base = if (parts.isEmpty()) "Off" else parts.joinToString(" · ")
    return if (streak > 0) "$base · $streak-day streak" else base
}
