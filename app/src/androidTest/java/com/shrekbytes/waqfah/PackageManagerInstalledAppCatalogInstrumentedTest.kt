package com.shrekbytes.waqfah

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shrekbytes.waqfah.data.repository.PackageManagerInstalledAppCatalog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

// The Android adapter is tested against the device PackageManager. Assertions
// cover the adapter's stable policy while leaving the installed app set device-
// dependent.
@RunWith(AndroidJUnit4::class)
class PackageManagerInstalledAppCatalogInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val catalog = PackageManagerInstalledAppCatalog(context)

    @Test
    fun load_excludesWaqfah_sortsLabels_andUsesFixedIconSize() = runBlocking {
        val apps = catalog.load()

        assertTrue(apps.none { it.packageName == context.packageName })
        assertEquals(
            apps.map { it.label.lowercase() }.sorted(),
            apps.map { it.label.lowercase() },
        )
        apps.mapNotNull { it.icon }.forEach { icon ->
            assertEquals(PackageManagerInstalledAppCatalog.ICON_SIZE_PX, icon.width)
            assertEquals(PackageManagerInstalledAppCatalog.ICON_SIZE_PX, icon.height)
        }
    }

    @Test
    fun labelFor_resolvesApplicationLabel() {
        assertTrue(catalog.labelFor(context.packageName).orEmpty().isNotBlank())
    }
}
