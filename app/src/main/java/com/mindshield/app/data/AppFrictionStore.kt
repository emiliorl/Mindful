package com.mindshield.app.data

import android.content.Context
import android.content.SharedPreferences
import com.mindshield.app.service.ZoneManagerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// ─────────────────────────────────────────────────────────────────────────────
// Mode
// ─────────────────────────────────────────────────────────────────────────────

enum class FrictionMode { OFF, ALWAYS, SMART }

// ─────────────────────────────────────────────────────────────────────────────
// Per-app config
// ─────────────────────────────────────────────────────────────────────────────

data class AppFrictionConfig(
    val mode: FrictionMode = FrictionMode.OFF,
    val smartIntents: Set<IntentType> = emptySet()
)

// ─────────────────────────────────────────────────────────────────────────────
// Store
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Single source of truth for per-app friction configuration.
 *
 * Replaces the old split between FrictionBlocklist and IntentFrictionRules.
 * Mode is stored *explicitly* — SMART with zero intents selected is a valid
 * distinct state, not silently collapsed to OFF.
 *
 * Encoding in SharedPreferences (one file, two key families):
 *   "mode:com.pkg"    → "OFF" | "ALWAYS" | "SMART"
 *   "intents:com.pkg" → "WORK,STUDY,..." (empty string when none selected)
 *
 * Only packages with a non-OFF mode are written. Absent key → OFF.
 */
object AppFrictionStore {

    private const val PREFS_NAME   = "app_friction_store"
    private const val MODE_PREFIX  = "mode:"
    private const val INTENT_PREFIX = "intents:"

    private val _configs = MutableStateFlow<Map<String, AppFrictionConfig>>(emptyMap())
    val configs: StateFlow<Map<String, AppFrictionConfig>> = _configs

    // ── Init ─────────────────────────────────────────────────────────────────

    fun init(context: Context) {
        _configs.value = loadAll(context)
    }

    // ── Reads ─────────────────────────────────────────────────────────────────

    fun getConfig(packageName: String): AppFrictionConfig =
        _configs.value[packageName] ?: AppFrictionConfig()

    /**
     * Returns true if the app should show friction given the current active session.
     * Safe to call from the AccessibilityService on any thread.
     */
    fun shouldFriction(packageName: String): Boolean {
        val config = getConfig(packageName)
        return when (config.mode) {
            FrictionMode.ALWAYS -> true
            FrictionMode.SMART  -> {
                val session = ZoneManagerService.sessionState.value ?: return false
                config.smartIntents.contains(session.type)
            }
            FrictionMode.OFF    -> false
        }
    }

    // ── Writes ────────────────────────────────────────────────────────────────

    fun setMode(context: Context, packageName: String, mode: FrictionMode) {
        val existing = getConfig(packageName)
        val updated = existing.copy(mode = mode)
        applyUpdate(context, packageName, updated)
    }

    fun setSmartIntent(
        context: Context,
        packageName: String,
        type: IntentType,
        enabled: Boolean
    ) {
        val existing = getConfig(packageName)
        val newIntents = existing.smartIntents.toMutableSet().apply {
            if (enabled) add(type) else remove(type)
        }
        applyUpdate(context, packageName, existing.copy(smartIntents = newIntents))
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun applyUpdate(context: Context, packageName: String, config: AppFrictionConfig) {
        val updated = _configs.value.toMutableMap()
        if (config.mode == FrictionMode.OFF && config.smartIntents.isEmpty()) {
            updated.remove(packageName)
        } else {
            updated[packageName] = config
        }
        _configs.value = updated
        persist(context, packageName, config)
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun persist(context: Context, packageName: String, config: AppFrictionConfig) {
        prefs(context).edit().apply {
            if (config.mode == FrictionMode.OFF && config.smartIntents.isEmpty()) {
                remove("$MODE_PREFIX$packageName")
                remove("$INTENT_PREFIX$packageName")
            } else {
                putString("$MODE_PREFIX$packageName", config.mode.name)
                putString(
                    "$INTENT_PREFIX$packageName",
                    config.smartIntents.joinToString(",") { it.name }
                )
            }
            apply()
        }
    }

    private fun loadAll(context: Context): Map<String, AppFrictionConfig> {
        val all = prefs(context).all
        val packages = all.keys
            .filter { it.startsWith(MODE_PREFIX) }
            .map { it.removePrefix(MODE_PREFIX) }

        return packages.mapNotNull { pkg ->
            val modeStr = all["$MODE_PREFIX$pkg"] as? String ?: return@mapNotNull null
            val mode = runCatching { FrictionMode.valueOf(modeStr) }.getOrNull()
                ?: return@mapNotNull null

            val intentsStr = all["$INTENT_PREFIX$pkg"] as? String ?: ""
            val intents = if (intentsStr.isBlank()) emptySet() else {
                intentsStr.split(",").mapNotNull { name ->
                    runCatching { IntentType.valueOf(name.trim()) }.getOrNull()
                }.toSet()
            }

            pkg to AppFrictionConfig(mode = mode, smartIntents = intents)
        }.toMap()
    }
}
