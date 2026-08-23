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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shrekbytes.waqfah.data.model.PermissionCatalog
import com.shrekbytes.waqfah.ui.components.OnboardPermissionRow
import com.shrekbytes.waqfah.ui.components.WaqfahPrimaryButton
import com.shrekbytes.waqfah.ui.theme.WaqfahTheme

@Composable
fun OnboardPermissionsScreen(
    viewModel: OnboardPermissionsViewModel = hiltViewModel(),
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
        title = "A few permissions to\nmake this work",
        onBack = onBack,
        bottomContent = {
            WaqfahPrimaryButton(
                text = "Continue",
                enabled = allGranted,
                onClick = {
                    viewModel.completeOnboarding()
                    onComplete()
                },
            )
        },
    ) {
        Text(
            "Android needs these so Waqfah can show the reading screen at the right moment and keep working reliably. None of them let it read screen content or send anything off your device. Grant all three to continue.",
            color = colors.inkMuted,
            fontSize = 14.sp,
            lineHeight = 21.sp,
        )
        Text(
            "Why Waqfah needs these?",
            color = colors.accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp).clickable(onClick = onOpenRationale),
        )
        Spacer(Modifier.height(6.dp))
        val (usage, overlay, battery) = PermissionCatalog.all
        OnboardPermissionRow(
            title = usage.name,
            subtitle = usage.description,
            granted = state.usageAccessGranted,
            onOpenSettings = { context.startActivity(viewModel.usageAccessSettingsIntent()) },
        )
        OnboardPermissionRow(
            title = overlay.name,
            subtitle = overlay.description,
            granted = state.overlayGranted,
            onOpenSettings = { context.startActivity(viewModel.overlaySettingsIntent()) },
        )
        OnboardPermissionRow(
            title = battery.name,
            subtitle = battery.description,
            granted = state.batteryExempted,
            onOpenSettings = { context.startActivity(viewModel.batteryOptimizationRequestIntent()) },
        )
    }
}
