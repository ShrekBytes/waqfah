package com.shrekbytes.waqfah.ui.reading

import com.shrekbytes.waqfah.data.local.core.SurahEntity
import com.shrekbytes.waqfah.data.model.NameDisplayLanguage

private val DIGIT_MAPS = mapOf(
    NameDisplayLanguage.BENGALI to "০১২৩৪৫৬৭৮৯",
    NameDisplayLanguage.ARABIC to "٠١٢٣٤٥٦٧٨٩",
)

internal fun localizeDigits(n: Int, lang: NameDisplayLanguage): String {
    val map = DIGIT_MAPS[lang] ?: return n.toString()
    return n.toString().map { ch -> if (ch.isDigit()) map[ch - '0'] else ch }.joinToString("")
}

internal fun ayahWord(lang: NameDisplayLanguage): String = when (lang) {
    NameDisplayLanguage.ENGLISH -> "ayat"
    NameDisplayLanguage.BENGALI -> "আয়াত"
    NameDisplayLanguage.ARABIC -> "آية"
}

internal fun surahDisplayName(surah: SurahEntity, lang: NameDisplayLanguage): String = when (lang) {
    NameDisplayLanguage.ENGLISH -> surah.nameEnglish ?: surah.nameArabic.orEmpty()
    NameDisplayLanguage.BENGALI -> surah.nameBengali ?: surah.nameEnglish.orEmpty()
    NameDisplayLanguage.ARABIC -> surah.nameArabic.orEmpty()
}
