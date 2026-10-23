package dev.shrekbytes.waqfah.ui.reading

import android.os.Build
import dev.shrekbytes.waqfah.data.local.core.SurahEntity
import dev.shrekbytes.waqfah.data.local.core.VerseEntity
import dev.shrekbytes.waqfah.data.model.ArabicScript
import dev.shrekbytes.waqfah.data.model.NameDisplayLanguage

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

internal fun ayahLabel(verse: VerseEntity, lang: NameDisplayLanguage): String =
    "${localizeDigits(verse.surahNo, lang)}:${localizeDigits(verse.ayahNo, lang)}"

// U+089C ARABIC MADDA WAAJIB, assigned only in Unicode 14 (2021), and its
// stand-in U+0653 ARABIC MADDAH ABOVE, a mark in every Unicode version.
private const val MADDA_WAAJIB = '\u089C'
private const val MADDA_ABOVE = '\u0653'

/**
 * Which verse column holds the currently selected script's glyphs, in the form
 * the device's shaper can join.
 *
 * Android 12 and below vendor shaper tables generated from
 * `ArabicShaping-12.0.0`, where U+089C is still unassigned. An unassigned
 * codepoint resolves to a non-joining base — `JOINING_TYPE_X` falls back to the
 * general category, and only marks and format characters come out transparent —
 * so the shaper cuts the cursive join inside every word carrying the sign:
 * أُولَٰئِكَ draws with the lam detached and the alef-maksura in initial form.
 * That decision is made from Unicode data before the font is consulted, which
 * is why no font change fixes it and why the report behind it (Realme C12,
 * Android 11) survived the CFF2 → TrueType conversion untouched.
 *
 * U+0653 is a maddah mark in every Unicode version this app can meet, and the
 * bundled Digital Khatt draws it from its own `maddahabove` glyph, so
 * substituting it restores the join everywhere the sign would otherwise break
 * one. Android 13 (API 33) vendors `ArabicShaping-14.0.0` tables, where
 * U+089C is a mark and joins normally; from there on the stored text is drawn
 * verbatim. See ADR-0008.
 *
 * Only `arabic_indopak` carries the sign — 2,098 occurrences across 1,576
 * verses. The uthmani column has none, so the substitution is a no-op there;
 * it is applied to either column all the same so the door stays in one place.
 *
 * `sdkInt` takes the platform level as an argument so both sides of the gate
 * are pinned by tests on the JVM, where `Build.VERSION.SDK_INT` reads 0.
 */
internal fun VerseEntity.arabicTextFor(
    script: ArabicScript,
    sdkInt: Int = Build.VERSION.SDK_INT,
): String {
    val text = when (script) {
        ArabicScript.INDOPAK -> arabicIndopak
        ArabicScript.UTHMANI -> arabicUthmani
    }
    return if (sdkInt < Build.VERSION_CODES.TIRAMISU) {
        text.replace(MADDA_WAAJIB, MADDA_ABOVE)
    } else {
        text
    }
}

internal fun surahDisplayName(surah: SurahEntity, lang: NameDisplayLanguage): String = when (lang) {
    NameDisplayLanguage.ENGLISH -> surah.nameEnglish ?: surah.nameArabic.orEmpty()
    NameDisplayLanguage.BENGALI -> surah.nameBengali ?: surah.nameEnglish.orEmpty()
    NameDisplayLanguage.ARABIC -> surah.nameArabic.orEmpty()
}
