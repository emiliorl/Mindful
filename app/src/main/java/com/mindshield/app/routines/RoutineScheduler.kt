package com.mindshield.app.routines

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mindshield.app.data.RoutineStore
import com.mindshield.app.service.ZoneManagerService
import java.util.concurrent.TimeUnit

class RoutineScheduler(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val phase = RoutineStore.computePhase()
        ZoneManagerService.updateRoutinePhase(phase)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "routine_scheduler"

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<RoutineScheduler>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
