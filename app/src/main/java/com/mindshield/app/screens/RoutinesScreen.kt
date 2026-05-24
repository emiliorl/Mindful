package com.mindshield.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mindshield.app.data.ChecklistItem
import com.mindshield.app.data.RoutinePhase
import com.mindshield.app.data.WindDownConfig
import com.mindshield.app.data.MorningConfig
import com.mindshield.app.viewmodel.RoutinesViewModel
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutinesScreen(vm: RoutinesViewModel = viewModel()) {
    val morning by vm.morning.collectAsStateWithLifecycle()
    val windDown by vm.windDown.collectAsStateWithLifecycle()
    val completedIds by vm.completedIds.collectAsStateWithLifecycle()
    val morningProgress by vm.morningProgress.collectAsStateWithLifecycle()
    val windDownProgress by vm.windDownProgress.collectAsStateWithLifecycle()
    val morningStreak by vm.morningStreak.collectAsStateWithLifecycle()
    val windDownStreak by vm.windDownStreak.collectAsStateWithLifecycle()
    val phase by vm.routinePhase.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Routines",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        // Current phase status
        if (phase != null) {
            item { PhaseStatusBanner(phase!!) }
        }

        // Timeline
        if (morning.enabled || windDown.enabled) {
            item { RoutineTimeline(morning, windDown, phase) }
        }

        // Morning routine
        item {
            RoutineSection(
                title = "Morning Routine 🌅",
                enabled = morning.enabled,
                onToggle = { vm.setMorningEnabled(it) },
                streak = morningStreak,
                content = {
                    MorningRoutineEditor(
                        config = morning,
                        completedIds = completedIds,
                        progress = morningProgress,
                        onWakeTimeChange = { vm.setMorningWakeTime(it) },
                        onPhoneTimeChange = { vm.setMorningPhoneAvailableTime(it) },
                        onItemToggle = { vm.toggleMorningItem(it) },
                        onAddItem = { vm.addMorningChecklistItem(it) },
                        onRemoveItem = { vm.removeMorningChecklistItem(it) }
                    )
                }
            )
        }

        // Wind-down routine
        item {
            RoutineSection(
                title = "Wind-Down Routine 🌙",
                enabled = windDown.enabled,
                onToggle = { vm.setWindDownEnabled(it) },
                streak = windDownStreak,
                content = {
                    WindDownRoutineEditor(
                        config = windDown,
                        completedIds = completedIds,
                        progress = windDownProgress,
                        onStartTimeChange = { vm.setWindDownStartTime(it) },
                        onSleepTimeChange = { vm.setWindDownSleepTime(it) },
                        onDelayChange = { vm.setWindDownExtendedDelay(it) },
                        onItemToggle = { vm.toggleWindDownItem(it) },
                        onAddItem = { vm.addWindDownChecklistItem(it) },
                        onRemoveItem = { vm.removeWindDownChecklistItem(it) }
                    )
                }
            )
        }
    }
}

