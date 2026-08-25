package com.shrekbytes.waqfah.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.shrekbytes.waqfah.R
import com.shrekbytes.waqfah.data.model.ArabicFont

private val DigitalKhattIndopakFontFamily = FontFamily(Font(R.font.digital_khatt_indopak))
private val DigitalKhattV2FontFamily = FontFamily(Font(R.font.digital_khatt_v2))
private val MeQuranFontFamily = FontFamily(Font(R.font.mequran))
private val AmiriFontFamily = FontFamily(Font(R.font.amiri))

fun ArabicFont.toFontFamily(): FontFamily = when (this) {
    ArabicFont.DIGITAL_KHATT_INDOPAK -> DigitalKhattIndopakFontFamily
    ArabicFont.DIGITAL_KHATT_V2 -> DigitalKhattV2FontFamily
    ArabicFont.MEQURAN -> MeQuranFontFamily
    ArabicFont.AMIRI -> AmiriFontFamily
}

fun ArabicFont.displayName(): String = when (this) {
    ArabicFont.DIGITAL_KHATT_INDOPAK -> "Digital Khatt Indopak"
    ArabicFont.DIGITAL_KHATT_V2 -> "Digital Khatt V2"
    ArabicFont.MEQURAN -> "MeQuran"
    ArabicFont.AMIRI -> "Amiri"
}
