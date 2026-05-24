package com.mindshield.app.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mindshield.app.data.IntentType
import com.mindshield.app.viewmodel.AppEntry
import com.mindshield.app.viewmodel.AppsViewModel
import com.mindshield.app.viewmodel.FrictionMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppsScreen() {
    val vm: AppsViewModel = viewModel()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val query by vm.searchQuery.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = "Apps",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Choose when each app gets a pause before opening.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = vm::onSearchQueryChanged,
                placeholder = { Text("Search apps") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        HorizontalDivider()

        if (apps.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn {
                items(apps, key = { it.packageName }) { app ->
                    AppRow(
                        app = app,
                        onModeSelected = { vm.setMode(app.packageName, it) },
                        onToggleIntent = { type, enabled ->
                            vm.setIntentRule(app.packageName, type, enabled)
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// App row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AppRow(
    app: AppEntry,
    onModeSelected: (FrictionMode) -> Unit,
    onToggleIntent: (IntentType, Boolean) -> Unit
) {
    val context = LocalContext.current
    val icon: ImageBitmap? by produceState<ImageBitmap?>(null, app.packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager
                    .getApplicationIcon(app.packageName)
                    .toBitmap(96, 96)
                    .asImageBitmap()
            }.getOrNull()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // ── App identity row ──────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                if (icon != null) {
                    Image(
                        bitmap = icon!!,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp)
                    )
                } else {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {}
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(10.dp))

        // ── Mode selector ─────────────────────────────────────────────────────
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            FrictionMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = FrictionMode.entries.size
                    ),
                    selected = app.frictionMode == mode,
                    onClick  = { onModeSelected(mode) },
                    label = {
                        Text(
                            text = when (mode) {
                                FrictionMode.OFF    -> "Off"
                                FrictionMode.ALWAYS -> "Always"
                                FrictionMode.SMART  -> "Smart"
                            },
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    icon = {
                        Text(
                            text = when (mode) {
                                FrictionMode.OFF    -> "○"
                                FrictionMode.ALWAYS -> "🔒"
                                FrictionMode.SMART  -> "🎯"
                            }
                        )
                    }
                )
            }
        }

        // ── Session chips — only visible in Smart mode ─────────────────────────
        AnimatedVisibility(
            visible = app.frictionMode == FrictionMode.SMART,
            enter = expandVertically(),
            exit  = shrinkVertically()
        ) {
            Column {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Pause during these sessions:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )
                Spacer(Modifier.height(6.dp))
                SessionChipRow(
                    selectedIntents = app.frictionIntents,
                    onToggle = onToggleIntent
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Session chips
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionChipRow(
    selectedIntents: Set<IntentType>,
    onToggle: (IntentType, Boolean) -> Unit
) {
    val types = IntentType.entries.filter { it != IntentType.JUST_LOOKING }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        types.forEach { type ->
            val selected = type in selectedIntents
            FilterChip(
                selected = selected,
                onClick  = { onToggle(type, !selected) },
                label    = { Text("${type.emoji} ${type.label}") }
            )
        }
    }
}
