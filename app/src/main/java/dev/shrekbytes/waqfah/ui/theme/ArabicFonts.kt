package dev.shrekbytes.waqfah.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import dev.shrekbytes.waqfah.R
import dev.shrekbytes.waqfah.data.model.ArabicFont

private val DigitalKhattIndopakFontFamily = FontFamily(Font(ArabicFont.DIGITAL_KHATT_INDOPAK.fontResId))
private val AmiriFontFamily = FontFamily(Font(ArabicFont.AMIRI.fontResId))

// The one place a font name meets its resource file. The reading UI, the
// startup probe and the coverage test all read the pairing from here, so a
// font is added or swapped in exactly one place.
val ArabicFont.fontResId: Int
    get() = when (this) {
        ArabicFont.DIGITAL_KHATT_INDOPAK -> R.font.digital_khatt_indopak
        ArabicFont.AMIRI -> R.font.amiri
    }

fun ArabicFont.toFontFamily(): FontFamily = when (this) {
    ArabicFont.DIGITAL_KHATT_INDOPAK -> DigitalKhattIndopakFontFamily
    ArabicFont.AMIRI -> AmiriFontFamily
}

fun ArabicFont.displayName(): String = when (this) {
    ArabicFont.DIGITAL_KHATT_INDOPAK -> "Digital Khatt Indopak"
    ArabicFont.AMIRI -> "Amiri"
}
