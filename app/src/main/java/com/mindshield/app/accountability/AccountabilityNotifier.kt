package com.mindshield.app.accountability

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mindshield.app.MainActivity
import com.mindshield.app.MindShieldApp
import com.mindshield.app.R

/**
 * Posts (and replaces, never stacks) the single "ready to share" notification
 * that points the user at their pending Accountability Partner summary.
 */
object AccountabilityNotifier {

    private const val NOTIFICATION_ID = 9001

    fun postPendingShareNotification(context: Context, label: String) {
        val tapIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, MindShieldApp.CHANNEL_ACCOUNTABILITY)
            .setSmallIcon(R.drawable.ic_mindshield_notification)
            .setContentTitle("MindShield")
            .setContentText("Ready to share: $label")
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}
