package com.mindshield.app.accountability

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mindshield.app.data.AccountabilityPartnerStore
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.WeekFields

/**
 * Daily self-checking tick (same pattern as [com.mindshield.app.routines.RoutineScheduler] and
 * [com.mindshield.app.notification.BatchDeliveryWorker]): runs once a day and only builds/sends
 * the weekly Accountability Partner digest when today matches the configured day/time and this
 * week's digest hasn't already been sent.
 */
class AccountabilityDigestWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        if (!AccountabilityPartnerStore.enabled.value || !AccountabilityPartnerStore.triggerWeeklyDigest.value) {
            return Result.success()
        }

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)

        val weekFields = WeekFields.ISO
        val weekYear = today.get(weekFields.weekBasedYear())
        val weekNumber = today.get(weekFields.weekOfWeekBasedYear())
        val weekKey = "%d-W%02d".format(weekYear, weekNumber)

        if (AccountabilityPartnerStore.lastDigestWeekKey.value == weekKey) return Result.success()
        if (today.dayOfWeek != AccountabilityPartnerStore.digestDayOfWeek.value) return Result.success()
        if (LocalTime.now(zone).hour < AccountabilityPartnerStore.digestHour.value) return Result.success()

        val weekStartMs = today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
        val weekEndMs = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val summary = AccountabilitySummaryBuilder.buildWeeklySummary(applicationContext, weekStartMs, weekEndMs)
        AccountabilityPartnerStore.setPendingShare(applicationContext, "Weekly summary", summary)
        AccountabilityPartnerStore.markDigestSent(applicationContext, weekKey)
        AccountabilityNotifier.postPendingShareNotification(applicationContext, "Weekly summary")

        return Result.success()
    }
}
