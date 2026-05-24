package com.mindshield.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Persists the set of package names that have friction enabled.
 * Exposes a [StateFlow] so the AccessibilityService and UI can react
 * to changes without polling.
 */
object FrictionBlocklist {

    private const val PREFS_NAME = "friction_blocklist"
    private const val KEY_PACKAGES = "blocked_packages"

    private val _blockedPackages = MutableStateFlow<Set<String>>(emptySet())
    val blockedPackages: StateFlow<Set<String>> = _blockedPackages

    fun init(context: Context) {
        _blockedPackages.value = load(context)
    }

    fun isBlocked(packageName: String): Boolean =
        _blockedPackages.value.contains(packageName)

    fun setBlocked(context: Context, packageName: String, blocked: Boolean) {
        val updated = _blockedPackages.value.toMutableSet().apply {
            if (blocked) add(packageName) else remove(packageName)
        }
        _blockedPackages.value = updated
        save(context, updated)
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun save(context: Context, packages: Set<String>) {
        prefs(context).edit().putStringSet(KEY_PACKAGES, packages).apply()
    }

    private fun load(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_PACKAGES, emptySet()) ?: emptySet()
}
