package com.shrekbytes.waqfah

import com.shrekbytes.waqfah.data.model.AidLanguage
import com.shrekbytes.waqfah.data.model.AppLanguage
import com.shrekbytes.waqfah.data.model.ArabicFont
import com.shrekbytes.waqfah.data.model.ArabicScript
import com.shrekbytes.waqfah.data.model.NameDisplayLanguage
import com.shrekbytes.waqfah.data.model.ReadingMode
import com.shrekbytes.waqfah.data.model.UserPreferences
import com.shrekbytes.waqfah.ui.reading.readingRenderSignature
import com.shrekbytes.waqfah.ui.theme.AccentColor
import com.shrekbytes.waqfah.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

// Guards the render-relevance filter in ReadingViewModel: every field of
// UserPreferences must either join the signature (because the reading card
// renders it) or deliberately stay out (so its changes skip the full
// re-render). These tests pin both sides of that decision — adding a new
// preference without updating readingRenderSignature fails loudly here.
// Note: appActive deliberately stays OUT — the on/off toggle governs detection
// only and must never gate or re-render the reading card.
class ReadingRelevanceTest {

    private val base = UserPreferences()

    @Test
    fun defaults_produceAStableSignature() {
        assertEquals(readingRenderSignature(base), readingRenderSignature(UserPreferences()))
    }

    @Test
    fun renderedFields_changeTheSignature() {
        assertNotEquals(sig(), sig { it.copy(readingMode = ReadingMode.RANDOM) })
        assertNotEquals(sig(), sig { it.copy(surahNameLanguage = NameDisplayLanguage.ARABIC) })
        assertNotEquals(sig(), sig { it.copy(arabicScript = ArabicScript.UTHMANI) })
        assertNotEquals(sig(), sig { it.copy(arabicFont = ArabicFont.AMIRI) })
        assertNotEquals(sig(), sig { it.copy(arabicFontSize = 27) })
        assertNotEquals(sig(), sig { it.copy(pronunciation = AidLanguage.NONE) })
        assertNotEquals(sig(), sig { it.copy(translitFontSize = 19) })
        assertNotEquals(sig(), sig { it.copy(translationDisplay = AidLanguage.BENGALI) })
        assertNotEquals(sig(), sig { it.copy(translationFontSize = 19) })
        assertNotEquals(sig(), sig { it.copy(activeTranslationEnglish = "pickthall") })
        assertNotEquals(sig(), sig { it.copy(activeTranslationBengali = "rawaialbayan") })
    }

    @Test
    fun nonRenderedFields_doNotChangeTheSignature() {
        assertEquals(sig(), sig { it.copy(theme = AppTheme.DARK) })
        assertEquals(sig(), sig { it.copy(accentColor = AccentColor.CLAY) })
        assertEquals(sig(), sig { it.copy(cooldownMinutes = 45) })
        assertEquals(sig(), sig { it.copy(appActive = false) })
        assertEquals(sig(), sig { it.copy(appLanguage = AppLanguage.BENGALI) })
        assertEquals(sig(), sig { it.copy(hasCompletedOnboarding = true) })
    }

    private fun sig(mutate: (UserPreferences) -> UserPreferences = { it }) =
        readingRenderSignature(mutate(base))
}
