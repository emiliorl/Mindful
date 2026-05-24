package com.mindshield.app.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mindshield.app.data.BatchCategory
import com.mindshield.app.data.BatchRule
import com.mindshield.app.data.ChannelRule

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchRuleSheet(
    rule: BatchRule,
    /** Channels discovered for this app: channelId → channelName. */
    knownChannels: Map<String, String>,
    onSave: (BatchRule) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    var appCategory by remember { mutableStateOf(rule.category) }
    // Local mutable copy of channel overrides so edits don't persist until Save is tapped
    var channelOverrides by remember { mutableStateOf(rule.channelOverrides.toMutableMap()) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp)
        ) {
            // ── App header ────────────────────────────────────────────────────
            Text(
                rule.appLabel,
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Default for all notifications from this app.",
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(Modifier.height(20.dp))

            // ── App-level category cards ──────────────────────────────────────
            CategoryCard(
                selected    = appCategory == BatchCategory.INSTANT,
                onClick     = { appCategory = BatchCategory.INSTANT },
                icon        = Icons.Outlined.NotificationsActive,
                title       = "Instant",
                description = "All notifications pass through immediately."
            )
            Spacer(Modifier.height(8.dp))
            CategoryCard(
                selected    = appCategory == BatchCategory.BATCHED,
                onClick     = { appCategory = BatchCategory.BATCHED },
                icon        = Icons.Outlined.Inbox,
                title       = "Batched",
                description = "All notifications held until schedule fires."
            )

            // ── Per-channel overrides ─────────────────────────────────────────
            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Tune,
                    contentDescription = null,
                    tint     = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Channel overrides",
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (knownChannels.isEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "No channels discovered yet. Channels appear here once a notification from each type arrives.",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Override the default for specific channel types.",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                knownChannels.forEach { (channelId, channelName) ->
                    val override = channelOverrides[channelId]
                    ChannelRow(
                        channelName  = channelName,
                        override     = override,
                        appDefault   = appCategory,
                        onSetInstant = {
                            channelOverrides = channelOverrides.toMutableMap().also {
                                it[channelId] = ChannelRule(channelId, channelName, BatchCategory.INSTANT)
                            }
                        },
                        onSetBatched = {
                            channelOverrides = channelOverrides.toMutableMap().also {
                                it[channelId] = ChannelRule(channelId, channelName, BatchCategory.BATCHED)
                            }
                        },
                        onClearOverride = {
                            channelOverrides = channelOverrides.toMutableMap().also { it.remove(channelId) }
                        }
                    )
                }
            }

            // ── Actions ───────────────────────────────────────────────────────
            Spacer(Modifier.height(24.dp))

            Button(
                onClick  = {
                    onSave(rule.copy(category = appCategory, channelOverrides = channelOverrides))
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save") }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick  = onRemove,
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Remove rule")
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Per-channel row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ChannelRow(
    channelName: String,
    override: ChannelRule?,
    appDefault: BatchCategory,
    onSetInstant: () -> Unit,
    onSetBatched: () -> Unit,
    onClearOverride: () -> Unit
) {
    Surface(
        color  = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape  = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(channelName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // "App default" chip — clears any override
                val defaultSelected = override == null
                FilterChip(
                    selected = defaultSelected,
                    onClick  = onClearOverride,
                    label    = {
                        Text(
                            "Default (${appDefault.displayLabel})",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )
                // Instant override
                FilterChip(
                    selected = override?.category == BatchCategory.INSTANT,
                    onClick  = onSetInstant,
                    leadingIcon = if (override?.category == BatchCategory.INSTANT) {
                        { Icon(Icons.Outlined.CheckCircle, null, modifier = Modifier.size(14.dp)) }
                    } else null,
                    label    = { Text("Instant", style = MaterialTheme.typography.labelSmall) }
                )
                // Batched override
                FilterChip(
                    selected = override?.category == BatchCategory.BATCHED,
                    onClick  = onSetBatched,
                    leadingIcon = if (override?.category == BatchCategory.BATCHED) {
                        { Icon(Icons.Outlined.CheckCircle, null, modifier = Modifier.size(14.dp)) }
                    } else null,
                    label    = { Text("Batch", style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// App-level category card (reused from before)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CategoryCard(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    title: String,
    description: String
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.outlineVariant
    val bgColor     = if (selected) MaterialTheme.colorScheme.primaryContainer
                      else MaterialTheme.colorScheme.surface

    Surface(
        color    = bgColor,
        shape    = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier          = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = if (selected) MaterialTheme.colorScheme.primary
                                     else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier           = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color      = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                 else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selected) {
                Spacer(Modifier.width(12.dp))
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint     = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
