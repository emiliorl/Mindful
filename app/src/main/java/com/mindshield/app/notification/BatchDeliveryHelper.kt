package com.mindshield.app.notification

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mindshield.app.MindShieldApp
import com.mindshield.app.R
import com.mindshield.app.data.AppDatabase
import com.mindshield.app.data.HeldNotification

object BatchDeliveryHelper {

    private const val CHILD_ID_BASE   = 10_000
    private const val SUMMARY_ID_BASE = 20_000
    private const val KEEP_DELIVERED_MS = 24 * 3_600_000L  // show inbox items for 24 h

    @Suppress("MissingPermission")
    suspend fun deliverAll(context: Context) {
        val dao    = AppDatabase.get(context).heldNotificationDao()
        val queued = dao.getQueuedSync()
        if (queued.isEmpty()) return

        post(context, queued)
        val now = System.currentTimeMillis()
        dao.markAllDelivered(now)
        dao.deleteOldDelivered(now - KEEP_DELIVERED_MS)
    }

    @Suppress("MissingPermission")
    suspend fun deliverForPackage(context: Context, pkg: String) {
        val dao    = AppDatabase.get(context).heldNotificationDao()
        val queued = dao.getQueuedForPackageSync(pkg)
        if (queued.isEmpty()) return

        post(context, queued)
        val now = System.currentTimeMillis()
        dao.markPackageDelivered(pkg, now)
        dao.deleteOldDelivered(now - KEEP_DELIVERED_MS)
    }

    private fun post(context: Context, notifications: List<HeldNotification>) {
        val nm = NotificationManagerCompat.from(context)
        notifications.groupBy { it.packageName }.forEach { (pkg, group) ->
            val groupKey = "batch_$pkg"
            group.forEach { n ->
                val notif = NotificationCompat.Builder(context, MindShieldApp.CHANNEL_BATCH)
                    .setSmallIcon(R.drawable.ic_mindshield_notification)
                    .setContentTitle("[${n.appLabel}] ${n.title}")
                    .setContentText(n.text)
                    .setWhen(n.postedAtMs)
                    .setShowWhen(true)
                    .setGroup(groupKey)
                    .setAutoCancel(true)
                    .build()
                nm.notify(CHILD_ID_BASE + n.id, notif)
            }
            val count = group.size
            val summary = NotificationCompat.Builder(context, MindShieldApp.CHANNEL_BATCH)
                .setSmallIcon(R.drawable.ic_mindshield_notification)
                .setContentTitle(group.first().appLabel)
                .setContentText("$count held notification${if (count == 1) "" else "s"}")
                .setGroup(groupKey)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .build()
            nm.notify(SUMMARY_ID_BASE + (pkg.hashCode() and 0x7FFF), summary)
        }
    }
}
