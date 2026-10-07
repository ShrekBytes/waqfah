package dev.shrekbytes.waqfah.ui.settings.advanced

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.shrekbytes.waqfah.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdvancedUiState(
    val autoNextOnMark: Boolean = false,
)

// The refinements page's own ViewModel — the sibling sub-pages each carry one,
// and the main tab's aggregate SettingsViewModel would drag its monitor and
// progress observers along for what is here a single toggle.
@HiltViewModel
class AdvancedViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<AdvancedUiState> = settingsRepository.loadedPreferences
        .filterNotNull()
        .map { AdvancedUiState(autoNextOnMark = it.autoNextOnMark) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AdvancedUiState())

    fun setAutoNextOnMark(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setAutoNextOnMark(enabled)
    }
}
