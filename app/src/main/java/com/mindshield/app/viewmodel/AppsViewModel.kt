package com.mindshield.app.viewmodel

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mindshield.app.data.AppFrictionStore
import com.mindshield.app.data.FrictionMode
import com.mindshield.app.data.IntentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AppEntry(
    val packageName: String,
    val label: String,
    val frictionMode: FrictionMode,
    val smartIntents: Set<IntentType>
)

class AppsViewModel(app: Application) : AndroidViewModel(app) {

    private val pm: PackageManager = app.packageManager

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _allApps = MutableStateFlow<List<AppEntry>>(emptyList())

    val apps: StateFlow<List<AppEntry>> = combine(
        _allApps,
        AppFrictionStore.configs,
        _searchQuery
    ) { all, configs, query ->
        all.map { entry ->
            val config = configs[entry.packageName]
            entry.copy(
                frictionMode = config?.mode ?: FrictionMode.OFF,
                smartIntents = config?.smartIntents ?: emptySet()
            )
        }.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _allApps.value = loadUserApps()
        }
    }

    fun setMode(packageName: String, mode: FrictionMode) {
        AppFrictionStore.setMode(getApplication(), packageName, mode)
    }

    fun setSmartIntent(packageName: String, type: IntentType, enabled: Boolean) {
        AppFrictionStore.setSmartIntent(getApplication(), packageName, type, enabled)
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    private fun loadUserApps(): List<AppEntry> {
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        }
        @Suppress("DEPRECATION")
        val launchable = pm.queryIntentActivities(
            intent,
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
