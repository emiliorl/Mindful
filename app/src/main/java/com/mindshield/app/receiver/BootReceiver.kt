package com.mindshield.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.mindshield.app.data.SessionStore
import com.mindshield.app.service.ZoneManagerService
import com.mindshield.app.util.OnboardingPrefs

/**
 * Restarts [ZoneManagerService] after a device reboot or app update.
 * Only fires if onboarding is complete. If a session was active before
 * the reboot, its type is forwarded so the service can restore it.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        if (!OnboardingPrefs.isComplete(context)) return

        val saved = SessionStore.load(context)
        val serviceIntent = if (saved != null) {
            ZoneManagerService.startIntent(context, saved.type)
        } else {
            Intent(context, ZoneManagerService::class.java)
        }

        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
