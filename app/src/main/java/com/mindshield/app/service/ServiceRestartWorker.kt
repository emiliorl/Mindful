package com.mindshield.app.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class ServiceRestartWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = try {
        ContextCompat.startForegroundService(
            applicationContext,
            Intent(applicationContext, ZoneManagerService::class.java)
        )
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }
}
