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
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.eventFlow
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mindshield.app.data.AppFrictionStore
import com.mindshield.app.data.FrictionMode
import com.mindshield.app.data.IntentType
import com.mindshield.app.util.AccessibilityServiceStatus
import com.mindshield.app.viewmodel.AppEntry
import com.mindshield.app.viewmodel.AppsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.withContext

// ─────────────────────────────────────────────────────────────────────────────
// Root
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AppsScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Re-check service status whenever the screen resumes (e.g. user returns
    // from Android Accessibility settings after enabling the service).
    var serviceEnabled by remember { mutableStateOf(AccessibilityServiceStatus.isEnabled(context)) }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.eventFlow
            .filterIsInstance<Lifecycle.Event>()
            .collect { event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    serviceEnabled = AccessibilityServiceStatus.isEnabled(context)
                }
            }
    }

    if (!serviceEnabled) {
        AccessibilityPermissionGate(
            onOpenSettings = {
                context.startActivity(
                    android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                )
            }
        )
    } else {
        AppListContent()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Permission gate — shown when accessibility service is not enabled
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AccessibilityPermissionGate(onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Accessibility,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Accessibility service required",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "MindShield needs the Accessibility service to detect when you open an app and show the friction overlay. Your data never leaves your device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "In Accessibility settings, find MindShield and toggle it on.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.OpenInNew, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Open Accessibility settings")
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Main content — visible once the service is enabled
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AppListContent() {
    val vm: AppsViewModel = viewModel()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val query by vm.searchQuery.collectAsStateWithLifecycle()
    val pauseDuration by vm.pauseDuration.collectAsStateWithLifecycle()

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
        }

        HorizontalDivider()

        // ── Duration selector ─────────────────────────────────────────────────
        DurationSelector(
            currentSeconds = pauseDuration,
            onSelect = vm::setPauseDuration
        )

        HorizontalDivider()

        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
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
                        onToggleIntent  = { type, enabled ->
                            vm.setSmartIntent(app.packageName, type, enabled)
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Duration selector
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DurationSelector(currentSeconds: Int, onSelect: (Int) -> Unit) {
    var showCustomDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(
            text = "Pause duration",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            AppFrictionStore.PRESET_DURATIONS.forEach { secs ->
                FilterChip(
                    selected = currentSeconds == secs && secs in AppFrictionStore.PRESET_DURATIONS,
                    onClick  = { onSelect(secs) },
                    label    = { Text("${secs}s") }
                )
            }
            // Custom chip
            val isCustom = currentSeconds !in AppFrictionStore.PRESET_DURATIONS
            FilterChip(
                selected = isCustom,
                onClick  = { showCustomDialog = true },
                label    = { Text(if (isCustom) "${currentSeconds}s ✎" else "Custom") }
            )
        }
    }

    if (showCustomDialog) {
        CustomDurationDialog(
            current = currentSeconds,
            onDismiss = { showCustomDialog = false },
            onConfirm = { secs ->
                onSelect(secs)
                showCustomDialog = false
            }
        )
    }
}

@Composable
private fun CustomDurationDialog(
    current: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var sliderValue by remember {
        mutableFloatStateOf(
            current.coerceIn(AppFrictionStore.MIN_DURATION, AppFrictionStore.MAX_DURATION).toFloat()
        )
    }
    val seconds = sliderValue.toInt()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom pause duration") },
        text = {
            Column {
                Text(
                    text = "$seconds seconds",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    valueRange = AppFrictionStore.MIN_DURATION.toFloat()..AppFrictionStore.MAX_DURATION.toFloat(),
                    steps = AppFrictionStore.MAX_DURATION - AppFrictionStore.MIN_DURATION - 1
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "${AppFrictionStore.MIN_DURATION}s",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${AppFrictionStore.MAX_DURATION}s",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(seconds) }) { Text("Set") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                if (icon != null) {
                    Image(bitmap = icon!!, contentDescription = null, modifier = Modifier.size(40.dp))
                } else {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {}
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(app.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(10.dp))

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            FrictionMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index, FrictionMode.entries.size),
                    selected = app.frictionMode == mode,
                    onClick  = { onModeSelected(mode) },
                    label = {
                        Text(
                            when (mode) {
                                FrictionMode.OFF    -> "Off"
                                FrictionMode.ALWAYS -> "Always"
                                FrictionMode.SMART  -> "Smart"
                            },
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    icon = {
                        Text(when (mode) {
                            FrictionMode.OFF    -> "○"
                            FrictionMode.ALWAYS -> "🔒"
                            FrictionMode.SMART  -> "🎯"
                        })
                    }
                )
            }
        }

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
                SessionChipRow(app.smartIntents, onToggleIntent)
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
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IntentType.entries
            .filter { it != IntentType.JUST_LOOKING }
            .forEach { type ->
                val selected = type in selectedIntents
                FilterChip(
                    selected = selected,
                    onClick  = { onToggle(type, !selected) },
                    label    = { Text("${type.emoji} ${type.label}") }
                )
            }
    }
}
