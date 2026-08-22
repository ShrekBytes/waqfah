package com.shrekbytes.waqfah.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.shrekbytes.waqfah.R
import com.shrekbytes.waqfah.data.model.ArabicFont

// Indopak-script fonts.
private val DigitalKhattIndopakFontFamily = FontFamily(Font(R.font.digital_khatt_indopak))
private val DigitalKhattV2FontFamily = FontFamily(Font(R.font.digital_khatt_v2))

// Uthmani-script fonts.
private val MeQuranFontFamily = FontFamily(Font(R.font.mequran))
private val AmiriFontFamily = FontFamily(Font(R.font.amiri))
private val KfgqpcNastaleeqFontFamily = FontFamily(Font(R.font.kfgqpc_nastaleeq))

fun ArabicFont.toFontFamily(): FontFamily = when (this) {
    ArabicFont.DIGITAL_KHATT_INDOPAK -> DigitalKhattIndopakFontFamily
    ArabicFont.DIGITAL_KHATT_V2 -> DigitalKhattV2FontFamily
    ArabicFont.MEQURAN -> MeQuranFontFamily
    ArabicFont.AMIRI -> AmiriFontFamily
    ArabicFont.KFGQPC_NASTALEEQ -> KfgqpcNastaleeqFontFamily
}

// Display label shown in Settings > Reading & display's Font chip group —
// kept here (rather than as an enum property) so ui/theme stays the single
// place that owns how each ArabicFont actually looks/is labeled, matching
// how the rest of the app's enums keep their UI strings in the screen/theme
// layer rather than baked into data/model.
fun ArabicFont.displayName(): String = when (this) {
    ArabicFont.DIGITAL_KHATT_INDOPAK -> "Digital Khatt Indopak"
    ArabicFont.DIGITAL_KHATT_V2 -> "Digital Khatt V2"
    ArabicFont.MEQURAN -> "MeQuran"
    ArabicFont.AMIRI -> "Amiri"
    ArabicFont.KFGQPC_NASTALEEQ -> "KFGQPC Nastaleeq"
}
