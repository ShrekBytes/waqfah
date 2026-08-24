package com.shrekbytes.waqfah.ui.settings.permissions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shrekbytes.waqfah.R
import com.shrekbytes.waqfah.data.model.PermissionCatalog
import com.shrekbytes.waqfah.ui.components.PermissionToggleRow
import com.shrekbytes.waqfah.ui.components.SettingsScaffold
import com.shrekbytes.waqfah.ui.theme.WaqfahTheme

@Composable
fun PermissionsScreen(
    viewModel: PermissionsViewModel = hiltViewModel(),
    onOpenRationale: () -> Unit = {},
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colors = WaqfahTheme.colors

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    SettingsScaffold(title = stringResource(R.string.permissions_title), onBack = onBack) {
        Text(
            stringResource(R.string.permissions_body),
            color = colors.inkMuted,
            fontSize = 14.sp,
            lineHeight = 21.sp,
        )
        Text(
            stringResource(R.string.why_permissions_link),
            color = colors.accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp).clickable(onClick = onOpenRationale),
        )
        Spacer(Modifier.height(6.dp))
        // Referenced by name, not destructured by position — see PermissionCatalog.
        val usage = PermissionCatalog.usage
        val overlay = PermissionCatalog.overlay
        val battery = PermissionCatalog.battery
        PermissionToggleRow(
            title = stringResource(usage.nameRes),
            subtitle = stringResource(usage.descriptionRes),
            granted = state.usageAccessGranted,
            onOpenSettings = { context.startActivity(viewModel.usageAccessSettingsIntent()) },
        )
        PermissionToggleRow(
            title = stringResource(overlay.nameRes),
            subtitle = stringResource(overlay.descriptionRes),
            granted = state.overlayGranted,
            onOpenSettings = { context.startActivity(viewModel.overlaySettingsIntent()) },
        )
        PermissionToggleRow(
            title = stringResource(battery.nameRes),
            subtitle = stringResource(battery.descriptionRes),
            granted = state.batteryExempted,
            onOpenSettings = { context.startActivity(viewModel.batteryOptimizationRequestIntent()) },
        )
    }
}
