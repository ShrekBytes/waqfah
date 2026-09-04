package com.shrekbytes.waqfah.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.core.graphics.createBitmap
import com.shrekbytes.waqfah.data.installedapp.InstalledAppCatalog
import com.shrekbytes.waqfah.data.model.InstalledApp
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PackageManagerInstalledAppCatalog @Inject constructor(
    @ApplicationContext private val context: Context,
) : InstalledAppCatalog {

    override fun labelFor(packageName: String): String? = try {
        context.packageManager.getApplicationInfo(packageName, 0).loadLabel(context.packageManager).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    // Runs off the main thread. Labels and icon bitmaps each bind against
    // PackageManager, so they're resolved concurrently on large app lists.
    override suspend fun load(): List<InstalledApp> = withContext(Dispatchers.Default) {
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
    // memory stays cheap; AppRow displays them at a fixed 36dp.
    private fun loadIconBitmap(info: ResolveInfo, pm: PackageManager): Bitmap? =
        try {
            info.loadIcon(pm).toFixedSizeBitmap(ICON_SIZE_PX)
        } catch (e: Exception) {
            null // AppRow falls back to its letter avatar.
        }

    private fun Drawable.toFixedSizeBitmap(sizePx: Int): Bitmap {
        val bitmap = createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, sizePx, sizePx)
        draw(canvas)
        return bitmap
    }

    companion object {
        const val ICON_SIZE_PX = 128
    }
}
