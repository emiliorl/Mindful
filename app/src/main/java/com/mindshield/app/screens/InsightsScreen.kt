package com.mindshield.app.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mindshield.app.data.HeldNotification
import com.mindshield.app.data.IntentType
import com.mindshield.app.ui.components.AppIcon
import com.mindshield.app.ui.components.SectionHeader
import com.mindshield.app.ui.theme.accent
import com.mindshield.app.ui.theme.icon
import com.mindshield.app.viewmodel.DayBar
import com.mindshield.app.viewmodel.DeliveredBatch
import com.mindshield.app.viewmodel.FrictionSummary
import com.mindshield.app.viewmodel.IntentTotal
import com.mindshield.app.viewmodel.NotificationsViewModel
import com.mindshield.app.viewmodel.RangeOption
import com.mindshield.app.viewmodel.StatsViewModel
import com.mindshield.app.viewmodel.formatDuration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val INITIAL_BATCH_LIMIT = 30

private val GUTTER = 16.dp

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun InsightsScreen() {
    val statsVm: StatsViewModel = viewModel()
    val notifVm: NotificationsViewModel = viewModel()

    val summary by statsVm.summary.collectAsStateWithLifecycle()
    val range by statsVm.selectedRange.collectAsStateWithLifecycle()
    val batches by notifVm.batches.collectAsStateWithLifecycle()
    val queuedCount by notifVm.queuedCount.collectAsStateWithLifecycle()

    // Batches whose expansion differs from the default (newest batch expanded, rest collapsed).
    val toggled = rememberSaveable(
        saver = listSaver<MutableList<Long>, Long>(
            save = { it.toList() },
            restore = { it.toMutableStateList() }
        )
    ) { mutableStateListOf<Long>() }
    var showAllBatches by rememberSaveable { mutableStateOf(false) }

    val newestTs = batches.firstOrNull()?.deliveredAtMs
    val visibleBatches = if (showAllBatches) batches else batches.take(INITIAL_BATCH_LIMIT)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
    ) {
        item(key = "range") {
            RangeSelector(
                selected = range,
                onSelect = { statsVm.selectedRange.value = it },
                modifier = Modifier.padding(horizontal = GUTTER)
            )
        }

        item(key = "summary") {
            SummaryRow(
                focusMs = summary.intentBreakdown.sumOf { it.totalMs },
                pausesResisted = summary.frictionSummary.wentBack,
                notificationsHeld = summary.notificationsBatched,
                modifier = Modifier.padding(start = GUTTER, end = GUTTER, top = 16.dp)
            )
        }

        item(key = "chart_header") { SectionHeader("Daily focus", Modifier.padding(top = 12.dp)) }
        item(key = "chart") {
            DailyChart(
                bars = summary.dailyBars,
                range = summary.range,
                modifier = Modifier.padding(horizontal = GUTTER)
            )
        }

        item(key = "intent_header") { SectionHeader("By intent", Modifier.padding(top = 12.dp)) }
        if (summary.intentBreakdown.isEmpty()) {
            item(key = "intent_empty") {
                MutedText("Start a session to see your breakdown.", Modifier.padding(horizontal = GUTTER))
            }
        } else {
            items(summary.intentBreakdown, key = { "intent_${it.type.name}" }) { item ->
                IntentRow(item, Modifier.padding(horizontal = GUTTER, vertical = 6.dp))
            }
        }

        item(key = "friction_header") { SectionHeader("Friction moments", Modifier.padding(top = 12.dp)) }
        item(key = "friction") {
            FrictionSection(summary.frictionSummary, Modifier.padding(horizontal = GUTTER))
        }

        item(key = "digest_header") { SectionHeader("Notification digests", Modifier.padding(top = 12.dp)) }

        if (queuedCount > 0) {
            item(key = "queued") {
                QueuedLine(
                    count = queuedCount,
                    onDeliverNow = notifVm::deliverNow,
                    modifier = Modifier.padding(horizontal = GUTTER, vertical = 4.dp)
                )
            }
        }

        if (batches.isEmpty()) {
            item(key = "digest_empty") {
                MutedText(
                    "No digests yet. Batched notifications show up here after your first delivery.",
                    Modifier.padding(horizontal = GUTTER, vertical = 8.dp)
                )
            }
        } else {
            visibleBatches.forEach { batch ->
                val ts = batch.deliveredAtMs
                val expanded = (ts == newestTs) != (ts in toggled)
                item(key = "batch_$ts") {
                    BatchHeader(
                        batch = batch,
                        expanded = expanded,
                        onToggle = { if (ts in toggled) toggled.remove(ts) else toggled.add(ts) }
                    )
                }
                if (expanded) {
                    items(batch.notifications, key = { "n_${it.id}" }) { n ->
                        HeldNotificationRow(n)
                    }
                }
                item(key = "div_$ts") {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = GUTTER),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
            if (!showAllBatches && batches.size > INITIAL_BATCH_LIMIT) {
                item(key = "show_older") {
                    TextButton(
                        onClick = { showAllBatches = true },
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Text("Show older (${batches.size - INITIAL_BATCH_LIMIT})")
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Range selector
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RangeSelector(
    selected: RangeOption,
    onSelect: (RangeOption) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RangeOption.entries.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(option.label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Summary row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SummaryRow(
    focusMs: Long,
    pausesResisted: Int,
    notificationsHeld: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SummaryStat(
            icon = Icons.Rounded.Timer,
            value = if (focusMs <= 0L) "0m" else focusMs.formatDuration(),
            label = "Focus time",
            modifier = Modifier.weight(1f)
        )
        SummaryStat(
            icon = Icons.Rounded.PauseCircle,
            value = pausesResisted.toString(),
            label = "Pauses resisted",
            modifier = Modifier.weight(1f)
        )
        SummaryStat(
            icon = Icons.Rounded.Inbox,
            value = notificationsHeld.toString(),
            label = "Notifications held",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SummaryStat(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = "$label: $value" },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Daily chart
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DailyChart(bars: List<DayBar>, range: RangeOption, modifier: Modifier = Modifier) {
    if (bars.isEmpty() || bars.all { it.byIntent.isEmpty() }) {
        MutedText("No sessions recorded yet.", modifier.padding(vertical = 8.dp))
        return
    }

    var selectedIndex by remember(range) { mutableStateOf<Int?>(null) }
    val selectedBar = selectedIndex?.let { bars.getOrNull(it) }

    Column(modifier = modifier.fillMaxWidth()) {
        SelectionCaption(selectedBar, range)
        Spacer(Modifier.height(8.dp))
        StackedBarChart(
            bars = bars,
            range = range,
            selectedIndex = selectedIndex,
            onSelect = { index -> selectedIndex = if (selectedIndex == index) null else index },
            onClear = { selectedIndex = null },
            modifier = Modifier.fillMaxWidth().height(160.dp)
        )
        Spacer(Modifier.height(12.dp))
        IntentLegend(bars)
    }
}

private fun dayName(bar: DayBar, range: RangeOption): String =
    if (range == RangeOption.MONTH) "Day ${bar.label}" else bar.label

private fun DayBar.totalMs(): Long = byIntent.values.sum()

private fun splitText(bar: DayBar): String =
    bar.byIntent.entries
        .filter { it.value > 0 }
        .sortedByDescending { it.value }
        .joinToString(" · ") { (type, ms) -> "${type.label} ${ms.formatDuration()}" }

@Composable
private fun SelectionCaption(bar: DayBar?, range: RangeOption) {
    Column(modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
        if (bar == null) {
            Text(
                "Tap a bar for details",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val total = bar.totalMs()
            Text(
                text = "${dayName(bar, range)} · ${if (total > 0) total.formatDuration() else "No sessions"}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (total > 0) {
                Text(
                    text = splitText(bar),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StackedBarChart(
    bars: List<DayBar>,
    range: RangeOption,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val maxMs = bars.maxOfOrNull { it.totalMs() }?.takeIf { it > 0 } ?: 1L
    val gap = if (bars.size > 14) 2.dp else 4.dp

    Column(
        modifier = modifier.pointerInput(Unit) { detectTapGestures { onClear() } }
    ) {
        // Bar area: baseline hairline layered behind the bars, sharing the same bottom edge.
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalAlignment = Alignment.Bottom
            ) {
                bars.forEachIndexed { index, bar ->
                    val total = bar.totalMs()
                    val isSelected = selectedIndex == index
                    val dimmed = selectedIndex != null && !isSelected
                    val description = if (total > 0) {
                        "${dayName(bar, range)}, ${total.formatDuration()} of focus. ${splitText(bar)}"
                    } else {
                        "${dayName(bar, range)}, no sessions"
                    }

                    // Full-height column is the tap target; the visible bar sits at its bottom.
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(onClickLabel = "Show details", role = Role.Button) { onSelect(index) }
                            .semantics { contentDescription = description; selected = isSelected },
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        if (total > 0) {
                            val segments = bar.byIntent.entries
                                .sortedBy { it.key.ordinal }
                                .filter { it.value > 0 }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight((total.toFloat() / maxMs).coerceIn(0.02f, 1f))
                                    .alpha(if (dimmed) 0.35f else 1f)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)),
                                verticalArrangement = Arrangement.Bottom
                            ) {
                                segments.forEach { (type, ms) ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight((ms.toFloat() / total).coerceAtLeast(0.01f))
                                            .background(type.accent())
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .alpha(if (dimmed) 0.35f else 1f)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // Day labels, aligned under their bars
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(gap)
        ) {
            bars.forEachIndexed { index, bar ->
                Text(
                    text = bar.label,
                    fontSize = 10.sp,
                    fontWeight = if (selectedIndex == index) FontWeight.Bold else FontWeight.Normal,
                    color = if (selectedIndex == index) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IntentLegend(bars: List<DayBar>) {
    val present = bars.flatMap { b -> b.byIntent.filterValues { it > 0 }.keys }.toSet()
    val types = IntentType.entries.filter { it in present }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        types.forEach { type ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(type.accent())
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    type.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Intent breakdown
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun IntentRow(item: IntentTotal, modifier: Modifier = Modifier) {
    val duration = item.totalMs.formatDuration()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = "${item.type.label}, $duration" }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                item.type.icon,
                contentDescription = null,
                tint = item.type.accent(),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = item.type.label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = duration,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { item.fraction },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            color = item.type.accent(),
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Friction
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FrictionSection(friction: FrictionSummary, modifier: Modifier = Modifier) {
    if (friction.shown == 0) {
        MutedText("No friction moments recorded yet.", modifier)
        return
    }
    val resistPct = (friction.wentBack * 100) / friction.shown
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                FrictionStat("Shown", friction.shown.toString(), MaterialTheme.colorScheme.onSurface)
                FrictionStat("Opened anyway", friction.openedAnyway.toString(), MaterialTheme.colorScheme.error)
                FrictionStat("Went back", friction.wentBack.toString(), MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "You resisted $resistPct% of friction moments",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FrictionStat(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "$label: $value" }
    ) {
        Text(text = value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = color)
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Notification digests
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun QueuedLine(count: Int, onDeliverNow: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp).heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.HourglassEmpty,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "$count held for the next delivery",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDeliverNow) { Text("Deliver now") }
        }
    }
}

private val TIME_FORMAT: DateTimeFormatter
    get() = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

@Composable
private fun BatchHeader(batch: DeliveredBatch, expanded: Boolean, onToggle: () -> Unit) {
    val count = batch.notifications.size
    val appCount = batch.notifications.map { it.packageName }.distinct().size
    val time = if (batch.deliveredAtMs > 0L) {
        TIME_FORMAT.format(Instant.ofEpochMilli(batch.deliveredAtMs).atZone(ZoneId.systemDefault()))
    } else "Delivered"
    val title = "$time · $count notification${if (count == 1) "" else "s"} from $appCount app${if (appCount == 1) "" else "s"}"
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClickLabel = if (expanded) "Collapse" else "Expand", role = Role.Button, onClick = onToggle)
            .semantics(mergeDescendants = true) { stateDescription = if (expanded) "Expanded" else "Collapsed" }
            .padding(horizontal = GUTTER, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (batch.deliveredAtMs > 0L) {
                Text(
                    text = relativeTime(batch.deliveredAtMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.Rounded.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.rotate(rotation)
        )
    }
}

@Composable
private fun HeldNotificationRow(n: HeldNotification) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = GUTTER, vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        AppIcon(n.packageName, size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (n.title.isBlank()) n.appLabel else n.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (n.text.isNotBlank()) {
                Text(
                    text = n.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "${n.appLabel} · ${relativeTime(n.postedAtMs)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MutedText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

private fun relativeTime(ms: Long): String {
    val diff = System.currentTimeMillis() - ms
    val minutes = diff / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        else -> "${days}d ago"
    }
}
