package com.mindshield.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityEvent
import com.mindshield.app.data.FrictionBlocklist
import com.mindshield.app.data.IntentFrictionRules

/**
 * Listens for TYPE_WINDOW_STATE_CHANGED events.
 * When the foreground app is in the friction blocklist, a [FrictionOverlay]
 * is drawn over it immediately. The overlay's own buttons handle dismissal.
 *
 * Design note: we track the last-shown package to avoid showing the overlay
 * again when the user returns to the same app after tapping "Open anyway" —
 * the overlay is only re-triggered when a *different* app comes to the fore.
 */
class MindShieldAccessibilityService : AccessibilityService() {

    private var activeOverlay: FrictionOverlay? = null
    private var lastFrictionedPackage: String? = null
    private var overlayShownForPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 100
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        // Never intercept our own app
        if (pkg == packageName) {
            overlayShownForPackage = null
            return
        }

        // If the user tapped "Open anyway" we already dismissed and stored the pkg;
        // skip re-triggering until they navigate away and come back
        if (pkg == overlayShownForPackage) return

        // A different app came to foreground — clear the bypass memory
        if (pkg != lastFrictionedPackage) {
            overlayShownForPackage = null
        }

        val shouldFriction = FrictionBlocklist.isBlocked(pkg) ||
                IntentFrictionRules.isBlockedForCurrentSession(pkg)

        if (!shouldFriction) {
            dismissOverlay()
            return
        }

        // Show the overlay for this new friction app
        dismissOverlay()
        lastFrictionedPackage = pkg
        val appName = appLabel(pkg)

        activeOverlay = FrictionOverlay(
            context = this,
            appName = appName,
            onOpenAnyway = {
                overlayShownForPackage = pkg   // bypass until they leave and return
                activeOverlay = null
            },
            onGoBack = {
                performGlobalAction(GLOBAL_ACTION_BACK)
                activeOverlay = null
            }
        ).also { it.show() }
    }

    override fun onInterrupt() {
        dismissOverlay()
    }

    override fun onDestroy() {
        super.onDestroy()
        dismissOverlay()
    }

    private fun dismissOverlay() {
        activeOverlay?.dismiss()
        activeOverlay = null
    }

    private fun appLabel(packageName: String): String =
        runCatching {
            packageManager
                .getApplicationInfo(packageName, PackageManager.GET_META_DATA)
                .let { packageManager.getApplicationLabel(it).toString() }
        }.getOrDefault(packageName)
}
