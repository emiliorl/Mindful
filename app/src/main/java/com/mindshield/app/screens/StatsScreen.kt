package com.mindshield.app.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mindshield.app.data.IntentType
import com.mindshield.app.viewmodel.*

// ─────────────────────────────────────────────────────────────────────────────
// Intent → color
// ─────────────────────────────────────────────────────────────────────────────

private fun IntentType.color(): Color = when (this) {
    IntentType.SOCIAL_MEDIA   -> Color(0xFFE57373)
    IntentType.WORK           -> Color(0xFF64B5F6)
    IntentType.STUDY          -> Color(0xFF81C784)
    IntentType.FITNESS        -> Color(0xFFFFB74D)
    IntentType.ENTERTAINMENT  -> Color(0xFFBA68C8)
    IntentType.JUST_LOOKING   -> Color(0xFF90A4AE)
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun StatsScreen(vm: StatsViewModel = viewModel()) {
    val summary by vm.summary.collectAsStateWithLifecycle()
    val selected by vm.selectedRange.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { ScreenTitle() }

        item {
            RangeSelector(
                selected  = selected,
                onSelect  = { vm.selectedRange.value = it }
            )
        }

        item { BarChartCard(summary) }

        item { IntentBreakdownCard(summary) }

        item { FrictionCard(summary.frictionSummary) }

        item { NotificationsCard(summary.notificationsBatched) }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Title
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ScreenTitle() {
    Text(
        text       = "Your Time",
        style      = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Range selector
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RangeSelector(selected: RangeOption, onSelect: (RangeOption) -> Unit) {
    Row(
        modifier            = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RangeOption.entries.forEach { option ->
            val isSelected = option == selected
            FilterChip(
                selected = isSelected,
                onClick  = { onSelect(option) },
                label    = { Text(option.label) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Bar chart card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BarChartCard(summary: StatsSummary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text       = "Daily usage",
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(16.dp))

            if (summary.dailyBars.isEmpty() || summary.dailyBars.all { it.byIntent.isEmpty() }) {
                Box(
                    modifier        = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No sessions recorded yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                StackedBarChart(bars = summary.dailyBars, modifier = Modifier.fillMaxWidth().height(140.dp))
                Spacer(Modifier.height(8.dp))
                IntentLegend()
            }
        }
    }
}

@Composable
private fun StackedBarChart(bars: List<DayBar>, modifier: Modifier = Modifier) {
    val maxMs = bars.maxOfOrNull { bar -> bar.byIntent.values.sum() }?.takeIf { it > 0 } ?: 1L

    Row(
        modifier              = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment     = Alignment.Bottom
    ) {
        bars.forEach { bar ->
            val total = bar.byIntent.values.sum()
            val fraction = total.toFloat() / maxMs

            Column(
                modifier              = Modifier.weight(1f),
                horizontalAlignment   = Alignment.CenterHorizontally,
                verticalArrangement   = Arrangement.Bottom
            ) {
                // Stacked bar
                if (total > 0) {
                    val segments = bar.byIntent.entries
                        .sortedBy { it.key.ordinal }
                        .filter { it.value > 0 }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(fraction)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)),
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        segments.forEachIndexed { idx, (type, ms) ->
                            val segFraction = ms.toFloat() / total
                            val isTop = idx == segments.lastIndex
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(segFraction.coerceAtLeast(0.01f))
                                    .background(
                                        color = type.color(),
                                        shape = if (isTop) RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp) else RoundedCornerShape(0.dp)
                                    )
                            )
                        }
                    }
                } else {
                    // Empty bar placeholder
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    text      = bar.label,
                    fontSize  = 10.sp,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines  = 1
                )
            }
        }
    }
}

@Composable
private fun IntentLegend() {
    val activeTypes = IntentType.entries.filter { it != IntentType.JUST_LOOKING }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        activeTypes.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { type ->
                    Row(
                        verticalAlignment   = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(type.color())
                        )
                        Text(type.label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Intent breakdown card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun IntentBreakdownCard(summary: StatsSummary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text       = "By intent",
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            if (summary.intentBreakdown.isEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Start a session to see your breakdown.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Spacer(Modifier.height(12.dp))
                summary.intentBreakdown.forEach { item ->
                    IntentRow(item)
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun IntentRow(item: IntentTotal) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(item.type.color())
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text     = "${item.type.emoji}  ${item.type.label}",
                style    = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text  = item.totalMs.formatDuration(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress       = { item.fraction },
            modifier       = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            color          = item.type.color(),
            trackColor     = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Friction card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FrictionCard(friction: FrictionSummary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text       = "Friction moments",
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))

            if (friction.shown == 0) {
                Text(
                    "No friction moments recorded yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    FrictionStat(label = "Shown",       value = friction.shown.toString(),        color = MaterialTheme.colorScheme.onSurface)
                    FrictionStat(label = "Opened anyway", value = friction.openedAnyway.toString(), color = Color(0xFFE57373))
                    FrictionStat(label = "Went back",   value = friction.wentBack.toString(),     color = Color(0xFF81C784))
                }

                if (friction.shown > 0) {
                    val resistPct = (friction.wentBack * 100) / friction.shown
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text  = "You resisted $resistPct% of friction moments",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun FrictionStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text       = value,
            fontSize   = 28.sp,
            fontWeight = FontWeight.Bold,
            color      = color
        )
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Notifications card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NotificationsCard(count: Int) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier          = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = "Notifications batched",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text  = "Delivered on your terms, not theirs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text       = count.toString(),
                fontSize   = 32.sp,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.primary
            )
        }
    }
}
