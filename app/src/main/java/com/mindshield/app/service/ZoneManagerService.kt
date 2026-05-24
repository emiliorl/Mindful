package com.mindshield.app.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.mindshield.app.MainActivity
import com.mindshield.app.MindShieldApp
import com.mindshield.app.R
import com.mindshield.app.data.IntentSession
import com.mindshield.app.data.IntentType
import com.mindshield.app.data.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Persistent foreground service that owns the current [IntentSession].
 *
 * State is exposed as a [StateFlow] so any component can observe it without
 * binding to this service. On START_STICKY restart the last session is restored
 * from [SessionStore] so the user's context survives process death.
 */
class ZoneManagerService : Service() {

    companion object {
        private val _sessionState = MutableStateFlow<IntentSession?>(null)
        val sessionState: StateFlow<IntentSession?> = _sessionState

        const val ACTION_START  = "com.mindshield.START_SESSION"
        const val ACTION_STOP   = "com.mindshield.STOP_SESSION"
        const val EXTRA_INTENT  = "session_intent_type"

        private const val NOTIFICATION_ID = 1001

        fun startIntent(context: Context, type: IntentType): Intent =
            Intent(context, ZoneManagerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_INTENT, type.name)
            }

        fun stopIntent(context: Context): Intent =
            Intent(context, ZoneManagerService::class.java).apply {
                action = ACTION_STOP
            }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val typeName = intent.getStringExtra(EXTRA_INTENT) ?: IntentType.JUST_LOOKING.name
                val type = runCatching { IntentType.valueOf(typeName) }.getOrDefault(IntentType.JUST_LOOKING)
                val session = IntentSession(type, System.currentTimeMillis())
                _sessionState.value = session
                SessionStore.save(this, session)
                startForeground(NOTIFICATION_ID, buildNotification())
                updateNotification()
            }
            ACTION_STOP -> {
                _sessionState.value = null
                SessionStore.clear(this)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                // START_STICKY restart — restore from prefs if a session was active
                val saved = SessionStore.load(this)
                if (saved != null) {
                    _sessionState.value = saved
                }
                startForeground(NOTIFICATION_ID, buildNotification())
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val session = _sessionState.value
        val contentText = if (session != null)
            "${session.type.emoji} ${session.type.label} session"
        else
            "Idle"

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
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification())
    }
}
