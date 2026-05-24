package com.mindshield.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Stub for Phase 2.
 *
 * The service is declared in the manifest and the accessibility_config.xml
 * now so it appears in the Accessibility settings list. The actual
 * foreground-window detection and friction overlay logic will be added
 * in Phase 2.
 */
class MindShieldAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Phase 2: detect TYPE_WINDOW_STATE_CHANGED and show FrictionOverlay
    }

    override fun onInterrupt() {
        // No-op for Phase 0
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Phase 2: configure dynamic service info if needed
    }
}
