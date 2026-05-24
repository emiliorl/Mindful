package com.mindshield.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mindshield.app.data.AppDatabase
import com.mindshield.app.data.ChecklistItem
import com.mindshield.app.data.MorningConfig
import com.mindshield.app.data.RoutineChecklistStore
import com.mindshield.app.data.RoutineCompletion
import com.mindshield.app.data.RoutineStore
import com.mindshield.app.data.WindDownConfig
import com.mindshield.app.data.newChecklistItem
import com.mindshield.app.service.ZoneManagerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

class RoutinesViewModel(app: Application) : AndroidViewModel(app) {

    val morning: StateFlow<MorningConfig> = RoutineStore.morning
    val windDown: StateFlow<WindDownConfig> = RoutineStore.windDown
    val completedIds: StateFlow<Set<String>> = RoutineChecklistStore.completedIds
    val routinePhase = ZoneManagerService.routinePhase

    // Combined progress fraction for morning checklist (0.0 – 1.0)
    val morningProgress: StateFlow<Float> = combine(morning, completedIds) { m, ids ->
        if (m.checklist.isEmpty()) 0f
        else m.checklist.count { it.id in ids }.toFloat() / m.checklist.size
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    val windDownProgress: StateFlow<Float> = combine(windDown, completedIds) { w, ids ->
        if (w.checklist.isEmpty()) 0f
        else w.checklist.count { it.id in ids }.toFloat() / w.checklist.size
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    // Streak — consecutive days with at least one completed routine
    private val _morningStreak = kotlinx.coroutines.flow.MutableStateFlow(0)
    val morningStreak: StateFlow<Int> = _morningStreak

    private val _windDownStreak = kotlinx.coroutines.flow.MutableStateFlow(0)
    val windDownStreak: StateFlow<Int> = _windDownStreak

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _morningStreak.value = computeStreak("MORNING")
            _windDownStreak.value = computeStreak("WIND_DOWN")
        }
    }

    // ── Morning config updates ─────────────────────────────────────────────────

    fun setMorningEnabled(enabled: Boolean) =
        RoutineStore.setMorningEnabled(getApplication(), enabled)

    fun setMorningWakeTime(time: LocalTime) =
        RoutineStore.setMorningWakeTime(getApplication(), time)

    fun setMorningPhoneAvailableTime(time: LocalTime) =
        RoutineStore.setMorningPhoneAvailableTime(getApplication(), time)

    fun addMorningChecklistItem(text: String) {
        val updated = morning.value.checklist + newChecklistItem(text)
        RoutineStore.setMorningChecklist(getApplication(), updated)
    }

    fun removeMorningChecklistItem(item: ChecklistItem) {
        val updated = morning.value.checklist.filter { it.id != item.id }
        RoutineStore.setMorningChecklist(getApplication(), updated)
    }

    // ── Wind-down config updates ───────────────────────────────────────────────

    fun setWindDownEnabled(enabled: Boolean) =
        RoutineStore.setWindDownEnabled(getApplication(), enabled)

    fun setWindDownStartTime(time: LocalTime) =
        RoutineStore.setWindDownStartTime(getApplication(), time)

    fun setWindDownSleepTime(time: LocalTime) =
        RoutineStore.setWindDownSleepTime(getApplication(), time)

    fun setWindDownExtendedDelay(seconds: Int) =
        RoutineStore.setWindDownExtendedDelay(getApplication(), seconds)

    fun addWindDownChecklistItem(text: String) {
        val updated = windDown.value.checklist + newChecklistItem(text)
        RoutineStore.setWindDownChecklist(getApplication(), updated)
    }

    fun removeWindDownChecklistItem(item: ChecklistItem) {
        val updated = windDown.value.checklist.filter { it.id != item.id }
        RoutineStore.setWindDownChecklist(getApplication(), updated)
    }

    // ── Checklist completion ───────────────────────────────────────────────────

    fun toggleMorningItem(item: ChecklistItem) {
        val app = getApplication<Application>()
        val wasCompleted = RoutineChecklistStore.isCompleted(item.id)
        if (wasCompleted) {
            RoutineChecklistStore.markUncompleted(app, item.id)
        } else {
            RoutineChecklistStore.markCompleted(app, item.id)
            val m = morning.value
            // All morning items done → record completion
            if (m.checklist.all { RoutineChecklistStore.isCompleted(it.id) }) {
                recordCompletion("MORNING")
            }
        }
    }

    fun toggleWindDownItem(item: ChecklistItem) {
        val app = getApplication<Application>()
        val wasCompleted = RoutineChecklistStore.isCompleted(item.id)
        if (wasCompleted) {
            RoutineChecklistStore.markUncompleted(app, item.id)
        } else {
            RoutineChecklistStore.markCompleted(app, item.id)
            val w = windDown.value
            if (w.checklist.all { RoutineChecklistStore.isCompleted(it.id) }) {
                recordCompletion("WIND_DOWN")
            }
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun recordCompletion(type: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val today = LocalDate.now().toString()
            val db = AppDatabase.get(getApplication())
            db.routineCompletionDao().insert(
                RoutineCompletion(
                    id = "${type}_$today",
                    type = type,
                    dateStr = today,
                    completedAtMs = System.currentTimeMillis()
                )
            )
            when (type) {
                "MORNING" -> _morningStreak.value = computeStreak("MORNING")
                "WIND_DOWN" -> _windDownStreak.value = computeStreak("WIND_DOWN")
            }
        }
    }

    private suspend fun computeStreak(type: String): Int {
        val dates = AppDatabase.get(getApplication())
            .routineCompletionDao()
            .getDatesForType(type)
            .sortedDescending()
        if (dates.isEmpty()) return 0

        var streak = 0
        var current = LocalDate.now()
        for (dateStr in dates) {
            val date = LocalDate.parse(dateStr)
            if (date == current || date == current.minusDays(1)) {
                streak++
                current = date
            } else break
        }
        return streak
    }
}
