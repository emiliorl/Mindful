package com.mindshield.app.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.mindshield.app.data.AppDatabase
import com.mindshield.app.data.BatchCategory
import com.mindshield.app.data.BatchRuleStore
import com.mindshield.app.data.HeldNotification
import com.mindshield.app.data.humanizeChannelId
import com.mindshield.app.service.ZoneManagerService
import kotlinx.coroutines.*

class MindShieldNotificationService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        super.onListenerConnected()
        BatchRuleStore.init(applicationContext)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName == packageName) return

        val rule      = BatchRuleStore.configs.value[sbn.packageName]
        val settings  = BatchRuleStore.globalSettings.value
        val channelId = sbn.notification?.channelId ?: ""

        // Channel-first lookup, then app-level rule, then session fallback
        val shouldBatch: Boolean = when {
            rule?.channelOverrides?.containsKey(channelId) == true ->
                rule.channelOverrides[channelId]!!.category == BatchCategory.BATCHED
            rule?.category == BatchCategory.INSTANT -> false
            rule?.category == BatchCategory.BATCHED -> true
            ZoneManagerService.sessionState.value != null && settings.batchDuringSession -> true
            else -> false
        }

        if (!shouldBatch) return

        // Discover and cache the channel name for display in the UI
        if (channelId.isNotEmpty() && rule != null) {
            val channelName = resolveChannelName(sbn.packageName, channelId)
            scope.launch {
                BatchRuleStore.recordChannel(applicationContext, sbn.packageName, channelId, channelName)
            }
        }

        cancelNotification(sbn.key)

        val appLabel = rule?.appLabel ?: resolveAppLabel(sbn.packageName)
        val extras   = sbn.notification?.extras
        val title    = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text     = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()

        scope.launch {
            AppDatabase.get(applicationContext).heldNotificationDao().insert(
                HeldNotification(
                    packageName = sbn.packageName,
                    appLabel    = appLabel,
                    title       = title,
                    text        = text,
                    postedAtMs  = sbn.postTime
                )
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Best-effort channel name resolution.
     * Tries the NotificationManager API (works on the calling app's channels),
     * then falls back to humanizing the channel ID.
     */
    private fun resolveChannelName(pkg: String, channelId: String): String {
        // Try via INotificationManager binder (works on many devices/versions)
        return try {
            val nm = getSystemService(android.app.NotificationManager::class.java)
            val svcField = android.app.NotificationManager::class.java.getDeclaredField("sService")
            svcField.isAccessible = true
            val iNm = svcField.get(null) ?: return humanizeChannelId(channelId)

            val uid = applicationContext.packageManager.getPackageUid(pkg, 0)
            // Signature varies by API: try the most common overload first
            val channel = runCatching {
                val m = iNm.javaClass.getMethod(
                    "getNotificationChannel", String::class.java, Int::class.java, String::class.java
                )
                m.invoke(iNm, pkg, uid, channelId)
            }.getOrNull() ?: runCatching {
                val m = iNm.javaClass.getMethod(
                    "getNotificationChannel", String::class.java, String::class.java
                )
                m.invoke(iNm, pkg, channelId)
            }.getOrNull()

            channel?.javaClass?.getMethod("getName")?.invoke(channel)?.toString()
                ?.takeIf { it.isNotBlank() }
                ?: humanizeChannelId(channelId)
        } catch (e: Exception) {
            humanizeChannelId(channelId)
        }
    }

    private fun resolveAppLabel(pkg: String): String =
        runCatching {
            val info = applicationContext.packageManager.getApplicationInfo(pkg, 0)
            applicationContext.packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(pkg)
}
