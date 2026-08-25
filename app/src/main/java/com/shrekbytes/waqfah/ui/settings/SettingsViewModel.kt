package com.shrekbytes.waqfah.ui.settings

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shrekbytes.waqfah.data.repository.MonitoredAppsRepository
import com.shrekbytes.waqfah.data.repository.PermissionsRepository
import com.shrekbytes.waqfah.data.repository.QuranRepository
import com.shrekbytes.waqfah.data.repository.ReadingProgressRepository
import com.shrekbytes.waqfah.data.repository.SettingsRepository
import com.shrekbytes.waqfah.data.model.AppLanguage
import com.shrekbytes.waqfah.detection.AppMonitorService
import com.shrekbytes.waqfah.ui.theme.AccentColor
import com.shrekbytes.waqfah.ui.theme.AppTheme
import com.shrekbytes.waqfah.ui.theme.hasAccentPicker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val permissionsRepository: PermissionsRepository,
    @ApplicationContext private val context: Context,
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
            showAccentPicker = prefs.theme.hasAccentPicker,
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
    //
    // The foreground service's lifetime mirrors this toggle exactly: turning
    // Waqfah off tears the monitor down (notification included) instead of
    // leaving an idle service parked in memory; turning it back on restarts it
    // immediately — the user is looking at the switch, so waiting for a
    // MainActivity resume would feel broken. Without permissions there is no
    // point starting (the service would stop itself on its first check);
    // MainActivity.onResume covers the grant-later path.
    fun toggleActive() = viewModelScope.launch {
        val activate = !settingsRepository.preferences.first().appActive
        settingsRepository.setAppActive(activate)
        if (activate) {
            if (permissionsRepository.hasRequiredPermissions()) {
                AppMonitorService.start(context)
            }
        } else {
            context.stopService(Intent(context, AppMonitorService::class.java))
        }
    }

    fun setTheme(theme: AppTheme) = viewModelScope.launch { settingsRepository.setTheme(theme) }
    fun setAccentColor(color: AccentColor) = viewModelScope.launch { settingsRepository.setAccentColor(color) }

    // Persists the UI mirror, then hands the choice to AppCompatDelegate, which
    // applies it to every activity, persists it itself (autoStoreLocales), and
    // recreates the running activities — no manual recreate() needed.
    fun setAppLanguage(language: AppLanguage) = viewModelScope.launch {
        settingsRepository.setAppLanguage(language)
        val locales = when (language) {
            AppLanguage.SYSTEM -> LocaleListCompat.getEmptyLocaleList()
            AppLanguage.ENGLISH -> LocaleListCompat.forLanguageTags("en")
            AppLanguage.BENGALI -> LocaleListCompat.forLanguageTags("bn")
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    fun resetProgress() = viewModelScope.launch {
        readingProgressRepository.resetAll()
    }
}
