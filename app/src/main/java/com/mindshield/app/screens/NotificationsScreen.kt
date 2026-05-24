package com.mindshield.app.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.eventFlow
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mindshield.app.data.BatchCategory
import com.mindshield.app.data.BatchRule
import com.mindshield.app.data.HeldNotification
import com.mindshield.app.service.ZoneManagerService
import com.mindshield.app.viewmodel.AppEntry
import com.mindshield.app.viewmodel.DeliveredBatch
import com.mindshield.app.viewmodel.NotificationsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.withContext
import java.time.LocalTime

// ─────────────────────────────────────────────────────────────────────────────
// Preset delivery times (label → LocalTime)
// ─────────────────────────────────────────────────────────────────────────────

private val DELIVERY_PRESETS = listOf(
    "Morning"   to LocalTime.of(8, 0),
    "Midday"    to LocalTime.of(12, 0),
    "Afternoon" to LocalTime.of(15, 0),
    "Evening"   to LocalTime.of(18, 0),
    "Night"     to LocalTime.of(21, 0)
)

// ─────────────────────────────────────────────────────────────────────────────
// Entry point
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen() {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var listenerEnabled by remember { mutableStateOf(isListenerEnabled(context)) }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.eventFlow
            .filterIsInstance<Lifecycle.Event>()
            .collect { event ->
                if (event == Lifecycle.Event.ON_RESUME) listenerEnabled = isListenerEnabled(context)
            }
    }

    if (!listenerEnabled) {
        NotificationPermissionGate { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        return
    }

    val vm: NotificationsViewModel = viewModel()
    val batches       by vm.batches.collectAsStateWithLifecycle()
    val rules         by vm.rules.collectAsStateWithLifecycle()
    val settings      by vm.globalSettings.collectAsStateWithLifecycle()
    val installedApps by vm.installedApps.collectAsStateWithLifecycle()
    val knownChannels by vm.knownChannels.collectAsStateWithLifecycle()
    val session       by ZoneManagerService.sessionState.collectAsStateWithLifecycle()

    var selectedRule: BatchRule? by remember { mutableStateOf(null) }
    var showAppPicker by remember { mutableStateOf(false) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {

        // ── Page header ───────────────────────────────────────────────────────
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    "Silence",
                    style      = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Non-urgent notifications are held and delivered in calm batches.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider()
        }

        // ── Session banner ────────────────────────────────────────────────────
        if (session != null && settings.batchDuringSession) {
            item {
                Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                    Row(
                        modifier          = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.FiberManualRecord,
                            contentDescription = null,
                            tint     = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${session!!.type.emoji} ${session!!.type.label} session active — all apps held",
                            style      = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color      = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier   = Modifier.weight(1f)
                        )
                    }
                }
                HorizontalDivider()
            }
        }

        // ── Inbox (delivered batches, newest first) ───────────────────────────
        item {
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Inbox",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier   = Modifier.weight(1f)
                )
                TextButton(onClick = vm::deliverNow) { Text("Check now") }
            }
        }

        if (batches.isEmpty()) {
            item { EmptyInboxState() }
        } else {
            batches.forEach { batch ->
                item(key = "header_${batch.deliveredAtMs}") {
                    BatchHeader(batch)
                }
                items(batch.notifications, key = { it.id }) { n ->
                    HeldNotificationRow(n, context)
                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                }
            }
        }

        item { HorizontalDivider(modifier = Modifier.padding(top = 8.dp)) }

        // ── Delivery schedule ─────────────────────────────────────────────────
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    "Delivery schedule",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Tap to toggle. Batched notifications are flushed at each selected time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                )
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DELIVERY_PRESETS.forEach { (label, time) ->
                        val selected = settings.deliveryTimes.contains(time)
                        FilterChip(
                            selected = selected,
                            onClick  = {
                                val newTimes = if (selected) settings.deliveryTimes - time
                                              else settings.deliveryTimes + time
                                vm.setDeliveryTimes(newTimes)
                            },
                            label    = { Text("$label · %02d:%02d".format(time.hour, time.minute)) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Bolt,
                        contentDescription = null,
                        tint     = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Batch during focus sessions",
                        style    = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked         = settings.batchDuringSession,
                        onCheckedChange = vm::setBatchDuringSession
                    )
                }
            }
        }

        item { HorizontalDivider() }

        // ── App rules ─────────────────────────────────────────────────────────
        item {
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "App rules",
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Instant apps always pass through. Batched apps are held.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { showAppPicker = true }) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add")
                }
            }
        }

        if (rules.isEmpty()) {
            item { EmptyRulesState() }
        } else {
            items(rules.values.toList(), key = { it.packageName }) { rule ->
                AppRuleRow(rule, context, onClick = { selectedRule = rule })
                HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }

    // ── Sheets ────────────────────────────────────────────────────────────────
    selectedRule?.let { rule ->
        BatchRuleSheet(
            rule          = rule,
            knownChannels = knownChannels[rule.packageName] ?: emptyMap(),
            onSave        = { updated -> vm.setRule(updated); selectedRule = null },
            onRemove      = { vm.removeRule(rule.packageName); selectedRule = null },
            onDismiss     = { selectedRule = null }
        )
    }

    if (showAppPicker) {
        AppPickerSheet(
            apps      = installedApps.filter { it.packageName !in rules },
            onSelect  = { app ->
                vm.setRule(BatchRule(packageName = app.packageName, appLabel = app.label))
                showAppPicker = false
            },
            onDismiss = { showAppPicker = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Permission gate
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NotificationPermissionGate(onOpenSettings: () -> Unit) {
    Column(
        modifier               = Modifier
            .fillMaxSize()
            .padding(40.dp),
        horizontalAlignment    = Alignment.CenterHorizontally,
        verticalArrangement    = Arrangement.Center
    ) {
        Icon(
            Icons.Outlined.NotificationsOff,
            contentDescription = null,
            modifier           = Modifier.size(72.dp),
            tint               = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "Notification access required",
            style      = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign  = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "MindShield needs notification access to silently intercept and batch notifications. " +
                "Your content never leaves your device.",
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "In Notification access settings, find MindShield and toggle it on.",
            style     = MaterialTheme.typography.bodySmall,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.OpenInNew, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Open Notification access settings")
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Batch header (groups notifications by delivery time)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BatchHeader(batch: DeliveredBatch) {
    val count = batch.notifications.size
    val appCount = batch.notifications.map { it.packageName }.distinct().size
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.Inbox,
            contentDescription = null,
            tint     = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "Delivered ${relativeTime(batch.deliveredAtMs)}",
            style      = MaterialTheme.typography.labelMedium,
            color      = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "· $count notification${if (count == 1) "" else "s"} from $appCount app${if (appCount == 1) "" else "s"}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Held notification row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HeldNotificationRow(n: HeldNotification, context: Context) {
    val icon by produceState<ImageBitmap?>(null, n.packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.packageManager.getApplicationIcon(n.packageName).toBitmap(96, 96).asImageBitmap() }.getOrNull()
        }
    }
    ListItem(
        headlineContent   = { Text(if (n.title.isBlank()) n.appLabel else n.title, maxLines = 1) },
        supportingContent = {
            Column {
                if (n.text.isNotBlank())
                    Text(n.text, maxLines = 2, style = MaterialTheme.typography.bodySmall)
                Text(
                    "${n.appLabel} · ${relativeTime(n.postedAtMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        leadingContent    = { AppIcon(icon) }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// App rule row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AppRuleRow(rule: BatchRule, context: Context, onClick: () -> Unit) {
    val icon by produceState<ImageBitmap?>(null, rule.packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.packageManager.getApplicationIcon(rule.packageName).toBitmap(96, 96).asImageBitmap() }.getOrNull()
        }
    }
    ListItem(
        headlineContent   = { Text(rule.appLabel, maxLines = 1) },
        supportingContent = {
            val (badgeColor, labelText) = when (rule.category) {
                BatchCategory.INSTANT -> MaterialTheme.colorScheme.tertiary to "Instant"
                BatchCategory.BATCHED -> MaterialTheme.colorScheme.primary to "Batched"
            }
            Surface(
                color = badgeColor.copy(alpha = 0.12f),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    labelText,
                    style    = MaterialTheme.typography.labelSmall,
                    color    = badgeColor,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        },
        leadingContent    = { AppIcon(icon) },
        trailingContent   = { Icon(Icons.Outlined.ChevronRight, contentDescription = "Edit") },
        modifier          = Modifier.clickable(onClick = onClick)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// App picker sheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppPickerSheet(
    apps: List<AppEntry>,
    onSelect: (AppEntry) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var query   by remember { mutableStateOf("") }
    val filtered = if (query.isBlank()) apps else apps.filter { it.label.contains(query, ignoreCase = true) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            Text(
                "Choose an app",
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier   = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )
            OutlinedTextField(
                value         = query,
                onValueChange = { query = it },
                placeholder   = { Text("Search") },
                leadingIcon   = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine    = true,
                modifier      = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(8.dp))

            if (filtered.isEmpty()) {
                Box(
                    modifier         = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No apps found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn {
                    items(filtered, key = { it.packageName }) { app ->
                        val icon by produceState<ImageBitmap?>(null, app.packageName) {
                            value = withContext(Dispatchers.IO) {
                                runCatching {
                                    context.packageManager.getApplicationIcon(app.packageName)
                                        .toBitmap(96, 96).asImageBitmap()
                                }.getOrNull()
                            }
                        }
                        ListItem(
                            headlineContent = { Text(app.label) },
                            leadingContent  = { AppIcon(icon) },
                            modifier        = Modifier.clickable { onSelect(app) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                    }
                    item { Spacer(Modifier.height(40.dp)) }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared composable helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AppIcon(icon: ImageBitmap?) {
    if (icon != null) {
        androidx.compose.foundation.Image(
            bitmap             = icon,
            contentDescription = null,
            modifier           = Modifier.size(40.dp)
        )
    } else {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Apps, contentDescription = null)
        }
    }
}

@Composable
private fun EmptyInboxState() {
    Box(
        modifier         = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "No deliveries yet. Batched notifications will appear here after your first scheduled delivery.",
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun EmptyRulesState() {
    Box(
        modifier         = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "No rules yet. Tap \"Add\" to mark an app as Instant or Batched.",
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun isListenerEnabled(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

private fun relativeTime(ms: Long): String {
    val diff    = System.currentTimeMillis() - ms
    val minutes = diff / 60_000
    val hours   = minutes / 60
    val days    = hours / 24
    return when {
        minutes < 1  -> "just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24   -> "${hours}h ago"
        else         -> "${days}d ago"
    }
}
