package com.mindshield.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.mindshield.app.data.AppFrictionStore
import com.mindshield.app.data.BatchRuleStore
import com.mindshield.app.data.RoutineChecklistStore
import com.mindshield.app.data.RoutineStore
import com.mindshield.app.notification.BatchDeliveryWorker
import com.mindshield.app.routines.RoutineScheduler
import com.mindshield.app.service.ZoneManagerService
import java.util.concurrent.TimeUnit

class MindShieldApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        AppFrictionStore.init(this)
        BatchRuleStore.init(this)
        RoutineStore.init(this)
        RoutineChecklistStore.init(this)
        enqueueBatchWorker()
        RoutineScheduler.enqueue(this)
        // Compute current phase immediately on startup
        ZoneManagerService.updateRoutinePhase(RoutineStore.computePhase())
    }

    private fun enqueueBatchWorker() {
        val request = PeriodicWorkRequestBuilder<BatchDeliveryWorker>(1, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "batch_delivery",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

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
