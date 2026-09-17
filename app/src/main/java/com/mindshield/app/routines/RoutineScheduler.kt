package com.mindshield.app.routines

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mindshield.app.data.RoutinePhase
import com.mindshield.app.data.RoutineStore
import com.mindshield.app.service.ServiceStarter
import com.mindshield.app.service.ZoneManagerService
import com.mindshield.app.util.OnboardingPrefs
import java.util.concurrent.TimeUnit

class RoutineScheduler(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        // Liveness check: restart ZoneManagerService if it died silently
        // (OEM battery killers, low-memory kills, etc).
        if (OnboardingPrefs.isComplete(applicationContext) && !ZoneManagerService.isRunning.value) {
            ServiceStarter.start(applicationContext)
        }

        val phase = RoutineStore.computePhase()
        ZoneManagerService.updateRoutinePhase(phase)

        // Auto-end any active session when sleep mode begins
        if (phase == RoutinePhase.SLEEP && ZoneManagerService.sessionState.value != null) {
            applicationContext.startService(ZoneManagerService.stopIntent(applicationContext))
        }

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
