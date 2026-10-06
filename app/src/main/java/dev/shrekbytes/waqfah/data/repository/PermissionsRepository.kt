package dev.shrekbytes.waqfah.data.repository

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

    // No REQUEST_IGNORE_BATTERY_OPTIMIZATIONS permission is declared, so
    // there is no direct request dialog: this deep-links to Waqfah's own
    // system page, where Battery -> Unrestricted grants (or revokes) the
    // exemption manually.
    fun batterySettingsIntent(): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())

    // Some ROMs block background activity starts behind a permission of their
    // own, which lives in the vendor settings app rather than in Settings.
    // Whether that screen exists is the only signal available: the op that
    // would report the permission's state is vendor-private and not stable
    // across builds, so the row is offered by capability and never claims a
    // state it cannot read (ADR-0008).
    fun vendorBackgroundStartSettingsIntent(): Intent = Intent(MIUI_PERM_EDITOR_ACTION).apply {
        setClassName(MIUI_SECURITY_CENTER, MIUI_PERMISSION_EDITOR)
        putExtra(MIUI_EXTRA_PKGNAME, context.packageName)
    }

    // The capability probe. Package visibility filtering hides the vendor app
    // from resolveActivity unless it is declared in the manifest's <queries>,
    // which is why that declaration is load-bearing rather than decorative.
    fun hasVendorBackgroundStartScreen(): Boolean =
        vendorBackgroundStartSettingsIntent().resolveActivity(context.packageManager) != null

    private companion object {
        // MIUI / HyperOS: the per-app permission editor, the screen that carries
        // "Open new windows while running in the background". Unverified on a
        // real device — the first thing to confirm on a HyperOS phone. If the
        // component is wrong the intent simply does not resolve, so no row is
        // shown: the failure is silent rather than misleading.
        const val MIUI_SECURITY_CENTER = "com.miui.securitycenter"
        const val MIUI_PERMISSION_EDITOR = "com.miui.permcenter.permissions.PermissionsEditorActivity"
        const val MIUI_PERM_EDITOR_ACTION = "miui.intent.action.APP_PERM_EDITOR"
        const val MIUI_EXTRA_PKGNAME = "extra_pkgname"
    }
}
