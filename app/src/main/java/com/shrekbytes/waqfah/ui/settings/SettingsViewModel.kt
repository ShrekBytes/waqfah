package com.shrekbytes.waqfah.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shrekbytes.waqfah.data.repository.MonitoredAppsRepository
import com.shrekbytes.waqfah.data.repository.QuranRepository
import com.shrekbytes.waqfah.data.repository.ReadingProgressRepository
import com.shrekbytes.waqfah.data.repository.SettingsRepository
import com.shrekbytes.waqfah.data.model.AppLanguage
import com.shrekbytes.waqfah.ui.theme.AccentColor
import com.shrekbytes.waqfah.ui.theme.AppTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val isActive: Boolean = true,
    val theme: AppTheme = AppTheme.SYSTEM,
    val accentColor: AccentColor = AccentColor.SAGE,
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    val showAccentPicker: Boolean = true,
    val readCount: Int = 0,
    val totalCount: Int = 0,
    val progressPercent: Int = 0,
    val monitoredAppCount: Int = 0,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val readingProgressRepository: ReadingProgressRepository,
    private val monitoredAppsRepository: MonitoredAppsRepository,
    quranRepository: QuranRepository,
) : ViewModel() {

    private val totalCount = MutableStateFlow(0)

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.preferences,
        readingProgressRepository.readCount,
        monitoredAppsRepository.monitoredApps,
        totalCount,
    ) { prefs, readCount, monitored, total ->
        SettingsUiState(
            isActive = prefs.appActive,
            theme = prefs.theme,
            accentColor = prefs.accentColor,
            appLanguage = prefs.appLanguage,
            showAccentPicker = prefs.theme == AppTheme.SYSTEM || prefs.theme == AppTheme.LIGHT || prefs.theme == AppTheme.DARK,
            readCount = readCount,
            totalCount = total,
            progressPercent = if (total > 0) (readCount * 100 / total) else 0,
            monitoredAppCount = monitored.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    init {
        viewModelScope.launch { totalCount.value = quranRepository.totalVerseCount() }
    }

    // Reads the persisted value rather than uiState, whose default (active =
    // true) is wrong until DataStore's first emission arrives.
    fun toggleActive() = viewModelScope.launch {
        settingsRepository.setAppActive(!settingsRepository.preferences.first().appActive)
    }

    fun setTheme(theme: AppTheme) = viewModelScope.launch { settingsRepository.setTheme(theme) }
    fun setAccentColor(color: AccentColor) = viewModelScope.launch { settingsRepository.setAccentColor(color) }

    // Persists first, THEN invokes [onApplied] — recreating the activity before
    // the DataStore write lands makes attachBaseContext read the stale locale,
    // so a switch appeared to lag one selection behind until an app restart.
    fun setAppLanguage(language: AppLanguage, onApplied: () -> Unit) = viewModelScope.launch {
        settingsRepository.setAppLanguage(language)
        onApplied()
    }

    fun resetProgress() = viewModelScope.launch {
        readingProgressRepository.resetAll()
    }
}
