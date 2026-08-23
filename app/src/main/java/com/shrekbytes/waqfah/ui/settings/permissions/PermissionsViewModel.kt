package com.shrekbytes.waqfah.ui.settings.permissions

import android.content.Intent
import androidx.lifecycle.ViewModel
import com.shrekbytes.waqfah.data.repository.PermissionsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class PermissionsUiState(
    val usageAccessGranted: Boolean = false,
    val overlayGranted: Boolean = false,
    val batteryExempted: Boolean = false,
)

@HiltViewModel
class PermissionsViewModel @Inject constructor(
    private val permissionsRepository: PermissionsRepository,
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
}
