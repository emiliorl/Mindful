package com.mindshield.app.data

import android.content.Context
import android.content.SharedPreferences

// ─────────────────────────────────────────────────────────────────────────────
// Intent types
// ─────────────────────────────────────────────────────────────────────────────

enum class IntentType(val label: String, val emoji: String) {
    SOCIAL_MEDIA("Social Media", "📱"),
    WORK("Work",               "💼"),
    STUDY("Study",             "📚"),
    FITNESS("Fitness",         "💪"),
    ENTERTAINMENT("Entertainment", "🎬"),
    JUST_LOOKING("Just Looking",   "👀");
}

// ─────────────────────────────────────────────────────────────────────────────
// Session model
// ─────────────────────────────────────────────────────────────────────────────

data class IntentSession(
    val type: IntentType,
    val startTimeMs: Long
)

// ─────────────────────────────────────────────────────────────────────────────
// SessionStore — thin SharedPreferences wrapper
// ─────────────────────────────────────────────────────────────────────────────

object SessionStore {

    private const val PREFS_NAME  = "session_store"
    private const val KEY_TYPE    = "session_type"
    private const val KEY_START   = "session_start_ms"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(context: Context, session: IntentSession) {
        prefs(context).edit()
            .putString(KEY_TYPE,  session.type.name)
            .putLong(KEY_START,   session.startTimeMs)
            .apply()
    }

    fun load(context: Context): IntentSession? {
        val p = prefs(context)
        val typeName = p.getString(KEY_TYPE, null) ?: return null
        val start    = p.getLong(KEY_START, 0L)
        val type     = runCatching { IntentType.valueOf(typeName) }.getOrNull() ?: return null
        return IntentSession(type, start)
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
