package com.shrekbytes.waqfah.ui.settings.permissions

import androidx.compose.foundation.clickable
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
import com.shrekbytes.waqfah.data.model.PermissionKey
import com.shrekbytes.waqfah.ui.components.PermissionToggleRow
import com.shrekbytes.waqfah.ui.components.SectionTitle
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

    // The three required rows above are settings-page grants and never go
    // through this — only the optional notification row below does.
    val notifLauncher = rememberNotificationPermissionLauncher(viewModel)

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
        // Two labeled groups so users see at a glance what's indispensable
        // versus recommended (SectionTitle carries the spacing itself).
        SectionTitle(stringResource(R.string.perm_section_required))
        PermissionCatalog.all.forEach { info ->
            PermissionToggleRow(
                title = stringResource(info.nameRes),
                subtitle = stringResource(info.descriptionRes),
                granted = state.isGranted(info.key),
                onOpenSettings = { context.startActivity(viewModel.settingsIntentFor(info.key)) },
            )
        }
        // Optional rows — recommended for reliability (battery) and visibility
        // (notifications), but neither gates anything.
        SectionTitle(stringResource(R.string.perm_section_optional))
        PermissionCatalog.recommended.forEach { info ->
            PermissionToggleRow(
                title = stringResource(info.nameRes),
                subtitle = stringResource(info.descriptionRes),
                granted = state.isGranted(info.key),
                onOpenSettings = {
                    if (info.key == PermissionKey.NOTIFICATIONS &&
                        !state.notificationsGranted &&
                        !state.notificationsPermanentlyDenied
                    ) {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        context.startActivity(viewModel.settingsIntentFor(info.key))
                    }
                },
            )
        }
    }
}
