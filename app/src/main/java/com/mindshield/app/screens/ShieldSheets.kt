package com.mindshield.app.screens

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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mindshield.app.data.AppFrictionStore
import com.mindshield.app.data.GlobalBatchSettings
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// Shared formatting
// ─────────────────────────────────────────────────────────────────────────────

/** 12-hour clock text such as "9:00 AM". */
internal fun formatDeliveryTime(time: LocalTime): String =
    time.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))

private val SHIELD_DELIVERY_PRESETS = listOf(
    "Morning"   to LocalTime.of(8, 0),
    "Midday"    to LocalTime.of(12, 0),
    "Afternoon" to LocalTime.of(15, 0),
    "Evening"   to LocalTime.of(18, 0),
    "Night"     to LocalTime.of(21, 0)
)

// ─────────────────────────────────────────────────────────────────────────────
// Pause length sheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PauseDurationSheet(
    currentSeconds: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val close: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) onDismiss() }
    }

    // Slider position is local while dragging; it is committed on release.
    var sliderValue by remember(currentSeconds) {
        mutableFloatStateOf(
            currentSeconds.coerceIn(AppFrictionStore.MIN_DURATION, AppFrictionStore.MAX_DURATION).toFloat()
        )
    }
    val seconds = sliderValue.toInt()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Text(
                "Pause length",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "How long the pause screen holds you before an app opens. Applies to every app.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(Modifier.height(16.dp))

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppFrictionStore.PRESET_DURATIONS.forEach { secs ->
                    FilterChip(
                        selected = currentSeconds == secs,
                        onClick = { onSelect(secs) },
                        label = { Text("${secs}s") }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = "$seconds seconds",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = { onSelect(sliderValue.toInt()) },
                valueRange = AppFrictionStore.MIN_DURATION.toFloat()..AppFrictionStore.MAX_DURATION.toFloat(),
                steps = AppFrictionStore.MAX_DURATION - AppFrictionStore.MIN_DURATION - 1,
                modifier = Modifier.semantics { stateDescription = "$seconds seconds" }
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

            Spacer(Modifier.height(24.dp))
            Button(onClick = close, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Notification delivery sheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DeliveryScheduleSheet(
    settings: GlobalBatchSettings,
    onTimesChange: (List<LocalTime>) -> Unit,
    onBatchDuringSessionChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val close: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) onDismiss() }
    }

    val presetTimes = SHIELD_DELIVERY_PRESETS.map { it.second }
    // Times set earlier that are not one of the presets (e.g. the 9:00 / 13:00 defaults).
    val customTimes = settings.deliveryTimes.filter { it !in presetTimes }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Text(
                "Notification delivery",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Held notifications are delivered together at each time you select.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(Modifier.height(16.dp))

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SHIELD_DELIVERY_PRESETS.forEach { (label, time) ->
                    ShieldTimeChip("$label · ${formatDeliveryTime(time)}", time, settings, onTimesChange)
                }
                customTimes.forEach { time ->
                    ShieldTimeChip(formatDeliveryTime(time), time, settings, onTimesChange)
                }
            }

            if (settings.deliveryTimes.isEmpty()) {
                Text(
                    "No times selected, so held notifications only arrive when a session ends or you check manually.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .toggleable(
                        value = settings.batchDuringSession,
                        role = Role.Switch,
                        onValueChange = onBatchDuringSessionChange
                    )
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Batch during focus sessions", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "When on, every app except those set to Always instant is held during a session.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(16.dp))
                Switch(checked = settings.batchDuringSession, onCheckedChange = null)
            }

            Spacer(Modifier.height(16.dp))
            Button(onClick = close, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        }
    }
}

@Composable
private fun ShieldTimeChip(
    label: String,
    time: LocalTime,
    settings: GlobalBatchSettings,
    onTimesChange: (List<LocalTime>) -> Unit
) {
    val selected = time in settings.deliveryTimes
    FilterChip(
        selected = selected,
        onClick = {
            onTimesChange(if (selected) settings.deliveryTimes - time else settings.deliveryTimes + time)
        },
        leadingIcon = if (selected) {
            { Icon(Icons.Rounded.Done, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        } else null,
        label = { Text(label) }
    )
}
