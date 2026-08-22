package com.shrekbytes.waqfah.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.shrekbytes.waqfah.data.local.appstate.MonitoredAppEntity
import com.shrekbytes.waqfah.data.local.appstate.WaqfahAppDatabase
import com.shrekbytes.waqfah.data.model.InstalledApp
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MonitoredAppsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appDatabase: WaqfahAppDatabase,
) {
    val monitoredApps: Flow<List<MonitoredAppEntity>> = appDatabase.monitoredAppDao().observeAll()

    suspend fun add(packageName: String) =
        appDatabase.monitoredAppDao().upsert(MonitoredAppEntity(packageName, addedAt = System.currentTimeMillis()))

    suspend fun remove(packageName: String) = appDatabase.monitoredAppDao().remove(packageName)

    suspend fun isInCooldown(packageName: String, cooldownMinutes: Int): Boolean {
        val lastShown = appDatabase.monitoredAppDao().getLastShown(packageName) ?: return false
        val elapsedMinutes = (System.currentTimeMillis() - lastShown) / 60_000
        return elapsedMinutes < cooldownMinutes
    }

    suspend fun recordShown(packageName: String) =
        appDatabase.monitoredAppDao().updateLastShown(packageName, System.currentTimeMillis())

    fun getAppLabel(packageName: String): String? = try {
        context.packageManager.getApplicationInfo(packageName, 0).loadLabel(context.packageManager).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    fun getInstalledLaunchableApps(): List<InstalledApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        // Default flags only — MATCH_ALL pulls in disabled components, hidden
        // system services, uninstalled-but-not-purged packages, etc. On a
        // typical OEM-skinned phone that's hundreds of extra entries nobody
        // would ever pick to monitor, which was very likely a real
        // contributor to the list feeling heavy to scroll through, on top of
        // just being a confusing picker (Waqfah's own entry included).
        return pm.queryIntentActivities(intent, 0)
            .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
}