@Composable
private fun PhaseStatusBanner(phase: RoutinePhase) {
    val (emoji, label, containerColor) = when (phase) {
        RoutinePhase.MORNING -> Triple("🌅", "Morning routine active", MaterialTheme.colorScheme.primaryContainer)
        RoutinePhase.WIND_DOWN -> Triple("🌙", "Wind-down active — friction extended", MaterialTheme.colorScheme.secondaryContainer)
        RoutinePhase.SLEEP -> Triple("💤", "Sleep mode — apps are blocked", MaterialTheme.colorScheme.tertiaryContainer)
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(emoji, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun RoutineTimeline(morning: MorningConfig, windDown: WindDownConfig, phase: RoutinePhase?) {
    val fmt = DateTimeFormatter.ofPattern("h:mm a")
    Column {
        Text(
            "Today's Timeline",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Simple bar representation — morning on left, wind-down on right
            if (morning.enabled) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                        .background(
                            if (phase == RoutinePhase.MORNING)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.primaryContainer
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${morning.wakeTime.format(fmt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (phase == RoutinePhase.MORNING)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            if (windDown.enabled) {
                val isActive = phase == RoutinePhase.WIND_DOWN || phase == RoutinePhase.SLEEP
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(
                            if (!morning.enabled)
                                RoundedCornerShape(8.dp)
                            else
                                RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
                        )
                        .background(
                            if (isActive)
                                MaterialTheme.colorScheme.secondary
                            else
                                MaterialTheme.colorScheme.secondaryContainer
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${windDown.startTime.format(fmt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isActive)
                            MaterialTheme.colorScheme.onSecondary
                        else
                            MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun RoutineSection(
    title: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    streak: Int,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (streak > 0) {
                        Text(
                            "🔥 $streak day streak",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }

            if (enabled) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                content()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MorningRoutineEditor(
    config: MorningConfig,
    completedIds: Set<String>,
    progress: Float,
    onWakeTimeChange: (LocalTime) -> Unit,
    onPhoneTimeChange: (LocalTime) -> Unit,
    onItemToggle: (ChecklistItem) -> Unit,
    onAddItem: (String) -> Unit,
    onRemoveItem: (ChecklistItem) -> Unit
) {
    var newItemText by remember { mutableStateOf("") }
    val fmt = DateTimeFormatter.ofPattern("h:mm a")

    // Time pickers
    TimePickerRow("Wake time", config.wakeTime, onWakeTimeChange)
    TimePickerRow("Phone available", config.phoneAvailableTime, onPhoneTimeChange)

    Spacer(Modifier.height(12.dp))
    Text("Checklist", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

    config.checklist.forEach { item ->
        ChecklistEditorRow(
            item = item,
            checked = item.id in completedIds,
            onToggle = { onItemToggle(item) },
            onRemove = { onRemoveItem(item) }
        )
    }

    AddItemRow(value = newItemText, onValueChange = { newItemText = it }, onAdd = {
        if (newItemText.isNotBlank()) {
            onAddItem(newItemText.trim())
            newItemText = ""
        }
    })
}

@Composable
private fun WindDownRoutineEditor(
    config: WindDownConfig,
    completedIds: Set<String>,
    progress: Float,
    onStartTimeChange: (LocalTime) -> Unit,
    onSleepTimeChange: (LocalTime) -> Unit,
    onDelayChange: (Int) -> Unit,
    onItemToggle: (ChecklistItem) -> Unit,
    onAddItem: (String) -> Unit,
    onRemoveItem: (ChecklistItem) -> Unit
) {
    var newItemText by remember { mutableStateOf("") }

    TimePickerRow("Wind-down starts", config.startTime, onStartTimeChange)
    TimePickerRow("Sleep time", config.sleepTime, onSleepTimeChange)

    Spacer(Modifier.height(12.dp))
    Text("Extended friction delay", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(4.dp))

    val delayOptions = listOf(10, 20, 30, 60)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        delayOptions.forEach { secs ->
            FilterChip(
                selected = config.extendedDelaySeconds == secs,
                onClick = { onDelayChange(secs) },
                label = { Text("${secs}s") }
            )
        }
    }

    Spacer(Modifier.height(12.dp))
    Text("Checklist", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

    config.checklist.forEach { item ->
        ChecklistEditorRow(
            item = item,
            checked = item.id in completedIds,
            onToggle = { onItemToggle(item) },
            onRemove = { onRemoveItem(item) }
        )
    }

    AddItemRow(value = newItemText, onValueChange = { newItemText = it }, onAdd = {
        if (newItemText.isNotBlank()) {
            onAddItem(newItemText.trim())
            newItemText = ""
        }
    })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerRow(label: String, time: LocalTime, onChange: (LocalTime) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    val fmt = DateTimeFormatter.ofPattern("h:mm a")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = { showPicker = true }) {
            Text(time.format(fmt), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
    }

    if (showPicker) {
        val state = rememberTimePickerState(
            initialHour = time.hour,
            initialMinute = time.minute,
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(label) },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    onChange(LocalTime.of(state.hour, state.minute))
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ChecklistEditorRow(
    item: ChecklistItem,
    checked: Boolean,
    onToggle: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Text(
            item.text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            color = if (checked) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.onSurface
        )
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Remove",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun AddItemRow(value: String, onValueChange: (String) -> Unit, onAdd: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text("Add item…") },
            modifier = Modifier.weight(1f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onAdd() })
        )
        IconButton(onClick = onAdd) {
            Icon(Icons.Default.Add, contentDescription = "Add")
        }
    }
}
