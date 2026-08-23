package com.shrekbytes.waqfah.data.model

data class PermissionInfo(val name: String, val description: String)

object PermissionCatalog {
    val all = listOf(
        PermissionInfo("Usage access", "Notices when a selected app opens"),
        PermissionInfo("Display over other apps", "Lets the reading screen appear over that app"),
        PermissionInfo("Unrestricted battery", "Keeps Waqfah running reliably in the background"),
    )
}
