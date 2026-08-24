package com.shrekbytes.waqfah.data.model

import androidx.annotation.StringRes
import com.shrekbytes.waqfah.R

// Stable identity for each permission row so screens never depend on list order.
enum class PermissionKey { USAGE_ACCESS, OVERLAY, BATTERY }

data class PermissionInfo(val key: PermissionKey, @StringRes val nameRes: Int, @StringRes val descriptionRes: Int)

object PermissionCatalog {
    val usage = PermissionInfo(
        PermissionKey.USAGE_ACCESS,
        R.string.perm_usage_name,
        R.string.perm_usage_desc,
    )
    val overlay = PermissionInfo(
        PermissionKey.OVERLAY,
        R.string.perm_overlay_name,
        R.string.perm_overlay_desc,
    )
    val battery = PermissionInfo(
        PermissionKey.BATTERY,
        R.string.perm_battery_name,
        R.string.perm_battery_desc,
    )

    val all = listOf(usage, overlay, battery)
}
