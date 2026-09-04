package com.shrekbytes.waqfah.data.installedapp

import com.shrekbytes.waqfah.data.model.InstalledApp

// The launchable apps Waqfah offers for selection. Android discovery and icon
// rendering live in the PackageManager adapter, not in monitored-app state.
interface InstalledAppCatalog {
    suspend fun load(): List<InstalledApp>
    fun labelFor(packageName: String): String?
}
