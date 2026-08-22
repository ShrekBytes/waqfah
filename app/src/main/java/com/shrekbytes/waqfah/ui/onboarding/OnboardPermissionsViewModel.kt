package com.shrekbytes.waqfah.ui.onboarding

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shrekbytes.waqfah.data.repository.PermissionsRepository
import com.shrekbytes.waqfah.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardPermissionsUiState(
    val accessibilityEnabled: Boolean = false,
    val batteryExempted: Boolean = false,
)

@HiltViewModel
class OnboardPermissionsViewModel @Inject constructor(
    private val permissionsRepository: PermissionsRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardPermissionsUiState())
    val uiState: StateFlow<OnboardPermissionsUiState> = _uiState.asStateFlow()

    init { refresh() }

    // Each permission has its own Grant button (see OnboardPermissionsScreen) —
    // no more forcing accessibility-then-battery in a fixed sequence. Status
    // can only change while the user is away in system settings, so the
    // screen re-checks this on every resume.
    fun refresh() {
        _uiState.value = OnboardPermissionsUiState(
            accessibilityEnabled = permissionsRepository.isAccessibilityServiceEnabled(),
            batteryExempted = permissionsRepository.isIgnoringBatteryOptimizations(),
        )
    }

    fun accessibilitySettingsIntent(): Intent = permissionsRepository.accessibilitySettingsIntent()
    fun batteryOptimizationRequestIntent(): Intent = permissionsRepository.batteryOptimizationRequestIntent()

    // Gated in the UI (see OnboardPermissionsScreen's `allGranted`) — this
    // just records completion once both permissions are actually granted.
    fun completeOnboarding() = viewModelScope.launch {
        settingsRepository.setOnboardingComplete(true)
    }
}
