package com.shrekbytes.waqfah

import com.shrekbytes.waqfah.data.model.InstalledApp
import com.shrekbytes.waqfah.ui.settings.apps.LoadedApp
import com.shrekbytes.waqfah.ui.settings.apps.filterLoadedApps
import org.junit.Assert.assertEquals
import org.junit.Test

// Pins the monitored-apps search semantics: labels match as case-insensitive
// substrings of the TRIMMED query, so a stray keyboard space can't blank the
// list — the same rule GoToSurahFilterTest pins for the go-to screen.
class AppsFilterTest {

    private fun app(label: String) = LoadedApp(InstalledApp("com.example.$label", label), pinnedTop = false)

    private val apps = listOf(app("Waqfah"), app("WhatsApp"), app("Telegram"))

    private fun matchingLabels(query: String) = filterLoadedApps(apps, query).map { it.app.label }

    @Test
    fun whitespaceAroundQuery_stillMatchesLabels() {
        assertEquals(listOf("Waqfah"), matchingLabels("waqfah "))
        assertEquals(listOf("WhatsApp"), matchingLabels(" sapp"))
    }

    @Test
    fun blankQuery_returnsAllApps() {
        assertEquals(apps, filterLoadedApps(apps, "   "))
    }

    @Test
    fun labels_matchCaseInsensitively() {
        assertEquals(listOf("Telegram"), matchingLabels("tele"))
    }
}
