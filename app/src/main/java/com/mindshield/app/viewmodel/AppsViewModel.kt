package com.mindshield.app.viewmodel

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mindshield.app.data.FrictionBlocklist
import com.mindshield.app.data.IntentFrictionRules
import com.mindshield.app.data.IntentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class FrictionMode { OFF, ALWAYS, SMART }

data class AppEntry(
    val packageName: String,
    val label: String,
    val isFrictionEnabled: Boolean,
    val frictionIntents: Set<IntentType> = emptySet()
) {
    val frictionMode: FrictionMode get() = when {
        isFrictionEnabled          -> FrictionMode.ALWAYS
        frictionIntents.isNotEmpty() -> FrictionMode.SMART
        else                       -> FrictionMode.OFF
    }
}

class AppsViewModel(app: Application) : AndroidViewModel(app) {

    private val pm: PackageManager = app.packageManager

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _allApps = MutableStateFlow<List<AppEntry>>(emptyList())

    val apps: StateFlow<List<AppEntry>> = combine(
        _allApps,
        FrictionBlocklist.blockedPackages,
        IntentFrictionRules.rules,
        _searchQuery
    ) { all, blocked, rules, query ->
        all.map { entry ->
            entry.copy(
                isFrictionEnabled = entry.packageName in blocked,
                frictionIntents   = rules[entry.packageName] ?: emptySet()
            )
        }.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _allApps.value = loadUserApps()
        }
    }

    fun setMode(packageName: String, mode: FrictionMode) {
        val ctx = getApplication<Application>()
        when (mode) {
            FrictionMode.OFF -> {
                FrictionBlocklist.setBlocked(ctx, packageName, false)
                // Clear any session rules so the row goes fully quiet
                IntentType.entries.forEach { IntentFrictionRules.setRule(ctx, packageName, it, false) }
            }
            FrictionMode.ALWAYS -> {
                FrictionBlocklist.setBlocked(ctx, packageName, true)
            }
            FrictionMode.SMART -> {
                // Switch to smart — turn off permanent block, keep existing intent rules
                FrictionBlocklist.setBlocked(ctx, packageName, false)
            }
        }
    }

    fun setIntentRule(packageName: String, type: IntentType, enabled: Boolean) {
        IntentFrictionRules.setRule(getApplication(), packageName, type, enabled)
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    private fun loadUserApps(): List<AppEntry> {
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        }
        @Suppress("DEPRECATION")
        val flags = PackageManager.GET_META_DATA or PackageManager.MATCH_ALL
        val launchable = pm.queryIntentActivities(intent, flags)

        return launchable
            .mapNotNull { resolveInfo ->
                val info = resolveInfo.activityInfo?.applicationInfo ?: return@mapNotNull null
                if (info.packageName == "com.mindshield.app") return@mapNotNull null
                AppEntry(
                    packageName       = info.packageName,
                    label             = pm.getApplicationLabel(info).toString(),
                    isFrictionEnabled = false
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
}
