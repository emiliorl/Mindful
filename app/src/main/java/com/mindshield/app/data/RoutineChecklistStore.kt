package com.mindshield.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

object RoutineChecklistStore {

    private const val PREFS_NAME = "routine_checklist"
    private const val KEY_DATE = "checklist_date"
    private const val KEY_COMPLETED_IDS = "completed_ids"

    private val _completedIds = MutableStateFlow<Set<String>>(emptySet())
    val completedIds: StateFlow<Set<String>> = _completedIds

    fun init(context: Context) {
        val p = prefs(context)
        val today = LocalDate.now().toString()
        val storedDate = p.getString(KEY_DATE, null)
        if (storedDate != today) {
            // New day — reset
            p.edit()
                .putString(KEY_DATE, today)
                .putString(KEY_COMPLETED_IDS, "")
                .apply()
            _completedIds.value = emptySet()
        } else {
            val raw = p.getString(KEY_COMPLETED_IDS, "") ?: ""
            _completedIds.value = if (raw.isEmpty()) emptySet()
            else raw.split(",").toSet()
        }
    }

    fun markCompleted(context: Context, itemId: String) {
        val updated = _completedIds.value + itemId
        _completedIds.value = updated
        prefs(context).edit()
            .putString(KEY_COMPLETED_IDS, updated.joinToString(","))
            .apply()
    }

    fun markUncompleted(context: Context, itemId: String) {
        val updated = _completedIds.value - itemId
        _completedIds.value = updated
        prefs(context).edit()
            .putString(KEY_COMPLETED_IDS, updated.joinToString(","))
            .apply()
    }

    fun isCompleted(itemId: String): Boolean = itemId in _completedIds.value

    fun resetForToday(context: Context) {
        _completedIds.value = emptySet()
        prefs(context).edit()
            .putString(KEY_DATE, LocalDate.now().toString())
            .putString(KEY_COMPLETED_IDS, "")
            .apply()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
