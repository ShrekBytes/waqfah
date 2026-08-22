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
    val accessibilityEnabled: Boolean = false,
    val batteryExempted: Boolean = false,
)

@HiltViewModel
class PermissionsViewModel @Inject constructor(
    private val permissionsRepository: PermissionsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PermissionsUiState())
    val uiState: StateFlow<PermissionsUiState> = _uiState.asStateFlow()

    init { refresh() }

    // These are real system permissions, not something the app can flip on its
    // own — the screen can only report status and hand off to system settings,
    // so call this again whenever the screen resumes (see PermissionsScreen).
    fun refresh() {
        _uiState.value = PermissionsUiState(
            accessibilityEnabled = permissionsRepository.isAccessibilityServiceEnabled(),
            batteryExempted = permissionsRepository.isIgnoringBatteryOptimizations(),
        )
    }

    fun accessibilitySettingsIntent(): Intent = permissionsRepository.accessibilitySettingsIntent()
    fun batteryOptimizationRequestIntent(): Intent = permissionsRepository.batteryOptimizationRequestIntent()
}
