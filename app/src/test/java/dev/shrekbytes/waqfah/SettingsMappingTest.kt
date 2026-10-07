package dev.shrekbytes.waqfah

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import dev.shrekbytes.waqfah.data.local.prefs.SettingsKeys
import dev.shrekbytes.waqfah.data.model.AppLanguage
import dev.shrekbytes.waqfah.data.model.ArabicFont
import dev.shrekbytes.waqfah.data.model.ArabicScript
import dev.shrekbytes.waqfah.data.model.AidLanguage
import dev.shrekbytes.waqfah.data.model.ReadingMode
import dev.shrekbytes.waqfah.data.model.UserPreferences
import dev.shrekbytes.waqfah.data.repository.toUserPreferences
import dev.shrekbytes.waqfah.ui.theme.AccentColor
import dev.shrekbytes.waqfah.ui.theme.AppTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

// Guards Preferences → UserPreferences mapping: defaults before first write,
// and safe fallbacks for stored enum names that no longer exist (e.g. renamed
// in an update) — a crash here would brick the app at startup.
class SettingsMappingTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun newStore() = PreferenceDataStoreFactory.create(
        produceFile = { tmp.newFile("test-${System.nanoTime()}.preferences_pb") },
    )

    @Test
    fun emptyStore_yieldsDocumentedDefaults() = runBlocking {
        val prefs = newStore().data.first().toUserPreferences()
        assertEquals(UserPreferences(), prefs)
    }

    @Test
    fun unknownEnumNames_fallBackToDefaults_insteadOfCrashing() = runBlocking {
        val store = newStore()
        store.edit {
            it[SettingsKeys.THEME] = "NO_SUCH_THEME"
            it[SettingsKeys.APP_LANGUAGE] = "KLINGON"
            it[SettingsKeys.READING_MODE] = "SHUFFLE"
            it[SettingsKeys.ARABIC_FONT] = "WINGDINGS"
            it[SettingsKeys.PRONUNCIATION] = "LATIN"
        }
        val prefs = store.data.first().toUserPreferences()
        assertEquals(AppTheme.SYSTEM, prefs.theme)
        assertEquals(AppLanguage.SYSTEM, prefs.appLanguage)
        assertEquals(ReadingMode.SEQUENTIAL, prefs.readingMode)
        assertEquals(ArabicFont.DIGITAL_KHATT_INDOPAK, prefs.arabicFont)
        assertEquals(AidLanguage.ENGLISH, prefs.pronunciation)
    }

    @Test
    fun retiredMequranFont_keepsTheUthmaniScript() = runBlocking {
        val store = newStore()
        store.edit { it[SettingsKeys.ARABIC_FONT] = "MEQURAN" }
        val prefs = store.data.first().toUserPreferences()
        // AMIRI, not the Indopak default: the removed font was the other Uthmani
        // one, so falling through would silently change the rendered script.
        assertEquals(ArabicFont.AMIRI, prefs.arabicFont)
        assertEquals(ArabicScript.UTHMANI, prefs.arabicFont.script)
    }

    @Test
    fun knownValues_mapThrough() = runBlocking {
        val store = newStore()
        store.edit {
            it[SettingsKeys.THEME] = AppTheme.DARK.name
            it[SettingsKeys.ACCENT_COLOR] = AccentColor.PLUM.name
            it[SettingsKeys.ARABIC_SCRIPT] = ArabicScript.UTHMANI.name
            it[SettingsKeys.ARABIC_FONT_SIZE] = 19
            it[SettingsKeys.APP_ACTIVE] = false
            it[SettingsKeys.ONBOARDING_COMPLETE] = true
            it[SettingsKeys.FEATURE_TOUR_COMPLETE] = true
            it[SettingsKeys.AUTO_NEXT_ON_MARK] = true
            it[SettingsKeys.HIDE_PREV_NEXT_ARROWS] = true
            it[SettingsKeys.HIDE_SHARE_CONTROL] = true
            it[SettingsKeys.HIDE_BOOKMARK_TOGGLE] = true
        }
        val prefs = store.data.first().toUserPreferences()
        assertEquals(AppTheme.DARK, prefs.theme)
        assertEquals(AccentColor.PLUM, prefs.accentColor)
        assertEquals(ArabicScript.UTHMANI, prefs.arabicScript)
        assertEquals(19, prefs.arabicFontSize)
        assertEquals(false, prefs.appActive)
        assertEquals(true, prefs.hasCompletedOnboarding)
        assertEquals(true, prefs.hasCompletedFeatureTour)
        assertEquals(true, prefs.autoNextOnMark)
        assertEquals(true, prefs.hidePrevNextArrows)
        assertEquals(true, prefs.hideShareControl)
        assertEquals(true, prefs.hideBookmarkToggle)
    }
}