package com.shrekbytes.waqfah.ui.reading

import androidx.compose.ui.unit.LayoutDirection
import com.shrekbytes.waqfah.data.model.ArabicFont

// The subset of an ayah's rendered content needed to peek at a neighbouring
// ayah while swiping — see ReadingUiState.nextPreview/previousPreview below
// and AyahPeekPage in ReadingCard.kt. No surah-name header (that stays
// static above the swipeable area, doesn't slide with the content) and no
// translation-switcher state (compare-mode only makes sense for the ayah
// you're actually reading, not one you're mid-swipe past).
data class AyahPreview(
    val ayahLabel: String,
    val arabicText: String,
    val arabicFont: ArabicFont,
    val arabicFontSize: Int,
    val translitText: String?,
    val translitFontSize: Int,
    val translationText: String?,
    val translationFontSize: Int,
)

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
    // The neighbouring ayahs, kept ready so a swipe can reveal their real
    // content sliding in from the edge instead of a blank gap — refreshed by
    // ReadingViewModel.render() every time the current ayah (or a display
    // setting that affects it, like font size) changes. Null only very
    // briefly before the first render() completes.
    val nextPreview: AyahPreview? = null,
    val previousPreview: AyahPreview? = null,
)
