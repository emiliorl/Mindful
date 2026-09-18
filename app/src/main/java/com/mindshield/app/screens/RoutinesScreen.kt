package com.mindshield.app.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mindshield.app.data.ChecklistItem
import com.mindshield.app.data.MorningConfig
import com.mindshield.app.data.RoutinePhase
import com.mindshield.app.data.WindDownConfig
import com.mindshield.app.viewmodel.RoutinesViewModel
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// Per-routine accent colors
private val MorningColor  = Color(0xFFF59E0B)  // warm amber
private val WindDownColor = Color(0xFF6366F1)  // calm indigo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutinesScreen(vm: RoutinesViewModel = viewModel()) {
    val morning         by vm.morning.collectAsStateWithLifecycle()
    val windDown        by vm.windDown.collectAsStateWithLifecycle()
    val completedIds    by vm.completedIds.collectAsStateWithLifecycle()
    val morningProgress by vm.morningProgress.collectAsStateWithLifecycle()
    val windDownProgress by vm.windDownProgress.collectAsStateWithLifecycle()
    val morningStreak   by vm.morningStreak.collectAsStateWithLifecycle()
    val windDownStreak  by vm.windDownStreak.collectAsStateWithLifecycle()
    val phase           by vm.routinePhase.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Routines",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Build habits around your phone use.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (phase != null) {
            item { PhaseStatusBanner(phase!!) }
        }

        if (morning.enabled || windDown.enabled) {
            item { DayTimeline(morning, windDown, phase) }
        }

        item {
            RoutineCard(
                emoji = "🌅",
                title = "Morning Routine",
                description = "Lock phone until your morning tasks are done.",
                accentColor = MorningColor,
                enabled = morning.enabled,
                onToggle = { vm.setMorningEnabled(it) },
                streak = morningStreak,
                progress = morningProgress,
                checklistTotal = morning.checklist.size
            ) {
                MorningEditor(
                    config = morning,
                    completedIds = completedIds,
                    accentColor = MorningColor,
                    onWakeTimeChange = { vm.setMorningWakeTime(it) },
                    onPhoneTimeChange = { vm.setMorningPhoneAvailableTime(it) },
                    onItemToggle = { vm.toggleMorningItem(it) },
                    onAddItem = { vm.addMorningChecklistItem(it) },
                    onRemoveItem = { vm.removeMorningChecklistItem(it) }
                )
            }
        }

        item {
            RoutineCard(
                emoji = "🌙",
                title = "Wind-Down Routine",
                description = "Increase friction and wind down before sleep.",
                accentColor = WindDownColor,
                enabled = windDown.enabled,
                onToggle = { vm.setWindDownEnabled(it) },
                streak = windDownStreak,
                progress = windDownProgress,
                checklistTotal = windDown.checklist.size
            ) {
                WindDownEditor(
                    config = windDown,
                    completedIds = completedIds,
                    accentColor = WindDownColor,
                    onStartTimeChange = { vm.setWindDownStartTime(it) },
                    onSleepTimeChange = { vm.setWindDownSleepTime(it) },
                    onDelayChange = { vm.setWindDownExtendedDelay(it) },
                    onItemToggle = { vm.toggleWindDownItem(it) },
                    onAddItem = { vm.addWindDownChecklistItem(it) },
                    onRemoveItem = { vm.removeWindDownChecklistItem(it) }
                )
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Phase banner
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PhaseStatusBanner(phase: RoutinePhase) {
    val (emoji, title, sub, color) = when (phase) {
        RoutinePhase.MORNING   -> PhaseInfo("🌅", "Morning routine", "Phone unlocks when you finish.", MorningColor)
        RoutinePhase.WIND_DOWN -> PhaseInfo("🌙", "Wind-down active", "Friction extended on apps with friction turned on.", WindDownColor)
        RoutinePhase.SLEEP     -> PhaseInfo("💤", "Sleep mode", "Apps are blocked until morning.", Color(0xFF64748B))
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Text(emoji, fontSize = 20.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = color)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private data class PhaseInfo(val emoji: String, val title: String, val sub: String, val color: Color)

// ─────────────────────────────────────────────────────────────────────────────
// Day timeline
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DayTimeline(morning: MorningConfig, windDown: WindDownConfig, phase: RoutinePhase?) {
    val fmt = DateTimeFormatter.ofPattern("h:mm a")
    val morningActive = phase == RoutinePhase.MORNING
    val eveningActive = phase == RoutinePhase.WIND_DOWN || phase == RoutinePhase.SLEEP

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                "Today's schedule",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (morning.enabled) {
                    TimelineNode(
                        emoji = "🌅",
                        timeLabel = morning.wakeTime.format(fmt),
                        routineLabel = "Morning",
                        color = if (morningActive) MorningColor else MorningColor.copy(alpha = 0.45f),
                        active = morningActive
                    )
                }

                // Connecting line — fills remaining space
                if (morning.enabled && windDown.enabled) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(2.dp)
                            .background(
                                MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(1.dp)
                            )
                    )
                }

                if (windDown.enabled) {
                    TimelineNode(
                        emoji = "🌙",
                        timeLabel = windDown.startTime.format(fmt),
                        routineLabel = "Wind-down",
                        color = if (eveningActive) WindDownColor else WindDownColor.copy(alpha = 0.45f),
                        active = eveningActive
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineNode(
    emoji: String,
    timeLabel: String,
    routineLabel: String,
    color: Color,
    active: Boolean
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, fontSize = 16.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            timeLabel,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = if (active) color else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            routineLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Routine card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RoutineCard(
    emoji: String,
    title: String,
    description: String,
    accentColor: Color,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    streak: Int,
    progress: Float,
    checklistTotal: Int,
    content: @Composable ColumnScope.() -> Unit
) {
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "progress")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {

            // ── Header ──────────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Emoji icon
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(emoji, fontSize = 22.sp)
                }

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (streak > 0) {
                            Spacer(Modifier.width(8.dp))
                            StreakBadge(streak, accentColor)
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.width(8.dp))

                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.surface,
                        checkedTrackColor = accentColor
                    )
                )
            }

            // ── Progress bar ────────────────────────────────────────────────
            if (enabled && checklistTotal > 0) {
                Spacer(Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = accentColor,
                        trackColor = accentColor.copy(alpha = 0.14f)
                    )
                    Text(
                        "${(progress * checklistTotal).toInt()}/$checklistTotal item${if (checklistTotal == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Expanded content ────────────────────────────────────────────
            AnimatedVisibility(
                visible = enabled,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    Spacer(Modifier.height(20.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.height(16.dp))
                    content()
                }
            }
        }
    }
}

@Composable
private fun StreakBadge(streak: Int, accentColor: Color) {
    Surface(
        shape = CircleShape,
        color = accentColor.copy(alpha = 0.14f)
    ) {
        Text(
            text = "🔥 $streak",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = accentColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Morning editor
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MorningEditor(
    config: MorningConfig,
    completedIds: Set<String>,
    accentColor: Color,
    onWakeTimeChange: (LocalTime) -> Unit,
    onPhoneTimeChange: (LocalTime) -> Unit,
    onItemToggle: (ChecklistItem) -> Unit,
    onAddItem: (String) -> Unit,
    onRemoveItem: (ChecklistItem) -> Unit
) {
    var newItemText by remember { mutableStateOf("") }

    EditorSection("Schedule") {
        TimePickerRow("Wake time", config.wakeTime, accentColor, onWakeTimeChange)
        Spacer(Modifier.height(4.dp))
        TimePickerRow("Phone unlocks", config.phoneAvailableTime, accentColor, onPhoneTimeChange)
    }

    if (config.checklist.isNotEmpty() || true) {
        Spacer(Modifier.height(16.dp))
        EditorSection("Checklist") {
            config.checklist.forEach { item ->
                ChecklistRow(
                    item = item,
                    checked = item.id in completedIds,
                    accentColor = accentColor,
                    onToggle = { onItemToggle(item) },
                    onRemove = { onRemoveItem(item) }
                )
            }
            AddItemRow(
                value = newItemText,
                onValueChange = { newItemText = it },
                onAdd = {
                    if (newItemText.isNotBlank()) { onAddItem(newItemText.trim()); newItemText = "" }
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Wind-down editor
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WindDownEditor(
    config: WindDownConfig,
    completedIds: Set<String>,
    accentColor: Color,
    onStartTimeChange: (LocalTime) -> Unit,
    onSleepTimeChange: (LocalTime) -> Unit,
    onDelayChange: (Int) -> Unit,
    onItemToggle: (ChecklistItem) -> Unit,
    onAddItem: (String) -> Unit,
    onRemoveItem: (ChecklistItem) -> Unit
) {
    var newItemText by remember { mutableStateOf("") }

    EditorSection("Schedule") {
        TimePickerRow("Wind-down starts", config.startTime, accentColor, onStartTimeChange)
        Spacer(Modifier.height(4.dp))
        TimePickerRow("Sleep time", config.sleepTime, accentColor, onSleepTimeChange)
        Spacer(Modifier.height(8.dp))
        Text(
            "Sessions end automatically at sleep time.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Spacer(Modifier.height(16.dp))
    EditorSection("Friction delay") {
        Text(
            "Extra wait before an app opens during wind-down. Only applies to apps with friction already turned on in the Apps tab — not a universal gate like Detox Day.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(10, 20, 30, 60).forEach { secs ->
                val selected = config.extendedDelaySeconds == secs
                Surface(
                    onClick = { onDelayChange(secs) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) accentColor.copy(alpha = 0.14f)
                            else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
                        Text(
                            "${secs}s",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) accentColor
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(16.dp))
    EditorSection("Checklist") {
        config.checklist.forEach { item ->
            ChecklistRow(
                item = item,
                checked = item.id in completedIds,
                accentColor = accentColor,
                onToggle = { onItemToggle(item) },
                onRemove = { onRemoveItem(item) }
            )
        }
        AddItemRow(
            value = newItemText,
            onValueChange = { newItemText = it },
            onAdd = {
                if (newItemText.isNotBlank()) { onAddItem(newItemText.trim()); newItemText = "" }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared sub-components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EditorSection(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.8.sp
        )
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerRow(
    label: String,
    time: LocalTime,
    accentColor: Color,
    onChange: (LocalTime) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    val fmt = DateTimeFormatter.ofPattern("h:mm a")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        // Chip-style time button
        Surface(
            onClick = { showPicker = true },
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Outlined.AccessTime,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    time.format(fmt),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
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
private fun ChecklistRow(
    item: ChecklistItem,
    checked: Boolean,
    accentColor: Color,
    onToggle: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onToggle,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = if (checked) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                contentDescription = null,
                tint = if (checked) accentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = item.text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            color = if (checked) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                    else MaterialTheme.colorScheme.onSurface,
            textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None
        )
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

@Composable
private fun AddItemRow(value: String, onValueChange: (String) -> Unit, onAdd: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onAdd, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Add",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onAdd() }),
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            "Add a task…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                    inner()
                }
            }
        )
    }
}
