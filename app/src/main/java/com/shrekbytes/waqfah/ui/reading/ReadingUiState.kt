package com.shrekbytes.waqfah.ui.reading

import androidx.compose.ui.unit.LayoutDirection
import com.shrekbytes.waqfah.data.model.ArabicFont

// The subset of an ayah's rendered content needed to peek at a neighbouring
// ayah mid-swipe (see AyahPeekPage in ReadingCard.kt): no surah header and no
// translation-switcher state — those belong to the ayah actually being read.
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
    // Name of whichever translation translationText currently shows — the
    // user's default unless cycleTranslationSource() swapped it for this ayah.
    val translationSourceName: String? = null,
    // Whether another downloaded translation exists to compare against.
    val translationHasAlternates: Boolean = false,
    val isMarkedRead: Boolean = false,
    val triggeredAppLabel: String? = null, // non-null only on the trigger screen
    // Neighbouring ayahs, kept ready so a swipe reveals real content instead
    // of a blank gap; refreshed on every render().
    val nextPreview: AyahPreview? = null,
    val previousPreview: AyahPreview? = null,
)
