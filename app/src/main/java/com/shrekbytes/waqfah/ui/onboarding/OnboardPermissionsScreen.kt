package com.shrekbytes.waqfah.ui.onboarding

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
import com.shrekbytes.waqfah.ui.components.OnboardPermissionRow
import com.shrekbytes.waqfah.ui.components.WaqfahPrimaryButton
import com.shrekbytes.waqfah.ui.settings.permissions.PermissionsViewModel
import com.shrekbytes.waqfah.ui.theme.WaqfahTheme

@Composable
fun OnboardPermissionsScreen(
    viewModel: PermissionsViewModel = hiltViewModel(),
    onOpenRationale: () -> Unit = {},
    onBack: () -> Unit,
    onComplete: () -> Unit,
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = WaqfahTheme.colors
    val allGranted = state.usageAccessGranted && state.overlayGranted && state.batteryExempted

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    OnboardingScaffold(
        step = 3,
        title = stringResource(R.string.onboard_perms_title),
        onBack = onBack,
        bottomContent = {
            WaqfahPrimaryButton(
                text = stringResource(R.string.continue_btn),
                enabled = allGranted,
                onClick = {
                    viewModel.completeOnboarding()
                    onComplete()
                },
            )
        },
    ) {
        Text(
            stringResource(R.string.onboard_perms_body),
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
        PermissionCatalog.all.forEach { info ->
            OnboardPermissionRow(
                title = stringResource(info.nameRes),
                subtitle = stringResource(info.descriptionRes),
                granted = state.isGranted(info.key),
                onOpenSettings = { context.startActivity(viewModel.settingsIntentFor(info.key)) },
            )
        }
    }
}
