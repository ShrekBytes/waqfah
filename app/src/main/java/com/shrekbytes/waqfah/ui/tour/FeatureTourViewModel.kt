package com.shrekbytes.waqfah.ui.tour

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shrekbytes.waqfah.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// Owns the feature tour's persistence: the tour auto-shows on the Home tab
// until the user finishes it once. Skipping writes nothing, so it naturally
// re-offers on every future launch until completed.
@HiltViewModel
class FeatureTourViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    // null until DataStore's first emission arrives — callers treat that as
    // "don't show yet" instead of flashing the tour over unresolved state.
    // The typed null comes from the repository's shared loadedPreferences
    // seam rather than a per-VM sentinel.
    val hasCompletedTour: StateFlow<Boolean?> = settingsRepository.loadedPreferences
        .map { it?.hasCompletedFeatureTour }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun completeTour() {
        viewModelScope.launch { settingsRepository.setFeatureTourComplete(true) }
    }
}
