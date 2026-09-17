package com.mindshield.app.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mindshield.app.data.AppDatabase
import com.mindshield.app.data.BatchRuleStore
import com.mindshield.app.data.DeliveryMode
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class BatchDeliveryWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        BatchRuleStore.init(applicationContext)
        val rules = BatchRuleStore.configs.value

        // INTERVAL: poll each app's own elapsed-since-last-delivery.
        val dao = AppDatabase.get(applicationContext).heldNotificationDao()
        val nowMs = System.currentTimeMillis()
        rules.values.filter { it.deliveryMode == DeliveryMode.INTERVAL }.forEach { rule ->
            val lastMs = dao.getLastDeliveredForPackage(rule.packageName) ?: 0L
            val elapsedHours = (nowMs - lastMs) / 3_600_000.0
            if (elapsedHours >= rule.intervalHours) {
                BatchDeliveryHelper.deliverForPackage(applicationContext, rule.packageName)
            }
        }

        // DAYPART: global schedule sweep, excluding apps on their own COUNT/INTERVAL trigger.
        val settings = BatchRuleStore.globalSettings.value
        if (settings.deliveryTimes.isEmpty()) return Result.success()

        val prefs = applicationContext.getSharedPreferences("batch_delivery_state", Context.MODE_PRIVATE)
        val nowLocal = LocalDateTime.now()
        val lastDeliveryMs = prefs.getLong("last_delivery", 0L)
        val lastDelivery = if (lastDeliveryMs == 0L) LocalDateTime.MIN
                           else Instant.ofEpochMilli(lastDeliveryMs)
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
            val excluded = rules.values
                .filter { it.deliveryMode != DeliveryMode.DAYPART }
                .map { it.packageName }
                .toSet()
            BatchDeliveryHelper.deliverDaypart(applicationContext, excluded)
            prefs.edit().putLong("last_delivery", nowMs).apply()
        }

        return Result.success()
    }
}
