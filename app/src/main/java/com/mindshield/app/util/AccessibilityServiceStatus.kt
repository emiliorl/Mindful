package com.mindshield.app.util

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import com.mindshield.app.accessibility.MindShieldAccessibilityService

object AccessibilityServiceStatus {

    fun isEnabled(context: Context): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val target = ComponentName(context, MindShieldAccessibilityService::class.java)
            .flattenToString()
        return flat.split(':').any { it.equals(target, ignoreCase = true) }
    }
}
