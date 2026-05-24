package com.mindshield.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.mindshield.app.service.ZoneManagerService
import com.mindshield.app.util.OnboardingPrefs

/**
 * Restarts the [ZoneManagerService] after a device reboot or app update,
 * but only if the user has already completed onboarding.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        // Only restart if onboarding is complete (service is expected)
        if (!OnboardingPrefs.isComplete(context)) return

        ContextCompat.startForegroundService(
            context,
            Intent(context, ZoneManagerService::class.java)
        )
    }
}
