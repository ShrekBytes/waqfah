package com.shrekbytes.waqfah.ui.settings.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shrekbytes.waqfah.data.model.InstalledApp
import com.shrekbytes.waqfah.data.repository.MonitoredAppsRepository
import com.shrekbytes.waqfah.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class AppRowState(val app: InstalledApp, val isMonitored: Boolean)

data class AppsUiState(
    val cooldownMinutes: Int = 5,
    val searchQuery: String = "",
    val apps: List<AppRowState> = emptyList(),
)

@HiltViewModel
class AppsViewModel @Inject constructor(
    private val monitoredAppsRepository: MonitoredAppsRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")

    // PackageManager.queryIntentActivities scans every installed package, so it
    // runs once off the main thread rather than inline in the combine below.
    private val installedApps: StateFlow<List<InstalledApp>> = flow {
        emit(withContext(Dispatchers.Default) { monitoredAppsRepository.getInstalledLaunchableApps() })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Narrowed to just the one field this screen cares about — combining the
    // full UserPreferences meant an unrelated change anywhere else (theme,
    // font size, active translation…) rebuilt and reassigned this whole
    // filtered/mapped list, which is very likely what scrolling felt laggy
    // against: not the scroll itself, but list recomputation racing it.
    private val cooldownMinutes = settingsRepository.preferences
        .map { it.cooldownMinutes }
        .distinctUntilChanged()

    val uiState: StateFlow<AppsUiState> = combine(
        cooldownMinutes,
        monitoredAppsRepository.monitoredApps,
        searchQuery,
        installedApps,
    ) { cooldown, monitored, query, allApps ->
        val monitoredIds = monitored.map { it.packageName }.toSet()
        AppsUiState(
            cooldownMinutes = cooldown,
            searchQuery = query,
            apps = allApps
                .filter { it.label.contains(query, ignoreCase = true) }
                .map { AppRowState(it, it.packageName in monitoredIds) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppsUiState())

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun toggle(app: InstalledApp) = viewModelScope.launch {
        val isMonitored = monitoredAppsRepository.monitoredApps.first().any { it.packageName == app.packageName }
        if (isMonitored) monitoredAppsRepository.remove(app.packageName) else monitoredAppsRepository.add(app.packageName)
    }

    fun setCooldown(minutes: Int) = viewModelScope.launch {
        settingsRepository.setCooldownMinutes(minutes.coerceIn(0, 60))
    }
}
