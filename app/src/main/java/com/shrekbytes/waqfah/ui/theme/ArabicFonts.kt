package com.shrekbytes.waqfah.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.shrekbytes.waqfah.R
import com.shrekbytes.waqfah.data.model.ArabicFont

private val DigitalKhattIndopakFontFamily = FontFamily(Font(R.font.digital_khatt_indopak))
private val MeQuranFontFamily = FontFamily(Font(R.font.mequran))
private val AmiriFontFamily = FontFamily(Font(R.font.amiri))

fun ArabicFont.toFontFamily(): FontFamily = when (this) {
    ArabicFont.DIGITAL_KHATT_INDOPAK -> DigitalKhattIndopakFontFamily
    ArabicFont.MEQURAN -> MeQuranFontFamily
    ArabicFont.AMIRI -> AmiriFontFamily
}

fun ArabicFont.displayName(): String = when (this) {
    ArabicFont.DIGITAL_KHATT_INDOPAK -> "Digital Khatt Indopak"
    ArabicFont.MEQURAN -> "MeQuran"
    ArabicFont.AMIRI -> "Amiri"
}
