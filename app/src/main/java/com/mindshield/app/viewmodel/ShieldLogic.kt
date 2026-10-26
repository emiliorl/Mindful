package com.mindshield.app.viewmodel

import com.mindshield.app.data.AppFrictionConfig
import com.mindshield.app.data.BatchCategory
import com.mindshield.app.data.BatchRule
import com.mindshield.app.data.FrictionMode

// Pure Kotlin (no android.* imports) so everything here is JVM-unit-testable.

/**
 * How an app's notifications are treated.
 * DEFAULT = no BatchRule: held only during focus sessions when `batchDuringSession` is on.
 */
enum class NotifyMode { DEFAULT, INSTANT, BATCHED }

enum class ShieldFilter { ALL, PROTECTED, SUGGESTED }

fun notifyModeOf(rule: BatchRule?): NotifyMode = when (rule?.category) {
    null                  -> NotifyMode.DEFAULT
    BatchCategory.INSTANT -> NotifyMode.INSTANT
    BatchCategory.BATCHED -> NotifyMode.BATCHED
}

/** "Protected" = pauses before opening, or notifications are always batched. */
fun isProtected(friction: AppFrictionConfig, rule: BatchRule?): Boolean =
    friction.mode != FrictionMode.OFF || rule?.category == BatchCategory.BATCHED

/**
 * Friction mode after flipping the row switch.
 * On: SMART if smart intents were already chosen (restores prior config), else ALWAYS.
 * Off: OFF (AppFrictionStore keeps smartIntents while OFF, so toggling back on restores SMART).
 */
fun frictionAfterToggle(config: AppFrictionConfig, on: Boolean): FrictionMode = when {
    !on                              -> FrictionMode.OFF
    config.smartIntents.isNotEmpty() -> FrictionMode.SMART
    else                             -> FrictionMode.ALWAYS
}

fun matchesFilter(
    filter: ShieldFilter,
    suggested: Boolean,
    friction: AppFrictionConfig,
    rule: BatchRule?
): Boolean = when (filter) {
    ShieldFilter.ALL       -> true
    ShieldFilter.PROTECTED -> isProtected(friction, rule)
    ShieldFilter.SUGGESTED -> suggested
}

fun matchesQuery(label: String, query: String): Boolean =
    query.isBlank() || label.contains(query.trim(), ignoreCase = true)

/** Row subtitle pieces; either may be null when not configured. */
data class StatusParts(val friction: String?, val notify: String?) {
    fun joined(): String = listOfNotNull(friction, notify).joinToString(" · ")
}

fun statusParts(friction: AppFrictionConfig, rule: BatchRule?, pauseSeconds: Int): StatusParts =
    StatusParts(
        friction = when (friction.mode) {
            FrictionMode.ALWAYS -> "${pauseSeconds}s pause"
            FrictionMode.SMART  -> "Smart pause"
            FrictionMode.OFF    -> null
        },
        notify = when (notifyModeOf(rule)) {
            NotifyMode.BATCHED -> "Batched"
            NotifyMode.INSTANT -> "Always instant"
            NotifyMode.DEFAULT -> null
        }
    )

/** Row subtitle, e.g. "6s pause · Batched". Empty string when nothing is configured. */
fun statusText(friction: AppFrictionConfig, rule: BatchRule?, pauseSeconds: Int): String =
    statusParts(friction, rule, pauseSeconds).joined()

/**
 * Takes the raw `ApplicationInfo.category` int. Literal values of the
 * ApplicationInfo.CATEGORY_* constants (stable platform API):
 * GAME=0, AUDIO=1, VIDEO=2, IMAGE=3, SOCIAL=4, NEWS=5, MAPS=6, PRODUCTIVITY=7
 * (UNDEFINED = -1). Suggested = GAME, VIDEO, SOCIAL, NEWS.
 */
fun isSuggestedCategory(category: Int): Boolean =
    category == 0 || category == 2 || category == 4 || category == 5
