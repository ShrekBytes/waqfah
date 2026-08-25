package com.shrekbytes.waqfah.ui.settings.permissions

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shrekbytes.waqfah.data.model.PermissionKey
import com.shrekbytes.waqfah.data.repository.PermissionsRepository
import com.shrekbytes.waqfah.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PermissionsUiState(
    val usageAccessGranted: Boolean = false,
    val overlayGranted: Boolean = false,
    val batteryExempted: Boolean = false,
    // Optional row: only controls whether the monitor notification is visible
    // on Android 13+. Never part of the allGranted gate.
    val notificationsGranted: Boolean = false,
    // Set after a "Don't ask again" denial — the system launcher then silently
    // no-ops, so taps route to the app's notification settings page instead.
    val notificationsPermanentlyDenied: Boolean = false,
) {
    fun isGranted(key: PermissionKey): Boolean = when (key) {
        PermissionKey.USAGE_ACCESS -> usageAccessGranted
        PermissionKey.OVERLAY -> overlayGranted
        PermissionKey.BATTERY -> batteryExempted
        PermissionKey.NOTIFICATIONS -> notificationsGranted
    }
}

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
            notificationsGranted = permissionsRepository.hasNotificationPermission(),
            notificationsPermanentlyDenied = _uiState.value.notificationsPermanentlyDenied,
        )
    }

    // Called from the screens' request-permission launcher. [canAskAgain]
    // mirrors ActivityCompat.shouldShowRequestPermissionRationale: false right
    // after a denial means the user picked "Don't ask again".
    fun onNotificationRequestResult(granted: Boolean, canAskAgain: Boolean) {
        _uiState.update {
            it.copy(
                notificationsGranted = permissionsRepository.hasNotificationPermission(),
                notificationsPermanentlyDenied = !granted && !canAskAgain,
            )
        }
    }

    // One lookup per catalog entry — screens iterate PermissionCatalog.all and
    // resolve state/intents by stable key instead of hardcoding each row.
    fun settingsIntentFor(key: PermissionKey): Intent = when (key) {
        PermissionKey.USAGE_ACCESS -> permissionsRepository.usageAccessSettingsIntent()
        PermissionKey.OVERLAY -> permissionsRepository.overlaySettingsIntent()
        PermissionKey.BATTERY -> permissionsRepository.batteryOptimizationRequestIntent()
        // NOTIFICATIONS is a runtime permission (see the screens' launcher);
        // this settings deep-link is only its "Don't ask again" fallback.
        else -> permissionsRepository.notificationSettingsIntent()
    }

    fun notificationSettingsIntent(): Intent = permissionsRepository.notificationSettingsIntent()

    // Onboarding-only: Gated in the UI (see OnboardPermissionsScreen's
    // allGranted) — records completion once all permissions are actually granted.
    fun completeOnboarding() = viewModelScope.launch {
        settingsRepository.setOnboardingComplete(true)
    }
}
