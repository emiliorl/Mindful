package com.mindshield.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.pm.PackageManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.mindshield.app.data.AppFrictionStore

class MindShieldAccessibilityService : AccessibilityService() {

    private var activeOverlay: FrictionOverlay? = null
    private var overlayPackage: String? = null   // package the visible overlay belongs to
    private var bypassPackage: String? = null    // allowed through after "Open anyway"

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        // Never intercept our own app
        if (pkg == packageName) { bypassPackage = null; return }

        // User tapped "Open anyway" for this exact package — let it through
        if (pkg == bypassPackage) return

        // A different app came to front — clear any stale bypass
        if (pkg != bypassPackage) bypassPackage = null

        // Overlay already visible for this package — don't re-trigger on
        // duplicate events (apps fire multiple TYPE_WINDOW_STATE_CHANGED on launch)
        if (pkg == overlayPackage && activeOverlay != null) return

        if (!AppFrictionStore.shouldFriction(pkg)) {
            if (pkg != overlayPackage) dismissOverlay()
            return
        }

        Log.d(TAG, "Showing friction overlay for $pkg")
        dismissOverlay()
        overlayPackage = pkg

        activeOverlay = FrictionOverlay(
            context = this,
            appName = appLabel(pkg),
            durationSeconds = AppFrictionStore.pauseDuration.value,
            onOpenAnyway = {
                Log.d(TAG, "User opened $pkg anyway")
                bypassPackage = pkg
                activeOverlay = null
                overlayPackage = null
            },
            onGoBack = {
                Log.d(TAG, "User went back from $pkg")
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

    private fun appLabel(pkg: String): String =
        runCatching {
            packageManager
                .getApplicationInfo(pkg, PackageManager.GET_META_DATA)
                .let { packageManager.getApplicationLabel(it).toString() }
        }.getOrDefault(pkg)

    companion object {
        private const val TAG = "MindShieldA11y"
    }
}
