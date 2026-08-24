package com.shrekbytes.waqfah.ui.settings.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shrekbytes.waqfah.data.model.InstalledApp
import com.shrekbytes.waqfah.data.model.PreferenceLimits
import com.shrekbytes.waqfah.data.model.UserPreferences
import com.shrekbytes.waqfah.data.repository.MonitoredAppsRepository
import com.shrekbytes.waqfah.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
import javax.inject.Inject

data class AppRowState(val app: InstalledApp, val isMonitored: Boolean)

data class AppsUiState(
    // Mirrors UserPreferences' persisted default so the pre-DataStore frame
    // doesn't flash a different number.
    val cooldownMinutes: Int = UserPreferences().cooldownMinutes,
    val searchQuery: String = "",
    val apps: List<AppRowState> = emptyList(),
    // True until getInstalledLaunchableApps() completes — apps.isEmpty() alone
    // can't tell "still loading" from "no search hits".
    val isLoading: Boolean = true,
)

@HiltViewModel
class AppsViewModel @Inject constructor(
    private val monitoredAppsRepository: MonitoredAppsRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val isLoadingApps = MutableStateFlow(true)

    // queryIntentActivities + icon rendering run off the main thread inside the
    // repository. Lazily, not Eagerly: the scan only happens when a screen
    // actually collects uiState, but the result is then kept for the ViewModel's
    // lifetime instead of rescanning on every revisit.
    private val installedApps: StateFlow<List<InstalledApp>> = flow {
        emit(monitoredAppsRepository.getInstalledLaunchableApps())
        isLoadingApps.value = false
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Narrowed to cooldownMinutes only so unrelated preference changes don't
    // rebuild the whole filtered list.
    private val cooldownMinutes = settingsRepository.preferences
        .map { it.cooldownMinutes }
        .distinctUntilChanged()

    val uiState: StateFlow<AppsUiState> = combine(
        cooldownMinutes,
        monitoredAppsRepository.monitoredApps,
        searchQuery,
        installedApps,
        isLoadingApps,
    ) { cooldown, monitored, query, allApps, loading ->
        val monitoredIds = monitored.map { it.packageName }.toSet()
        AppsUiState(
            cooldownMinutes = cooldown,
            searchQuery = query,
            apps = allApps
                .filter { it.label.contains(query, ignoreCase = true) }
                .map { AppRowState(it, it.packageName in monitoredIds) },
            isLoading = loading,
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
        settingsRepository.setCooldownMinutes(
            minutes.coerceIn(PreferenceLimits.COOLDOWN_MIN_MINUTES, PreferenceLimits.COOLDOWN_MAX_MINUTES),
        )
    }
}
