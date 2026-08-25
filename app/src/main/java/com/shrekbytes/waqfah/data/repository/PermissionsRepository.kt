package com.shrekbytes.waqfah.data.repository

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PermissionsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION") // checkOpNoThrow works on every supported API level
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)

    // Auto-granted below Android 13 once declared in the manifest; from 13 on,
    // a normal runtime permission that only gates notification VISIBILITY.
    fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    // Fallback route once the system stops showing the runtime dialog ("Don't
    // ask again") — deep-links to this app's system notification page.
    fun notificationSettingsIntent(): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    fun hasRequiredPermissions(): Boolean = hasUsageAccess() && canDrawOverlays()

    fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun usageAccessSettingsIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    fun overlaySettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())

    // Once granted, requesting again is a no-op on most OEMs, so this opens the
    // app's details page instead, where Battery -> Unrestricted can be revoked.
    fun batteryOptimizationRequestIntent(): Intent =
        if (isIgnoringBatteryOptimizations()) {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
        } else {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = "package:${context.packageName}".toUri()
            }
        }
}
