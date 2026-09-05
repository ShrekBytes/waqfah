package com.shrekbytes.waqfah.ui.tour

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shrekbytes.waqfah.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

// The tour machine's Android adapter: it hosts TourSession in the ViewModel's
// own scope — so the machine survives rotation and the navigation pushes that
// dispose MainScreen mid-tour (the browse-translations deep-link) — and adapts
// persistence to the session's single finish probe. Skipping never reaches
// here: skip persists nothing (ADR-0003).
@HiltViewModel
class FeatureTourViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val session = TourSession(
        tasks = TOUR_STEP_TASKS,
        hasCompletedTour = settingsRepository.loadedPreferences.map { it?.hasCompletedFeatureTour },
        onFinished = {
            // Persisted: never auto-shows again after finishing once.
            viewModelScope.launch { settingsRepository.setFeatureTourComplete(true) }
        },
        scope = viewModelScope,
    )
}
