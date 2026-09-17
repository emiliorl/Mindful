package com.mindshield.app.util

import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

object PermissionStatus {

    fun isAccessibilityEnabled(context: Context): Boolean =
        AccessibilityServiceStatus.isEnabled(context)

    fun isNotificationListenerEnabled(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName)

    fun canDrawOverlays(context: Context): Boolean =
        Settings.canDrawOverlays(context)
}
