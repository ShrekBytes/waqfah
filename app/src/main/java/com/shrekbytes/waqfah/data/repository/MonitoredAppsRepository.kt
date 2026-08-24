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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
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

    suspend fun isInCooldown(packageName: String, cooldownMinutes: Int): Boolean =
        isWithinCooldown(
            lastShownAt = appDatabase.monitoredAppDao().getLastShown(packageName),
            now = System.currentTimeMillis(),
            cooldownMinutes = cooldownMinutes,
        )

    suspend fun recordShown(packageName: String) =
        appDatabase.monitoredAppDao().updateLastShown(packageName, System.currentTimeMillis())

    fun getAppLabel(packageName: String): String? = try {
        context.packageManager.getApplicationInfo(packageName, 0).loadLabel(context.packageManager).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    // Runs off the main thread (see AppsViewModel.installedApps). Labels and
    // icon bitmaps each bind against PackageManager, so they're resolved
    // concurrently — on big app lists that cuts wall-clock roughly to the
    // slowest entry instead of the sum of all of them.
    suspend fun getInstalledLaunchableApps(): List<InstalledApp> = withContext(Dispatchers.Default) {
        val pm = context.packageManager
        // Default flags only — MATCH_ALL also pulls in disabled components and
        // hidden system services nobody would pick to monitor.
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .filter { it.activityInfo.packageName != context.packageName }
            // Dedupe before rendering so duplicate activities don't pay icon cost.
            .distinctBy { it.activityInfo.packageName }
            .map { info ->
                async {
                    InstalledApp(
                        packageName = info.activityInfo.packageName,
                        label = info.loadLabel(pm).toString(),
                        icon = loadIconBitmap(info, pm),
                    )
                }
            }
            .awaitAll()
            .sortedBy { it.label.lowercase() }
    }
    // Icons are downscaled to a fixed pixel size so holding the whole list in
    // memory stays cheap; AppRow displays them at a fixed 36dp regardless of
    // device density.
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

    companion object {
        const val ICON_SIZE_PX = 128

        // Pure core of isInCooldown, extracted for unit testing. Negative
        // elapsed (clock rolled back / NTP resync) counts as expired, never as
        // permanently cooling down. A 0-minute interval (Off) never cools down.
        fun isWithinCooldown(lastShownAt: Long?, now: Long, cooldownMinutes: Int): Boolean {
            lastShownAt ?: return false
            val elapsedMs = now - lastShownAt
            return elapsedMs >= 0 && elapsedMs < cooldownMinutes * 60_000L
        }
    }
}
