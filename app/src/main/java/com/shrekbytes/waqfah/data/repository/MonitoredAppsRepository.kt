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

    // Runs alongside the label load above (same off-main-thread call site —
    // see AppsViewModel.installedApps), so it doesn't add a second pass over
    // the app list. Downscaled to a small, fixed pixel size regardless of the
    // device's actual icon density (which can be 192px+ on xxxhdpi screens)
    // so holding 100+ of these in memory at once — the whole point of this
    // list — stays cheap: ICON_SIZE_PX² * 4 bytes ≈ 65KB each, so even 150
    // apps is under 10MB. AppRow displays these at a fixed 36dp regardless,
    // so there's no visible quality loss.
    private fun loadIconBitmap(info: ResolveInfo, pm: PackageManager): Bitmap? =
        try {
            info.loadIcon(pm).toFixedSizeBitmap(ICON_SIZE_PX)
        } catch (e: Exception) {
            // A handful of odd system/launcher entries can fail to resolve an
            // icon — AppRow falls back to its existing letter avatar for these.
            null
        }

    // Manual Canvas draw rather than androidx.core's Drawable.toBitmap() —
    // keeps this independent of whatever core-ktx version (or lack of it) is
    // on the classpath, using only framework android.graphics APIs.
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
