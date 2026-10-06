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

    fun isXiaomiOrHyperOS(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        if (manufacturer in setOf("xiaomi", "redmi", "poco")) return true
        val miuiVersion = getSystemProperty("ro.miui.ui.version.name")
        val miuiCode = getSystemProperty("ro.miui.ui.version.code")
        return !miuiVersion.isNullOrEmpty() || !miuiCode.isNullOrEmpty()
    }

    // On Xiaomi HyperOS and MIUI, background activity starts (startActivity
    // from a background service) are silently intercepted and blocked unless
    // the proprietary OP_BACKGROUND_START_ACTIVITY permission (10021) is granted.
    fun hasBackgroundStartPermission(): Boolean {
        if (!isXiaomiOrHyperOS()) return true
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return try {
            val method = AppOpsManager::class.java.getMethod(
                "checkOpNoThrow",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java,
            )
            val mode = method.invoke(
                appOps,
                OP_BACKGROUND_START_ACTIVITY,
                Process.myUid(),
                context.packageName,
            ) as Int
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            true
        }
    }

    fun hasRequiredPermissions(): Boolean =
        hasUsageAccess() && canDrawOverlays() && hasBackgroundStartPermission()

    fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun usageAccessSettingsIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    fun overlaySettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())

    // Directs to the MIUI/HyperOS Security Center permissions editor page for
    // this package, where "Open new windows while running in the background" is toggled.
    fun xiaomiPermissionsSettingsIntent(): Intent {
        val miuiIntent = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
            setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
            putExtra("extra_pkgname", context.packageName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return if (miuiIntent.resolveActivity(context.packageManager) != null) {
            miuiIntent
        } else {
            batterySettingsIntent()
        }
    }

    // No REQUEST_IGNORE_BATTERY_OPTIMIZATIONS permission is declared, so
    // there is no direct request dialog: this deep-links to Waqfah's own
    // system page, where Battery -> Unrestricted grants (or revokes) the
    // exemption manually.
    fun batterySettingsIntent(): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())

    private fun getSystemProperty(key: String): String? = try {
        val clazz = Class.forName("android.os.SystemProperties")
        val getMethod = clazz.getMethod("get", String::class.java)
        getMethod.invoke(null, key) as? String
    } catch (_: Exception) {
        null
    }

    private companion object {
        // Xiaomi MIUI / HyperOS custom AppOp code for OP_BACKGROUND_START_ACTIVITY
        private const val OP_BACKGROUND_START_ACTIVITY = 10021
    }
}
