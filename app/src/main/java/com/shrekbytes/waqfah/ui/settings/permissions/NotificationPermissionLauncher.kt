package com.shrekbytes.waqfah.ui.settings.permissions

import android.Manifest
import android.app.Activity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat

// Runtime request for the OPTIONAL notification permission, shared by the
// onboarding permissions step and the Settings permissions screen — denying
// it never blocks onboarding, it just keeps the silent monitor notification
// hidden on Android 13+.
@Composable
fun rememberNotificationPermissionLauncher(
    viewModel: PermissionsViewModel,
): ManagedActivityResultLauncher<String, Boolean> {
    val context = LocalContext.current
    return rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onNotificationRequestResult(
            granted = granted,
            canAskAgain = ActivityCompat.shouldShowRequestPermissionRationale(
                context as Activity,
                Manifest.permission.POST_NOTIFICATIONS,
            ),
        )
    }
}
