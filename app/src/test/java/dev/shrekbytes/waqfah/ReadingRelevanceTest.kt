package dev.shrekbytes.waqfah

import dev.shrekbytes.waqfah.data.model.AidLanguage
import dev.shrekbytes.waqfah.data.model.AppLanguage
import dev.shrekbytes.waqfah.data.model.ArabicFont
import dev.shrekbytes.waqfah.data.model.ArabicScript
import dev.shrekbytes.waqfah.data.model.NameDisplayLanguage
import dev.shrekbytes.waqfah.data.model.ReadingMode
import dev.shrekbytes.waqfah.data.model.UserPreferences
import dev.shrekbytes.waqfah.ui.reading.readingRenderSignature
import dev.shrekbytes.waqfah.ui.theme.AccentColor
import dev.shrekbytes.waqfah.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

// Guards the render-relevance filter in the reading machine (ReadingSession):
// every field of UserPreferences must either join the signature (because the
// reading card renders it) or deliberately stay out (so its changes skip the
// full re-render). These tests pin both sides of that decision — adding a new
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
        // autoNextOnMark gates the mark-read action only — what the card
        // renders never changes with it, so toggling it must not re-render.
        assertEquals(sig(), sig { it.copy(autoNextOnMark = true) })
        // The hide-settings are presentation-only: hosts turn them into the
        // card's visibility params outside the session, so toggling one must
        // not re-render either.
        assertEquals(sig(), sig { it.copy(hidePrevNextArrows = true) })
        assertEquals(sig(), sig { it.copy(hideShareControl = true) })
        assertEquals(sig(), sig { it.copy(hideBookmarkToggle = true) })
    }

    private fun sig(mutate: (UserPreferences) -> UserPreferences = { it }) =
        readingRenderSignature(mutate(base))
}
