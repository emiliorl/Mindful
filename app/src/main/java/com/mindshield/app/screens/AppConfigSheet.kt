package com.mindshield.app.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mindshield.app.data.BatchCategory
import com.mindshield.app.data.ChannelRule
import com.mindshield.app.data.DeliveryMode
import com.mindshield.app.data.FrictionMode
import com.mindshield.app.data.IntentType
import com.mindshield.app.ui.components.AppIcon
import com.mindshield.app.ui.theme.icon
import com.mindshield.app.viewmodel.NotifyMode
import com.mindshield.app.viewmodel.ShieldViewModel
import com.mindshield.app.viewmodel.statusText
import kotlinx.coroutines.launch

private val SHIELD_INTERVAL_PRESETS = listOf(1, 2, 4)
private val SHIELD_COUNT_PRESETS = listOf(5, 10, 20)

/**
 * Per-app configuration. Every control writes through to [vm] immediately; the only
 * button is "Done". Sections whose permission is missing are shown disabled.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppConfigSheet(
    packageName: String,
    vm: ShieldViewModel,
    accessibilityEnabled: Boolean,
    notificationAccessEnabled: Boolean,
    onOpenSettings: () -> Unit,
    onChangePauseLength: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val close: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) onDismiss() }
    }

    val appFlow = remember(packageName) { vm.app(packageName) }
    val appState by appFlow.collectAsStateWithLifecycle()
    val pauseDuration by vm.pauseDuration.collectAsStateWithLifecycle()
    val globalSettings by vm.globalSettings.collectAsStateWithLifecycle()
    val knownChannels by vm.knownChannels.collectAsStateWithLifecycle()

    val app = appState ?: return
    val rule = app.rule
    val status = statusText(app.friction, rule, pauseDuration)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            // ── Header ────────────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(packageName = app.packageName, size = 48.dp)
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        app.label,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        status.ifEmpty { "Not protected yet" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Before opening ───────────────────────────────────────────────
            Spacer(Modifier.height(24.dp))
            ShieldSheetSectionTitle("Before opening")
            if (!accessibilityEnabled) {
                ShieldPermissionNotice(
                    text = "Pause screen needs Accessibility access.",
                    onEnable = onOpenSettings
                )
            }
            Column(modifier = Modifier.alpha(if (accessibilityEnabled) 1f else 0.5f)) {
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    FrictionMode.entries.forEachIndexed { index, mode ->
                        val (label, vector) = when (mode) {
                            FrictionMode.OFF    -> "Off" to Icons.Rounded.RadioButtonUnchecked
                            FrictionMode.ALWAYS -> "Always" to Icons.Rounded.Lock
                            FrictionMode.SMART  -> "Smart" to Icons.Rounded.AutoAwesome
                        }
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index, FrictionMode.entries.size),
                            selected = app.friction.mode == mode,
                            onClick = { vm.setFrictionMode(app.packageName, mode) },
                            enabled = accessibilityEnabled,
                            modifier = Modifier.heightIn(min = 48.dp),
                            icon = {
                                Icon(
                                    vector,
                                    contentDescription = null,
                                    modifier = Modifier.size(SegmentedButtonDefaults.IconSize)
                                )
                            },
                            label = { Text(label, style = MaterialTheme.typography.labelMedium) }
                        )
                    }
                }

                AnimatedVisibility(
                    visible = app.friction.mode == FrictionMode.SMART,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Smart mode only applies friction while a matching session is active — it doesn't use time of day.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Pause during these sessions",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IntentType.entries
                                .filter { it != IntentType.JUST_LOOKING }
                                .forEach { type ->
                                    val selected = type in app.friction.smartIntents
                                    FilterChip(
                                        selected = selected,
                                        enabled = accessibilityEnabled,
                                        onClick = { vm.setSmartIntent(app.packageName, type, !selected) },
                                        leadingIcon = {
                                            Icon(
                                                type.icon,
                                                contentDescription = null,
                                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                                            )
                                        },
                                        label = { Text(type.label) }
                                    )
                                }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Pause length · ${pauseDuration}s",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onChangePauseLength) { Text("Change") }
                }
            }

            // ── Notifications ────────────────────────────────────────────────
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            ShieldSheetSectionTitle("Notifications")
            if (!notificationAccessEnabled) {
                ShieldPermissionNotice(
                    text = "Held notifications need Notification access.",
                    onEnable = onOpenSettings
                )
            }
            Column(modifier = Modifier.alpha(if (notificationAccessEnabled) 1f else 0.5f)) {
                Spacer(Modifier.height(4.dp))
                Column(modifier = Modifier.selectableGroup()) {
                    ShieldNotifyOption(
                        selected = app.notifyMode == NotifyMode.DEFAULT,
                        enabled = notificationAccessEnabled,
                        title = "Follow default",
                        description = if (globalSettings.batchDuringSession) "Held only during focus sessions"
                                      else "Delivered instantly",
                        onClick = { vm.setNotifyMode(app.packageName, app.label, NotifyMode.DEFAULT) }
                    )
                    ShieldNotifyOption(
                        selected = app.notifyMode == NotifyMode.INSTANT,
                        enabled = notificationAccessEnabled,
                        title = "Always instant",
                        description = "Never held, even during focus sessions",
                        onClick = { vm.setNotifyMode(app.packageName, app.label, NotifyMode.INSTANT) }
                    )
                    ShieldNotifyOption(
                        selected = app.notifyMode == NotifyMode.BATCHED,
                        enabled = notificationAccessEnabled,
                        title = "Always batched",
                        description = "Held until your next delivery",
                        onClick = { vm.setNotifyMode(app.packageName, app.label, NotifyMode.BATCHED) }
                    )
                }

                if (rule != null && rule.category == BatchCategory.BATCHED) {
                    Spacer(Modifier.height(16.dp))
                    ShieldDeliveryModePicker(
                        mode = rule.deliveryMode,
                        intervalHours = rule.intervalHours,
                        countThreshold = rule.countThreshold,
                        enabled = notificationAccessEnabled,
                        onModeSelected = { vm.setDeliveryMode(app.packageName, it) },
                        onIntervalSelected = { vm.setInterval(app.packageName, it) },
                        onCountSelected = { vm.setCountThreshold(app.packageName, it) }
                    )
                }

                if (rule != null) {
                    val channels = knownChannels[app.packageName].orEmpty()
                    Spacer(Modifier.height(20.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Channel overrides",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (channels.isEmpty()) {
                        Text(
                            "No channels discovered yet. Channels appear here once a notification from each type arrives.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    } else {
                        Text(
                            "Override the default for specific channel types.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                        )
                        channels.forEach { (channelId, channelName) ->
                            ShieldChannelRow(
                                channelName = channelName,
                                override = rule.channelOverrides[channelId],
                                appDefault = rule.category,
                                enabled = notificationAccessEnabled,
                                onSet = { category ->
                                    vm.setChannelOverride(
                                        app.packageName,
                                        ChannelRule(channelId, channelName, category)
                                    )
                                },
                                onClear = { vm.clearChannelOverride(app.packageName, channelId) }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Button(onClick = close, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Pieces
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ShieldSheetSectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun ShieldPermissionNotice(text: String, onEnable: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onEnable) { Text("Enable") }
        }
    }
}

@Composable
private fun ShieldNotifyOption(
    selected: Boolean,
    enabled: Boolean,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ShieldDeliveryModePicker(
    mode: DeliveryMode,
    intervalHours: Int,
    countThreshold: Int,
    enabled: Boolean,
    onModeSelected: (DeliveryMode) -> Unit,
    onIntervalSelected: (Int) -> Unit,
    onCountSelected: (Int) -> Unit
) {
    Column {
        Text(
            "Delivery",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            DeliveryMode.entries.forEachIndexed { index, m ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index, DeliveryMode.entries.size),
                    selected = mode == m,
                    onClick = { onModeSelected(m) },
                    enabled = enabled,
                    modifier = Modifier.heightIn(min = 48.dp),
                    label = { Text(m.displayLabel, style = MaterialTheme.typography.labelMedium) }
                )
            }
        }
        when (mode) {
            DeliveryMode.INTERVAL -> {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SHIELD_INTERVAL_PRESETS.forEach { hours ->
                        FilterChip(
                            selected = intervalHours == hours,
                            enabled = enabled,
                            onClick = { onIntervalSelected(hours) },
                            label = { Text("${hours}h") }
                        )
                    }
                }
            }
            DeliveryMode.COUNT -> {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SHIELD_COUNT_PRESETS.forEach { count ->
                        FilterChip(
                            selected = countThreshold == count,
                            enabled = enabled,
                            onClick = { onCountSelected(count) },
                            label = { Text("$count") }
                        )
                    }
                }
            }
            DeliveryMode.DAYPART -> Unit
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShieldChannelRow(
    channelName: String,
    override: ChannelRule?,
    appDefault: BatchCategory,
    enabled: Boolean,
    onSet: (BatchCategory) -> Unit,
    onClear: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(channelName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShieldChannelChip("Default (${appDefault.displayLabel})", override == null, enabled, onClear)
                ShieldChannelChip(
                    "Instant", override?.category == BatchCategory.INSTANT, enabled
                ) { onSet(BatchCategory.INSTANT) }
                ShieldChannelChip(
                    "Batch", override?.category == BatchCategory.BATCHED, enabled
                ) { onSet(BatchCategory.BATCHED) }
            }
        }
    }
}

@Composable
private fun ShieldChannelChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        enabled = enabled,
        onClick = onClick,
        leadingIcon = if (selected) {
            { Icon(Icons.Rounded.Done, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        } else null,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) }
    )
}
