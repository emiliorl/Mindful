package com.mindshield.app.viewmodel

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mindshield.app.data.AppDatabase
import com.mindshield.app.data.AppFrictionConfig
import com.mindshield.app.data.AppFrictionStore
import com.mindshield.app.data.BatchCategory
import com.mindshield.app.data.BatchRule
import com.mindshield.app.data.BatchRuleStore
import com.mindshield.app.data.ChannelRule
import com.mindshield.app.data.DeliveryMode
import com.mindshield.app.data.FrictionMode
import com.mindshield.app.data.GlobalBatchSettings
import com.mindshield.app.data.IntentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime

/** An installed launchable app joined with its live friction config and batch rule. */
data class ShieldApp(
    val packageName: String,
    val label: String,
    val suggested: Boolean,
    val friction: AppFrictionConfig,
    val rule: BatchRule?
) {
    val notifyMode: NotifyMode get() = notifyModeOf(rule)
    val isProtected: Boolean get() = isProtected(friction, rule)
}

private data class BaseApp(val packageName: String, val label: String, val suggested: Boolean)

class ShieldViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.get(app).heldNotificationDao()

    private val baseApps = MutableStateFlow<List<BaseApp>>(emptyList())

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _filter = MutableStateFlow(ShieldFilter.ALL)
    val filter: StateFlow<ShieldFilter> = _filter

    /** Every launchable app with live friction + rule state (unfiltered, sorted by label). */
    private val allApps: StateFlow<List<ShieldApp>> = combine(
        baseApps, AppFrictionStore.configs, BatchRuleStore.configs
    ) { base, frictions, rules ->
        base.map {
            ShieldApp(
                packageName = it.packageName,
                label       = it.label,
                suggested   = it.suggested,
                friction    = frictions[it.packageName] ?: AppFrictionConfig(),
                rule        = rules[it.packageName]
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val visibleApps: StateFlow<List<ShieldApp>> = combine(
        allApps, _searchQuery, _filter
    ) { apps, query, filter ->
        apps.filter { matchesQuery(it.label, query) && matchesFilter(filter, it.suggested, it.friction, it.rule) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val protectedApps: StateFlow<List<ShieldApp>> = visibleApps
        .map { apps -> apps.filter { it.isProtected }.sortedBy { it.label.lowercase() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Empty when filter == PROTECTED. */
    val otherApps: StateFlow<List<ShieldApp>> = combine(visibleApps, _filter) { apps, filter ->
        if (filter == ShieldFilter.PROTECTED) emptyList()
        else apps.filter { !it.isProtected }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pauseDuration: StateFlow<Int> = AppFrictionStore.pauseDuration
    val globalSettings: StateFlow<GlobalBatchSettings> = BatchRuleStore.globalSettings
    val knownChannels: StateFlow<Map<String, Map<String, String>>> = BatchRuleStore.knownChannels

    /** Count only; the live queue contents are intentionally never exposed. */
    val queuedCount: StateFlow<Int> = dao.countQueued()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            baseApps.value = loadUserApps()
            _isLoading.value = false
        }
    }

    /** Live state for one package (ignores search/filter); null until apps load or if not installed. */
    fun app(pkg: String): StateFlow<ShieldApp?> =
        allApps.map { list -> list.firstOrNull { it.packageName == pkg } }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                allApps.value.firstOrNull { it.packageName == pkg }
            )

    // ── Search / filter ───────────────────────────────────────────────────────

    fun onSearchQueryChanged(query: String) { _searchQuery.value = query }
    fun setFilter(filter: ShieldFilter) { _filter.value = filter }

    // ── Friction ──────────────────────────────────────────────────────────────

    fun setFrictionMode(pkg: String, mode: FrictionMode) =
        AppFrictionStore.setMode(getApplication(), pkg, mode)

    fun setSmartIntent(pkg: String, type: IntentType, enabled: Boolean) =
        AppFrictionStore.setSmartIntent(getApplication(), pkg, type, enabled)

    /** Row switch: OFF <-> ALWAYS, or SMART when smart intents already exist. */
    fun toggleFriction(pkg: String, on: Boolean) {
        val mode = frictionAfterToggle(AppFrictionStore.getConfig(pkg), on)
        AppFrictionStore.setMode(getApplication(), pkg, mode)
    }

    fun setPauseDuration(seconds: Int) =
        AppFrictionStore.setPauseDuration(getApplication(), seconds)

    // ── Notifications ─────────────────────────────────────────────────────────

    /** DEFAULT removes the rule; INSTANT/BATCHED create or update it, preserving all other fields. */
    fun setNotifyMode(pkg: String, label: String, mode: NotifyMode) {
        val ctx = getApplication<Application>()
        when (mode) {
            NotifyMode.DEFAULT -> BatchRuleStore.removeRule(ctx, pkg)
            NotifyMode.INSTANT, NotifyMode.BATCHED -> {
                val category =
                    if (mode == NotifyMode.INSTANT) BatchCategory.INSTANT else BatchCategory.BATCHED
                val existing = BatchRuleStore.configs.value[pkg]
                BatchRuleStore.setRule(ctx, existing?.copy(category = category) ?: BatchRule(pkg, label, category))
            }
        }
    }

    private fun updateRule(pkg: String, transform: (BatchRule) -> BatchRule) {
        val existing = BatchRuleStore.configs.value[pkg] ?: return
        BatchRuleStore.setRule(getApplication(), transform(existing))
    }

    fun setDeliveryMode(pkg: String, mode: DeliveryMode) = updateRule(pkg) { it.copy(deliveryMode = mode) }
    fun setInterval(pkg: String, hours: Int) = updateRule(pkg) { it.copy(intervalHours = hours) }
    fun setCountThreshold(pkg: String, count: Int) = updateRule(pkg) { it.copy(countThreshold = count) }

    fun setChannelOverride(pkg: String, channelRule: ChannelRule) =
        BatchRuleStore.setChannelOverride(getApplication(), pkg, channelRule)

    fun clearChannelOverride(pkg: String, channelId: String) =
        BatchRuleStore.clearChannelOverride(getApplication(), pkg, channelId)

    // ── Global delivery schedule ──────────────────────────────────────────────

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

    // ── App loading ───────────────────────────────────────────────────────────

    private fun loadUserApps(): List<BaseApp> {
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
                BaseApp(
                    packageName = info.packageName,
                    label       = pm.getApplicationLabel(info).toString(),
                    suggested   = isSuggestedCategory(info.category)
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
}
