package com.mindshield.app.util

import android.content.Context

/**
 * Thin SharedPreferences wrapper that tracks whether the user has
 * completed the first-run permissions onboarding.
 */
object OnboardingPrefs {

    private const val PREFS_NAME = "mindshield_prefs"
    private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"

    fun isComplete(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ONBOARDING_COMPLETE, false)

    fun markComplete(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ONBOARDING_COMPLETE, true)
            .apply()
    }

    fun reset(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_ONBOARDING_COMPLETE)
            .apply()
    }
}
