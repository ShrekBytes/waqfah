package com.shrekbytes.waqfah.ui.settings.permissions

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

data class PermissionsUiState(
    val usageAccessGranted: Boolean = false,
    val overlayGranted: Boolean = false,
    val batteryExempted: Boolean = false,
)

// Shared by the onboarding permissions step and the Settings permissions
// screen — each navigation entry gets its own instance.
@HiltViewModel
class PermissionsViewModel @Inject constructor(
    private val permissionsRepository: PermissionsRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PermissionsUiState())
    val uiState: StateFlow<PermissionsUiState> = _uiState.asStateFlow()

    init { refresh() }

    // Status can only change while the user is away in system settings, so the
    // screen calls this again on every resume (see PermissionsScreen).
    fun refresh() {
        _uiState.value = PermissionsUiState(
            usageAccessGranted = permissionsRepository.hasUsageAccess(),
            overlayGranted = permissionsRepository.canDrawOverlays(),
            batteryExempted = permissionsRepository.isIgnoringBatteryOptimizations(),
        )
    }

    fun usageAccessSettingsIntent(): Intent = permissionsRepository.usageAccessSettingsIntent()
    fun overlaySettingsIntent(): Intent = permissionsRepository.overlaySettingsIntent()
    fun batteryOptimizationRequestIntent(): Intent = permissionsRepository.batteryOptimizationRequestIntent()

    // Onboarding-only: Gated in the UI (see OnboardPermissionsScreen's
    // allGranted) — records completion once all permissions are actually granted.
    fun completeOnboarding() = viewModelScope.launch {
        settingsRepository.setOnboardingComplete(true)
    }
}
