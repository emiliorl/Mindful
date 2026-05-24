package com.mindshield.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.mindshield.app.MainActivity
import com.mindshield.app.MindShieldApp
import com.mindshield.app.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Persistent foreground service.
 *
 * Holds the current [SessionState] in a [StateFlow] so the rest of the app
 * (and, later, the AccessibilityService) can react to session changes without
 * binding to this service directly.
 */
class ZoneManagerService : Service() {

    // ── Public API ────────────────────────────────────────────────────────────

    data class SessionState(
        val intent: String = "",          // "" means no active session
        val startTimeMs: Long = 0L,
        val isActive: Boolean = false
    )

    companion object {
        // Shared state — readable from any component without binding
        private val _sessionState = MutableStateFlow(SessionState())
        val sessionState: StateFlow<SessionState> = _sessionState

        fun startSession(intent: String) {
            _sessionState.value = SessionState(
                intent     = intent,
                startTimeMs = System.currentTimeMillis(),
                isActive   = true
            )
        }

        fun endSession() {
            _sessionState.value = SessionState()
        }

        const val ACTION_START = "com.mindshield.START_SESSION"
        const val ACTION_STOP  = "com.mindshield.STOP_SESSION"
        const val EXTRA_INTENT = "session_intent"

        private const val NOTIFICATION_ID = 1001
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())

        when (intent?.action) {
            ACTION_START -> {
                val sessionIntent = intent.getStringExtra(EXTRA_INTENT) ?: "Other"
                startSession(sessionIntent)
                updateNotification()
            }
            ACTION_STOP  -> {
                endSession()
                stopSelf()
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Notification ──────────────────────────────────────────────────────────

    private fun buildNotification(): Notification {
        val tapIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val state = _sessionState.value
        val contentText = if (state.isActive) "Session: ${state.intent}" else "Idle"

        return NotificationCompat.Builder(this, MindShieldApp.CHANNEL_SESSION)
            .setSmallIcon(R.drawable.ic_mindshield_notification)
            .setContentTitle("MindShield")
            .setContentText(contentText)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification() {
        val nm = getSystemService(android.app.NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification())
    }
}
