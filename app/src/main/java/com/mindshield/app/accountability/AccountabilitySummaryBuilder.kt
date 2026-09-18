package com.mindshield.app.accountability

import android.content.Context
import com.mindshield.app.data.AppDatabase
import com.mindshield.app.data.CompletedSession
import com.mindshield.app.data.FrictionEvent
import com.mindshield.app.data.IntentType
import com.mindshield.app.viewmodel.formatDuration
import kotlinx.coroutines.flow.first

/**
 * Pure text-building logic for Accountability Partner summaries. No UI/Compose
 * dependencies — safe to call from a Worker, a Service, or a ViewModel alike.
 *
 * Tone: second person when addressed to the user (weekly/session summaries),
 * third person only in [buildViolationSummary] (written for the partner reading
 * it), plural-safe counts, no exclamation points, calm/factual.
 */
object AccountabilitySummaryBuilder {

    /**
     * Reads the same DAOs/queries [com.mindshield.app.viewmodel.StatsViewModel] already
     * uses to power the Stats screen and builds one short friendly paragraph covering
     * session count, intent types, total duration, and the friction-hold ratio.
     */
    suspend fun buildWeeklySummary(context: Context, weekStartMs: Long, weekEndMs: Long): String {
        val db = AppDatabase.get(context)
        val sessions = db.completedSessionDao().getBetween(weekStartMs, weekEndMs).first()
        val frictionEvents = db.frictionEventDao().getBetween(weekStartMs, weekEndMs).first()

        if (sessions.isEmpty() && frictionEvents.isEmpty()) {
            return "This week you didn't have any MindShield sessions or pause prompts to report."
        }

        val sb = StringBuilder()

        if (sessions.isNotEmpty()) {
            val sessionCount = sessions.size
            val sessionWord = if (sessionCount == 1) "session" else "sessions"
            val intentLabels = sessions
                .mapNotNull { runCatching { IntentType.valueOf(it.intentType) }.getOrNull() }
                .distinct()
                .joinToString(", ") { it.label }
            val totalMs = sessions.sumOf { it.durationMs }
            sb.append("This week you had $sessionCount focus $sessionWord")
            if (intentLabels.isNotEmpty()) sb.append(" ($intentLabels)")
            sb.append(" totaling ${totalMs.formatDuration()}.")
        } else {
            sb.append("This week you didn't have any focus sessions.")
        }

        if (frictionEvents.isNotEmpty()) {
            val total = frictionEvents.size
            val heldStrong = frictionEvents.count { it.outcome == FrictionEvent.OUTCOME_WENT_BACK }
            val promptWord = if (total == 1) "prompt" else "prompts"
            sb.append(" You held strong on $heldStrong of $total pause $promptWord.")
        }

        return sb.toString()
    }

    /** One sentence, e.g. "You just finished a 25-minute Study session." */
    fun buildSessionSummary(session: CompletedSession): String {
        val type = runCatching { IntentType.valueOf(session.intentType) }.getOrNull()
        val minutes = (session.durationMs / 60_000L).coerceAtLeast(0L)
        // Hyphenated compound modifiers stay singular regardless of count
        // ("a 3-minute session", not "a 3-minutes session").
        val typePrefix = type?.label?.let { "$it " } ?: ""
        return "You just finished a $minutes-minute ${typePrefix}session."
    }

    /**
     * One sentence written for the partner reading it (third person, not "you"),
     * e.g. "Heads up — they opened Instagram anyway during a Study session."
     * Drops the "during a ... session" clause entirely when [sessionType] is null.
     */
    fun buildViolationSummary(appLabel: String, sessionType: IntentType?): String {
        return if (sessionType != null) {
            "Heads up — they opened $appLabel anyway during a ${sessionType.label} session."
        } else {
            "Heads up — they opened $appLabel anyway."
        }
    }
}
