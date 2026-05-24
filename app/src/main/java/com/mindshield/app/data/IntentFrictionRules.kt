package com.mindshield.app.data

import android.content.Context
import android.content.SharedPreferences
import com.mindshield.app.service.ZoneManagerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Stores per-app, per-intent friction rules.
 *
 * A package in this map gets friction shown when the user's *active session*
 * matches one of the configured [IntentType]s — independently of the permanent
 * [FrictionBlocklist]. This lets users say "add friction to Instagram only
 * during Work and Study sessions" without permanently blocking it.
 */
object IntentFrictionRules {

    private const val PREFS_NAME = "intent_friction_rules"

    // Map<packageName, Set<IntentType.name>>
    private val _rules = MutableStateFlow<Map<String, Set<IntentType>>>(emptyMap())
    val rules: StateFlow<Map<String, Set<IntentType>>> = _rules

    fun init(context: Context) {
        _rules.value = load(context)
    }

    /** Returns true if [packageName] should get friction given the current active session. */
    fun isBlockedForCurrentSession(packageName: String): Boolean {
        val session = ZoneManagerService.sessionState.value ?: return false
        return _rules.value[packageName]?.contains(session.type) == true
    }

    /** Returns the set of [IntentType]s for which [packageName] has auto-friction enabled. */
    fun intentsFor(packageName: String): Set<IntentType> =
        _rules.value[packageName] ?: emptySet()

    fun setRule(context: Context, packageName: String, type: IntentType, enabled: Boolean) {
        val current = _rules.value.toMutableMap()
        val existing = current[packageName]?.toMutableSet() ?: mutableSetOf()
        if (enabled) existing.add(type) else existing.remove(type)
        if (existing.isEmpty()) current.remove(packageName) else current[packageName] = existing
        _rules.value = current
        save(context, current)
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun save(context: Context, rules: Map<String, Set<IntentType>>) {
        prefs(context).edit().apply {
            clear()
            rules.forEach { (pkg, intents) ->
                putStringSet(pkg, intents.mapTo(mutableSetOf()) { it.name })
            }
            apply()
        }
    }

    private fun load(context: Context): Map<String, Set<IntentType>> {
        val prefs = prefs(context)
        return prefs.all.mapNotNull { (pkg, value) ->
            val names = value as? Set<*> ?: return@mapNotNull null
            val intents = names.mapNotNull { name ->
                runCatching { IntentType.valueOf(name.toString()) }.getOrNull()
            }.toSet()
            if (intents.isEmpty()) null else pkg to intents
        }.toMap()
    }
}
