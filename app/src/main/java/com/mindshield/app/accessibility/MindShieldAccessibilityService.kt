package com.mindshield.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityEvent
import com.mindshield.app.data.FrictionBlocklist
import com.mindshield.app.data.IntentFrictionRules

class MindShieldAccessibilityService : AccessibilityService() {

    private var activeOverlay: FrictionOverlay? = null
    private var overlayPackage: String? = null      // package the current overlay is for
    private var bypassPackage: String? = null       // package allowed through after "Open anyway"

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        // Never intercept our own app
        if (pkg == packageName) {
            clearBypass()
            return
        }

        // User already tapped "Open anyway" for this package — let them in
        if (pkg == bypassPackage) return

        // Navigated away from bypass app — clear bypass
        if (pkg != bypassPackage) bypassPackage = null

        // Overlay already visible for this exact package — don't re-trigger
        // (apps fire multiple TYPE_WINDOW_STATE_CHANGED on launch)
        if (pkg == overlayPackage && activeOverlay != null) return

        val shouldFriction = FrictionBlocklist.isBlocked(pkg) ||
                IntentFrictionRules.isBlockedForCurrentSession(pkg)

        if (!shouldFriction) {
            // Different non-friction app came to front — dismiss any stale overlay
            if (pkg != overlayPackage) dismissOverlay()
            return
        }

        // Show overlay for this package
        dismissOverlay()
        overlayPackage = pkg

        activeOverlay = FrictionOverlay(
            context = this,
            appName = appLabel(pkg),
            onOpenAnyway = {
                bypassPackage = pkg
                activeOverlay = null
                overlayPackage = null
            },
            onGoBack = {
                performGlobalAction(GLOBAL_ACTION_BACK)
                activeOverlay = null
                overlayPackage = null
            }
        ).also { it.show() }
    }

    override fun onInterrupt() = dismissOverlay()

    override fun onDestroy() {
        super.onDestroy()
        dismissOverlay()
    }

    private fun dismissOverlay() {
        activeOverlay?.dismiss()
        activeOverlay = null
        overlayPackage = null
    }

    private fun clearBypass() {
        bypassPackage = null
    }

    private fun appLabel(pkg: String): String =
        runCatching {
            packageManager
                .getApplicationInfo(pkg, PackageManager.GET_META_DATA)
                .let { packageManager.getApplicationLabel(it).toString() }
        }.getOrDefault(pkg)
}
