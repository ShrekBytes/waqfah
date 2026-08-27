package com.shrekbytes.waqfah.data.model

import com.shrekbytes.waqfah.ui.theme.AccentColor
import com.shrekbytes.waqfah.ui.theme.AppTheme

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

enum class ReadingMode { SEQUENTIAL, RANDOM }
enum class NameDisplayLanguage { ENGLISH, BENGALI, ARABIC }
enum class AidLanguage { NONE, ENGLISH, BENGALI }

// Which of quran_core.db's two verse-text columns is rendered. Each script has
// its own disjoint set of fonts below — see ReadingDisplayViewModel.setArabicScript().
enum class ArabicScript { INDOPAK, UTHMANI }

enum class ArabicFont(val script: ArabicScript) {
    DIGITAL_KHATT_INDOPAK(ArabicScript.INDOPAK),
    MEQURAN(ArabicScript.UTHMANI),
    AMIRI(ArabicScript.UTHMANI),
}

data class UserPreferences(
    val theme: AppTheme = AppTheme.SYSTEM,
    val accentColor: AccentColor = AccentColor.SAGE,
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    val readingMode: ReadingMode = ReadingMode.SEQUENTIAL,
    val surahNameLanguage: NameDisplayLanguage = NameDisplayLanguage.ENGLISH,
    val arabicScript: ArabicScript = ArabicScript.INDOPAK,
    val arabicFont: ArabicFont = ArabicFont.DIGITAL_KHATT_INDOPAK,
    val arabicFontSize: Int = 26,
    val pronunciation: AidLanguage = AidLanguage.ENGLISH,
    val translitFontSize: Int = 18,
    val translationDisplay: AidLanguage = AidLanguage.ENGLISH,
    val translationFontSize: Int = 18,
    val activeTranslationEnglish: String = "sahih",
    val activeTranslationBengali: String = "taisirul",
    val cooldownMinutes: Int = 30,
    val appActive: Boolean = true,
    val hasCompletedOnboarding: Boolean = false,

    // False until the user finishes the Home-screen feature tour once. Skipped
    // tours leave this untouched, so they're offered again next launch.
    val hasCompletedFeatureTour: Boolean = false,
)
