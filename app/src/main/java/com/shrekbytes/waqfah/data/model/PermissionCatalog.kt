package com.shrekbytes.waqfah.data.model

data class PermissionInfo(val name: String, val description: String)

// Single accessibility-based variant only (see Permissions screen + the
// accessibility service) — no separate Play/usage-access list to branch on.
object PermissionCatalog {
    val all = listOf(
        PermissionInfo("Accessibility", "Notices when a selected app opens"),
        PermissionInfo("Unrestricted battery usage", "Keeps Waqfah running reliably in the background"),
    )
}
