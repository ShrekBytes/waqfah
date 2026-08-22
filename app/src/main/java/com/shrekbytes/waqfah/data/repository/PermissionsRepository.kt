package com.shrekbytes.waqfah.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import com.shrekbytes.waqfah.detection.WaqfahAccessibilityService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PermissionsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun isAccessibilityServiceEnabled(): Boolean {
        val expected = "${context.packageName}/${WaqfahAccessibilityService::class.java.name}"
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    // Cross-OEM accessibility settings don't reliably support deep-linking to one
    // specific service's toggle, so this opens the general list and the user
    // finds "Waqfah" in it.
    fun accessibilitySettingsIntent(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    // ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS only does anything when the
    // app *isn't* exempted yet — once granted, requesting it again is a
    // no-op on most OEMs (no dialog appears, nothing happens). Android has
    // no direct deep link to revoke the exemption, so once it's already
    // granted this instead opens the app's own details page, where Battery
    // -> Unrestricted can be turned off manually.
    fun batteryOptimizationRequestIntent(): Intent =
        if (isIgnoringBatteryOptimizations()) {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        } else {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
}
