package com.mindshield.app.viewmodel

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mindshield.app.data.*
import com.mindshield.app.notification.BatchDeliveryHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalTime

/** A single delivered batch: timestamp → notifications within it. */
data class DeliveredBatch(val deliveredAtMs: Long, val notifications: List<HeldNotification>)

class NotificationsViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.get(app).heldNotificationDao()

    /**
     * Past deliveries grouped into batches, newest first.
     * The live queue is intentionally never exposed — users should not
     * be able to see what's being held right now.
     */
    val batches: StateFlow<List<DeliveredBatch>> = dao.getDelivered()
        .map { items ->
            items
                .groupBy { it.deliveredAtMs ?: 0L }
                .entries
                .sortedByDescending { it.key }
                .map { (ts, list) -> DeliveredBatch(ts, list) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val rules: StateFlow<Map<String, BatchRule>> = BatchRuleStore.configs

    val globalSettings: StateFlow<GlobalBatchSettings> = BatchRuleStore.globalSettings

    /** packageName → (channelId → channelName) discovered from received notifications. */
    val knownChannels: StateFlow<Map<String, Map<String, String>>> = BatchRuleStore.knownChannels

    private val _installedApps = MutableStateFlow<List<AppEntry>>(emptyList())
    val installedApps: StateFlow<List<AppEntry>> = _installedApps

    init {
        viewModelScope.launch(Dispatchers.IO) { _installedApps.value = loadUserApps() }
    }

    fun deliverNow() {
        viewModelScope.launch(Dispatchers.IO) { BatchDeliveryHelper.deliverAll(getApplication()) }
    }

    fun setRule(rule: BatchRule) = BatchRuleStore.setRule(getApplication(), rule)

    fun removeRule(pkg: String) = BatchRuleStore.removeRule(getApplication(), pkg)

    fun setChannelOverride(pkg: String, channelRule: com.mindshield.app.data.ChannelRule) =
        BatchRuleStore.setChannelOverride(getApplication(), pkg, channelRule)

    fun clearChannelOverride(pkg: String, channelId: String) =
        BatchRuleStore.clearChannelOverride(getApplication(), pkg, channelId)

    fun setDeliveryTimes(times: List<LocalTime>) {
        BatchRuleStore.setGlobalSettings(
            getApplication(),
            globalSettings.value.copy(deliveryTimes = times.sortedBy { it.hour * 60 + it.minute })
        )
    }

    fun setBatchDuringSession(enabled: Boolean) {
        BatchRuleStore.setGlobalSettings(
            getApplication(),
            globalSettings.value.copy(batchDuringSession = enabled)
        )
    }

    private fun loadUserApps(): List<AppEntry> {
        val pm = getApplication<Application>().packageManager
        @Suppress("DEPRECATION")
        val launchable = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
            PackageManager.GET_META_DATA or PackageManager.MATCH_ALL
        )
        return launchable
            .mapNotNull { ri ->
                val info = ri.activityInfo?.applicationInfo ?: return@mapNotNull null
                if (info.packageName == "com.mindshield.app") return@mapNotNull null
                AppEntry(
                    packageName  = info.packageName,
                    label        = pm.getApplicationLabel(info).toString(),
                    frictionMode = FrictionMode.OFF,
                    smartIntents = emptySet()
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
}
