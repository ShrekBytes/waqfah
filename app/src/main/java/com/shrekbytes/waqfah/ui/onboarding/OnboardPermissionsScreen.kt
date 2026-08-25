package com.shrekbytes.waqfah.ui.onboarding

import android.Manifest
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.compose.foundation.clickable
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
import com.shrekbytes.waqfah.data.model.PermissionKey
import com.shrekbytes.waqfah.ui.components.OnboardPermissionRow
import com.shrekbytes.waqfah.ui.components.SectionTitle
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
    // Battery exemption was deliberately moved out of this gate: it improves
    // reliability (aggressive OEMs kill non-exempted monitors) but monitoring
    // works without it, so refusing onboarding over it would lock users out of
    // a fully functional core for a nice-to-have.
    val allGranted = state.usageAccessGranted && state.overlayGranted

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    // Runtime request for the OPTIONAL notification permission — deliberately
    // outside allGranted below: denying it never blocks onboarding, it just
    // keeps the silent monitor notification hidden on Android 13+.
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onNotificationRequestResult(
            granted = granted,
            canAskAgain = ActivityCompat.shouldShowRequestPermissionRationale(
                context as Activity,
                Manifest.permission.POST_NOTIFICATIONS,
            ),
        )
    }

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
        // The two groups get explicit headers so users can see at a glance
        // what's indispensable versus nice-to-have (SectionTitle carries the
        // group separation spacing itself).
        SectionTitle(stringResource(R.string.perm_section_required))
        PermissionCatalog.all.forEach { info ->
            OnboardPermissionRow(
                title = stringResource(info.nameRes),
                subtitle = stringResource(info.descriptionRes),
                granted = state.isGranted(info.key),
                onOpenSettings = { context.startActivity(viewModel.settingsIntentFor(info.key)) },
            )
        }
        // Optional rows — recommended for reliability (battery) and visibility
        // (notifications) but never gating Continue. Battery routes through
        // system settings; notifications uses the runtime request above until
        // it can no longer change anything.
        SectionTitle(stringResource(R.string.perm_section_optional))
        PermissionCatalog.recommended.forEach { info ->
            OnboardPermissionRow(
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
