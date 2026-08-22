package com.shrekbytes.waqfah.ui.reading

import androidx.compose.ui.unit.LayoutDirection
import com.shrekbytes.waqfah.data.model.ArabicFont

data class ReadingUiState(
    val isLoading: Boolean = true,
    val isPaused: Boolean = false,
    val surahName: String = "",
    val surahNameDirection: LayoutDirection = LayoutDirection.Ltr,
    val ayahLabel: String = "",
    val totalLabel: String = "",
    val arabicText: String = "",
    val arabicFont: ArabicFont = ArabicFont.DIGITAL_KHATT_INDOPAK,
    val arabicFontSize: Int = 26,
    val translitText: String? = null,
    val translitFontSize: Int = 18,
    val translationText: String? = null,
    val translationFontSize: Int = 18,
    // Display name of whichever translation translationText is currently
    // showing — the user's real default unless cycleTranslationSource() has
    // temporarily swapped it out for this ayah (see ReadingViewModel).
    val translationSourceName: String? = null,
    // Whether there's more than one *downloaded* translation for the active
    // display language, i.e. whether tapping the translation to compare
    // sources would actually do anything.
    val translationHasAlternates: Boolean = false,
    val isMarkedRead: Boolean = false,
    val triggeredAppLabel: String? = null, // non-null only on the Reading (trigger) screen
)
