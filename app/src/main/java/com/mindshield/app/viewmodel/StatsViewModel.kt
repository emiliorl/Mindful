package com.mindshield.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mindshield.app.data.AppDatabase
import com.mindshield.app.data.FrictionEvent
import com.mindshield.app.data.IntentType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// Range options
// ─────────────────────────────────────────────────────────────────────────────

enum class RangeOption(val label: String, val days: Int) {
    TODAY("Today", 1),
    WEEK("Week",   7),
    MONTH("Month", 30)
}

// ─────────────────────────────────────────────────────────────────────────────
// Output models
// ─────────────────────────────────────────────────────────────────────────────

data class DayBar(
    val label: String,
    val byIntent: Map<IntentType, Long>   // intentType → total ms
)

data class IntentTotal(
    val type: IntentType,
    val totalMs: Long,
    val fraction: Float                   // 0..1 relative to the longest
)

data class FrictionSummary(
    val shown: Int,
    val openedAnyway: Int,
    val wentBack: Int
)

data class StatsSummary(
    val range: RangeOption,
    val dailyBars: List<DayBar>,
    val intentBreakdown: List<IntentTotal>,
    val frictionSummary: FrictionSummary,
    val notificationsBatched: Int
)

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)

    val selectedRange = MutableStateFlow(RangeOption.WEEK)

    val summary: StateFlow<StatsSummary> = selectedRange
        .flatMapLatest { range ->
            val (fromMs, toMs) = range.toEpochRange()

            combine(
                db.completedSessionDao().getBetween(fromMs, toMs),
                db.frictionEventDao().getBetween(fromMs, toMs),
                db.heldNotificationDao().countDeliveredBetween(fromMs, toMs)
            ) { sessions, frictionEvents, batchedCount ->

                // ── Daily bars ────────────────────────────────────────────────
                val zone = ZoneId.systemDefault()
                val today = LocalDate.now(zone)
                val days = (0 until range.days).map { offset ->
                    today.minusDays((range.days - 1 - offset).toLong())
                }

                val dailyBars = days.map { day ->
                    val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
                    val dayEnd   = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

                    val byIntent = sessions
                        .filter { it.startMs >= dayStart && it.startMs < dayEnd }
                        .groupBy { it.intentType }
                        .mapValues { (_, list) -> list.sumOf { it.durationMs } }
                        .mapKeys { (k, _) ->
                            runCatching { IntentType.valueOf(k) }.getOrDefault(IntentType.JUST_LOOKING)
                        }

                    val label = when (range) {
                        RangeOption.TODAY -> "Today"
                        RangeOption.WEEK  -> day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                        RangeOption.MONTH -> day.dayOfMonth.toString()
                    }
                    DayBar(label, byIntent)
                }

                // ── Intent breakdown ──────────────────────────────────────────
                val totalByIntent = sessions
                    .groupBy { it.intentType }
                    .mapValues { (_, list) -> list.sumOf { it.durationMs } }

                val maxMs = totalByIntent.values.maxOrNull()?.takeIf { it > 0 } ?: 1L

                val intentBreakdown = IntentType.entries
                    .mapNotNull { type ->
                        val ms = totalByIntent[type.name] ?: return@mapNotNull null
                        IntentTotal(type, ms, ms.toFloat() / maxMs)
                    }
                    .sortedByDescending { it.totalMs }

                // ── Friction summary ──────────────────────────────────────────
                val opened    = frictionEvents.count { it.outcome == FrictionEvent.OUTCOME_OPENED }
                val wentBack  = frictionEvents.count { it.outcome == FrictionEvent.OUTCOME_WENT_BACK }
                val friction  = FrictionSummary(
                    shown        = frictionEvents.size,
                    openedAnyway = opened,
                    wentBack     = wentBack
                )

                StatsSummary(range, dailyBars, intentBreakdown, friction, batchedCount)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), defaultSummary())

    // ─────────────────────────────────────────────────────────────────────────

    private fun RangeOption.toEpochRange(): Pair<Long, Long> {
        val zone  = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val from  = today.minusDays((days - 1).toLong()).atStartOfDay(zone).toInstant()
        val to    = today.plusDays(1).atStartOfDay(zone).toInstant()
        return from.toEpochMilli() to to.toEpochMilli()
    }

    private fun defaultSummary() = StatsSummary(
        range            = RangeOption.WEEK,
        dailyBars        = emptyList(),
        intentBreakdown  = emptyList(),
        frictionSummary  = FrictionSummary(0, 0, 0),
        notificationsBatched = 0
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Formatting helpers
// ─────────────────────────────────────────────────────────────────────────────

fun Long.formatDuration(): String {
    val totalSeconds = this / 1_000
    val hours   = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return when {
        hours > 0   -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else        -> "<1m"
    }
}
