package com.mindshield.app.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mindshield.app.data.BatchRuleStore
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class BatchDeliveryWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        BatchRuleStore.init(applicationContext)
        val settings = BatchRuleStore.globalSettings.value
        if (settings.deliveryTimes.isEmpty()) return Result.success()

        val prefs = applicationContext.getSharedPreferences("batch_delivery_state", Context.MODE_PRIVATE)
        val nowMs    = System.currentTimeMillis()
        val nowLocal = LocalDateTime.now()
        val lastMs   = prefs.getLong("last_delivery", 0L)
        val lastDelivery = if (lastMs == 0L) LocalDateTime.MIN
                           else Instant.ofEpochMilli(lastMs)
                               .atZone(ZoneId.systemDefault())
                               .toLocalDateTime()

        val today     = nowLocal.toLocalDate()
        val yesterday = today.minusDays(1)

        val scheduledTimeElapsed = settings.deliveryTimes.any { time ->
            listOf(today, yesterday).any { date ->
                val occurrence = LocalDateTime.of(date, time)
                occurrence.isAfter(lastDelivery) && !occurrence.isAfter(nowLocal)
            }
        }

        if (scheduledTimeElapsed) {
            BatchDeliveryHelper.deliverAll(applicationContext)
            prefs.edit().putLong("last_delivery", nowMs).apply()
        }

        return Result.success()
    }
}
