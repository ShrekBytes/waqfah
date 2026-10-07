package dev.shrekbytes.waqfah.data.model

import dev.shrekbytes.waqfah.ui.theme.AccentColor
import dev.shrekbytes.waqfah.ui.theme.AppTheme

// Single source of truth for every user-adjustable range — steppers render it
// and ViewModels coerce against it, so UI and persistence can't drift apart.
object PreferenceLimits {
    const val FONT_SIZE_MIN = 11
    const val FONT_SIZE_MAX = 33
    const val COOLDOWN_MIN_MINUTES = 0
    const val COOLDOWN_MAX_MINUTES = 60
}

// The app's own display language (independent of the aid-content languages).
enum class AppLanguage { SYSTEM, ENGLISH, BENGALI }

// SEQUENTIAL and RANDOM are mushaf orderings; BOOKMARKS walks the bookmark
// collection in Quran order and only the interstitial does so — Home is always
// the whole Quran, and the mushaf treats BOOKMARKS as SEQUENTIAL (see
// VerseSelection.start). See CONTEXT.md's "Reading mode".
enum class ReadingMode { SEQUENTIAL, RANDOM, BOOKMARKS }
enum class NameDisplayLanguage { ENGLISH, BENGALI, ARABIC }
enum class AidLanguage { NONE, ENGLISH, BENGALI }

// Which of quran_core.db's two verse-text columns is rendered. Each script has
// its own disjoint set of fonts below — see ReadingDisplayViewModel.setArabicScript().
enum class ArabicScript { INDOPAK, UTHMANI }

enum class ArabicFont(val script: ArabicScript) {
    DIGITAL_KHATT_INDOPAK(ArabicScript.INDOPAK),
    AMIRI(ArabicScript.UTHMANI),
}

data class UserPreferences(
    val theme: AppTheme = AppTheme.SYSTEM,
    val accentColor: AccentColor = AccentColor.SAGE,
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    val readingMode: ReadingMode = ReadingMode.SEQUENTIAL,
    // #33: when on, a successful mark-read also steps the card to the next
    // ayah — the walk's own "next", read or unread. Advanced settings; off by
    // default so the card never moves without the user's own step. Affects
    // the mark-read action only, never what the card renders.
    val autoNextOnMark: Boolean = false,
    // The clean-look toggles (Advanced settings): hide the reading card's
    // prev/next arrows, share control and bookmark toggle. Default false —
    // every control visible. Presentation only: hosts turn them into the
    // card's visibility params, and the session never reads them, so they
    // deliberately stay out of readingRenderSignature. The long-press
    // gestures keep every hidden control reachable; mark read is not
    // hideable.
    val hidePrevNextArrows: Boolean = false,
    val hideShareControl: Boolean = false,
    val hideBookmarkToggle: Boolean = false,
    val surahNameLanguage: NameDisplayLanguage = NameDisplayLanguage.ENGLISH,
    val arabicScript: ArabicScript = ArabicScript.INDOPAK,
    val arabicFont: ArabicFont = ArabicFont.DIGITAL_KHATT_INDOPAK,
    val arabicFontSize: Int = 26,
    val pronunciation: AidLanguage = AidLanguage.ENGLISH,
    val translitFontSize: Int = 18,
    val translationDisplay: AidLanguage = AidLanguage.ENGLISH,
    val translationFontSize: Int = 18,
    val activeTranslationEnglish: String = "sahih",
    val activeTranslationBengali: String = "muhiuddinkhan",
    val cooldownMinutes: Int = 30,
    val appActive: Boolean = true,
    val hasCompletedOnboarding: Boolean = false,

    // False until the user finishes the Home-screen feature tour once. Skipped
    // tours leave this untouched, so they're offered again next launch.
    val hasCompletedFeatureTour: Boolean = false,
) {
    // The stored translation for a language — which field holds it is this
    // class's shape detail. Resolve it to the active translation through
    // TranslationLibrary.
    fun storedTranslationId(language: TranslationLanguage): String =
        if (language == TranslationLanguage.ENGLISH) activeTranslationEnglish else activeTranslationBengali
}
