package com.shrekbytes.waqfah.ui.settings.permissions

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shrekbytes.waqfah.data.model.PermissionCatalog
import com.shrekbytes.waqfah.ui.components.PermissionRow
import com.shrekbytes.waqfah.ui.components.SettingsScaffold
import com.shrekbytes.waqfah.ui.theme.WaqfahTheme

@Composable
fun PermissionsScreen(viewModel: PermissionsViewModel = hiltViewModel(), onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colors = WaqfahTheme.colors

    // Permission state can only change while the user is away in system
    // settings, so re-check whenever this screen comes back into view.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    SettingsScaffold(title = "Permissions", onBack = onBack) {
        Text(
            "Waqfah needs these to notice app launches and keep running. Review or change them anytime.",
            color = colors.inkMuted,
            fontSize = 14.sp,
            lineHeight = 21.sp,
        )
        Spacer(Modifier.height(6.dp))
        val (accessibility, battery) = PermissionCatalog.all
        PermissionRow(
            title = accessibility.name,
            subtitle = accessibility.description,
            granted = state.accessibilityEnabled,
            onOpenSettings = { context.startActivity(viewModel.accessibilitySettingsIntent()) },
        )
        PermissionRow(
            title = battery.name,
            subtitle = battery.description,
            granted = state.batteryExempted,
            onOpenSettings = { context.startActivity(viewModel.batteryOptimizationRequestIntent()) },
        )
    }
}
