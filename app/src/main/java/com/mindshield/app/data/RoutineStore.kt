package com.mindshield.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalTime
import java.util.UUID

object RoutineStore {

    private const val PREFS_NAME = "routine_store"

    private val _morning = MutableStateFlow(MorningConfig())
    val morning: StateFlow<MorningConfig> = _morning

    private val _windDown = MutableStateFlow(WindDownConfig())
    val windDown: StateFlow<WindDownConfig> = _windDown

    fun init(context: Context) {
        val p = prefs(context)
        _morning.value = loadMorning(p)
        _windDown.value = loadWindDown(p)
    }

    // --- Morning ---

    fun setMorningEnabled(context: Context, enabled: Boolean) {
        _morning.value = _morning.value.copy(enabled = enabled)
        prefs(context).edit().putBoolean("morning_enabled", enabled).apply()
    }

    fun setMorningWakeTime(context: Context, time: LocalTime) {
        _morning.value = _morning.value.copy(wakeTime = time)
        prefs(context).edit().putString("morning_wake_time", time.toHHMM()).apply()
    }

    fun setMorningPhoneAvailableTime(context: Context, time: LocalTime) {
        _morning.value = _morning.value.copy(phoneAvailableTime = time)
        prefs(context).edit().putString("morning_phone_time", time.toHHMM()).apply()
    }

    fun setMorningChecklist(context: Context, items: List<ChecklistItem>) {
        _morning.value = _morning.value.copy(checklist = items)
        prefs(context).edit().putString("morning_checklist", items.toJson()).apply()
    }

    // --- Wind-Down ---

    fun setWindDownEnabled(context: Context, enabled: Boolean) {
        _windDown.value = _windDown.value.copy(enabled = enabled)
        prefs(context).edit().putBoolean("wind_down_enabled", enabled).apply()
    }

    fun setWindDownStartTime(context: Context, time: LocalTime) {
        _windDown.value = _windDown.value.copy(startTime = time)
        prefs(context).edit().putString("wind_down_start_time", time.toHHMM()).apply()
    }

    fun setWindDownSleepTime(context: Context, time: LocalTime) {
        _windDown.value = _windDown.value.copy(sleepTime = time)
        prefs(context).edit().putString("wind_down_sleep_time", time.toHHMM()).apply()
    }

    fun setWindDownExtendedDelay(context: Context, seconds: Int) {
        _windDown.value = _windDown.value.copy(extendedDelaySeconds = seconds)
        prefs(context).edit().putInt("wind_down_delay_secs", seconds).apply()
    }

    fun setWindDownChecklist(context: Context, items: List<ChecklistItem>) {
        _windDown.value = _windDown.value.copy(checklist = items)
        prefs(context).edit().putString("wind_down_checklist", items.toJson()).apply()
    }

    // --- Phase computation ---

    fun computePhase(now: LocalTime = LocalTime.now()): RoutinePhase? {
        val m = _morning.value
        if (m.enabled && now >= m.wakeTime && now < m.phoneAvailableTime) return RoutinePhase.MORNING

        val w = _windDown.value
        if (w.enabled) {
            if (now >= w.sleepTime) return RoutinePhase.SLEEP
            if (now >= w.startTime) return RoutinePhase.WIND_DOWN
        }
        return null
    }

    // --- Helpers ---

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun loadMorning(p: SharedPreferences) = MorningConfig(
        enabled = p.getBoolean("morning_enabled", false),
        wakeTime = p.getString("morning_wake_time", "07:00")!!.toLocalTime(),
        phoneAvailableTime = p.getString("morning_phone_time", "07:30")!!.toLocalTime(),
        checklist = p.getString("morning_checklist", null).parseChecklist()
    )

    private fun loadWindDown(p: SharedPreferences) = WindDownConfig(
        enabled = p.getBoolean("wind_down_enabled", false),
        startTime = p.getString("wind_down_start_time", "22:00")!!.toLocalTime(),
        sleepTime = p.getString("wind_down_sleep_time", "23:00")!!.toLocalTime(),
        extendedDelaySeconds = p.getInt("wind_down_delay_secs", 30),
        checklist = p.getString("wind_down_checklist", null).parseChecklist()
    )

    private fun LocalTime.toHHMM(): String = String.format("%02d:%02d", hour, minute)

    private fun String.toLocalTime(): LocalTime {
        val parts = split(":")
        return LocalTime.of(parts[0].toInt(), parts[1].toInt())
    }

    private fun List<ChecklistItem>.toJson(): String {
        val arr = JSONArray()
        forEach { item ->
            arr.put(JSONObject().apply {
                put("id", item.id)
                put("text", item.text)
                item.unlocksPackage?.let { put("pkg", it) }
            })
        }
        return arr.toString()
    }

    private fun String?.parseChecklist(): List<ChecklistItem> {
        if (this == null) return emptyList()
        return try {
            val arr = JSONArray(this)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                ChecklistItem(
                    id = obj.getString("id"),
                    text = obj.getString("text"),
                    unlocksPackage = obj.optString("pkg", "").takeIf { it.isNotEmpty() }
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

fun newChecklistItem(text: String, unlocksPackage: String? = null) =
    ChecklistItem(id = UUID.randomUUID().toString(), text = text, unlocksPackage = unlocksPackage)
