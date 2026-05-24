package com.mindshield.app.viewmodel

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mindshield.app.data.FrictionBlocklist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AppEntry(
    val packageName: String,
    val label: String,
    val isFrictionEnabled: Boolean
)

class AppsViewModel(app: Application) : AndroidViewModel(app) {

    private val pm: PackageManager = app.packageManager

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _allApps = MutableStateFlow<List<AppEntry>>(emptyList())

    val apps: StateFlow<List<AppEntry>> = combine(
        _allApps,
        FrictionBlocklist.blockedPackages,
        _searchQuery
    ) { all, blocked, query ->
        all.map { it.copy(isFrictionEnabled = it.packageName in blocked) }
            .filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _allApps.value = loadUserApps()
        }
    }

    fun setFriction(packageName: String, enabled: Boolean) {
        FrictionBlocklist.setBlocked(getApplication(), packageName, enabled)
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    private fun loadUserApps(): List<AppEntry> {
        val launchable = pm.queryIntentActivities(
            android.content.Intent(android.content.Intent.ACTION_MAIN).also {
                it.addCategory(android.content.Intent.CATEGORY_LAUNCHER)
            },
            PackageManager.GET_META_DATA
        )
        return launchable
            .map { it.activityInfo.applicationInfo }
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .distinctBy { it.packageName }
            .map { info ->
                AppEntry(
                    packageName = info.packageName,
                    label = pm.getApplicationLabel(info).toString(),
                    isFrictionEnabled = false
                )
            }
            .sortedBy { it.label.lowercase() }
    }
}
