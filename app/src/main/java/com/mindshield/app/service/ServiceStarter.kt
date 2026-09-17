package com.mindshield.app.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Starts [ZoneManagerService] and falls back to a backed-off WorkManager
 * retry if the OS refuses the start (e.g. ForegroundServiceStartNotAllowedException
 * on Android 12+ when called from a background context).
 */
object ServiceStarter {

    private const val RETRY_WORK_NAME = "service_restart"

    fun start(context: Context, intent: Intent = Intent(context, ZoneManagerService::class.java)) {
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            scheduleRetry(context)
        }
    }

    private fun scheduleRetry(context: Context) {
        val request = OneTimeWorkRequestBuilder<ServiceRestartWorker>()
            .setInitialDelay(30, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(RETRY_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }
}
