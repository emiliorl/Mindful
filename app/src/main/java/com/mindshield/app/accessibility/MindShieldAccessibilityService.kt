package com.mindshield.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.pm.PackageManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.mindshield.app.data.AppFrictionStore
import com.mindshield.app.data.FrictionMode
import com.mindshield.app.data.RoutinePhase
import com.mindshield.app.service.ZoneManagerService

class MindShieldAccessibilityService : AccessibilityService() {

    private var activeOverlay: FrictionOverlay? = null
    private var overlayPackage: String? = null
    private var bypassPackage: String? = null
    private var backSuppressedPackage: String? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        if (pkg == packageName) { bypassPackage = null; return }

        if (pkg == backSuppressedPackage) return
        if (pkg != backSuppressedPackage) backSuppressedPackage = null

        if (pkg == bypassPackage) return
        if (pkg != bypassPackage) bypassPackage = null

        if (pkg == overlayPackage && activeOverlay != null) return

        val routinePhase = ZoneManagerService.routinePhase.value
        val config = AppFrictionStore.configs.value[pkg]
        val hasFrictionConfig = config != null && config.mode != FrictionMode.OFF

        val shouldShow = when (routinePhase) {
            RoutinePhase.SLEEP -> hasFrictionConfig
            RoutinePhase.WIND_DOWN -> hasFrictionConfig
            RoutinePhase.MORNING -> hasFrictionConfig
            null -> AppFrictionStore.shouldFriction(pkg)
        }

        if (!shouldShow) {
            if (pkg != overlayPackage) dismissOverlay()
            return
        }

        val isSleepBlock = routinePhase == RoutinePhase.SLEEP
        val duration = when (routinePhase) {
            RoutinePhase.WIND_DOWN -> com.mindshield.app.data.RoutineStore.windDown.value.extendedDelaySeconds
            RoutinePhase.SLEEP -> AppFrictionStore.pauseDuration.value
            else -> AppFrictionStore.pauseDuration.value
        }

        Log.d(TAG, "Showing friction overlay for $pkg (phase=$routinePhase)")
        dismissOverlay()
        overlayPackage = pkg

        activeOverlay = FrictionOverlay(
            context = this,
            appName = appLabel(pkg),
            durationSeconds = duration,
            isSleepBlock = isSleepBlock,
            onOpenAnyway = {
                Log.d(TAG, "User opened $pkg anyway (phase=$routinePhase)")
                bypassPackage = pkg
                activeOverlay = null
                overlayPackage = null
            },
            onGoBack = {
                Log.d(TAG, "User went back from $pkg")
                backSuppressedPackage = pkg
                activeOverlay = null
                overlayPackage = null
                performGlobalAction(GLOBAL_ACTION_HOME)
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
