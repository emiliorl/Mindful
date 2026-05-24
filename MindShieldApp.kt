package com.mindshield.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class MindShieldApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            // Channel for ZoneManagerService persistent notification
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_SESSION,
                    "Active Session",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows your current MindShield session"
                    setShowBadge(false)
                }
            )

            // Channel for batch notification delivery (Phase 3)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_BATCH,
                    "Batched Notifications",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Delivers your batched notifications"
                }
            )
        }
    }

    companion object {
        const val CHANNEL_SESSION = "mindshield_session"
        const val CHANNEL_BATCH  = "mindshield_batch"
    }
}
