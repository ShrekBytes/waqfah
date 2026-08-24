package com.shrekbytes.waqfah.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
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
        val elapsedMs = System.currentTimeMillis() - lastShown
        // Negative elapsed (clock rolled back / NTP resync) counts as expired,
        // never as permanently cooling down.
        return elapsedMs >= 0 && elapsedMs < cooldownMinutes * 60_000L
    }

    suspend fun recordShown(packageName: String) =
        appDatabase.monitoredAppDao().updateLastShown(packageName, System.currentTimeMillis())

    fun getAppLabel(packageName: String): String? = try {
        context.packageManager.getApplicationInfo(packageName, 0).loadLabel(context.packageManager).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    // Default flags only — MATCH_ALL also pulls in disabled components and
    // hidden system services nobody would pick to monitor.
    fun getInstalledLaunchableApps(): List<InstalledApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { info ->
                InstalledApp(
                    packageName = info.activityInfo.packageName,
                    label = info.loadLabel(pm).toString(),
                    icon = loadIconBitmap(info, pm),
                )
            }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    // Runs alongside the label load above (same off-main-thread call site — see
    // AppsViewModel.installedApps). Icons are downscaled to a fixed pixel size
    // so holding the whole list in memory stays cheap; AppRow displays them at
    // a fixed 36dp regardless of device density.
    private fun loadIconBitmap(info: ResolveInfo, pm: PackageManager): Bitmap? =
        try {
            info.loadIcon(pm).toFixedSizeBitmap(ICON_SIZE_PX)
        } catch (e: Exception) {
            null // AppRow falls back to its letter avatar
        }

    private fun Drawable.toFixedSizeBitmap(sizePx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, sizePx, sizePx)
        draw(canvas)
        return bitmap
    }

    private companion object {
        const val ICON_SIZE_PX = 128
    }
}
