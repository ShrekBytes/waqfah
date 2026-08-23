package com.shrekbytes.waqfah.data.model

import com.shrekbytes.waqfah.ui.theme.AccentColor
import com.shrekbytes.waqfah.ui.theme.AppTheme

enum class ReadingMode { SEQUENTIAL, RANDOM }
enum class NameDisplayLanguage { ENGLISH, BENGALI, ARABIC }
enum class AidLanguage { NONE, ENGLISH, BENGALI }

// Which of quran_core.db's two verse-text columns is rendered. Each script has
// its own disjoint set of fonts below — see ReadingDisplayViewModel.setArabicScript().
enum class ArabicScript { INDOPAK, UTHMANI }

enum class ArabicFont(val script: ArabicScript) {
    DIGITAL_KHATT_INDOPAK(ArabicScript.INDOPAK),
    DIGITAL_KHATT_V2(ArabicScript.INDOPAK),
    MEQURAN(ArabicScript.UTHMANI),
    AMIRI(ArabicScript.UTHMANI),
    KFGQPC_NASTALEEQ(ArabicScript.UTHMANI),
}

data class UserPreferences(
    val theme: AppTheme = AppTheme.SYSTEM,
    val accentColor: AccentColor = AccentColor.SAGE,
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
    val activeTranslationBengali: String = "bayan",
    val cooldownMinutes: Int = 30,
    val appActive: Boolean = true,
    val hasCompletedOnboarding: Boolean = false,
    val lastViewedVerseId: Int? = null,
)
