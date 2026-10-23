package dev.shrekbytes.waqfah

import android.os.Build
import dev.shrekbytes.waqfah.data.local.core.SurahEntity
import dev.shrekbytes.waqfah.data.local.core.VerseEntity
import dev.shrekbytes.waqfah.data.model.ArabicScript
import dev.shrekbytes.waqfah.data.model.NameDisplayLanguage
import dev.shrekbytes.waqfah.ui.reading.ayahLabel
import dev.shrekbytes.waqfah.ui.reading.ayahWord
import dev.shrekbytes.waqfah.ui.reading.arabicTextFor
import dev.shrekbytes.waqfah.ui.reading.localizeDigits
import dev.shrekbytes.waqfah.ui.reading.surahDisplayName
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingFormattingTest {

    private val verse = VerseEntity(
        id = 262,
        surahNo = 2,
        ayahNo = 255,
        arabicIndopak = "indopak-text",
        arabicUthmani = "uthmani-text",
        bnTransliteration = "bn-translit",
        enTransliteration = "en-translit",
    )

    private fun surah(
        nameEnglish: String? = "Al-Baqarah",
        nameBengali: String? = "আল-বাকারা",
        nameArabic: String? = "البقرة",
    ) = SurahEntity(id = 2, surahNo = 2, nameArabic = nameArabic, nameEnglish = nameEnglish, nameBengali = nameBengali, ayahCount = 286)

    // localizeDigits

    @org.junit.Test
    fun localizeDigits_englishUsesWesternDigits() {
        assertEquals("2", localizeDigits(2, NameDisplayLanguage.ENGLISH))
        assertEquals("255", localizeDigits(255, NameDisplayLanguage.ENGLISH))
    }

    @org.junit.Test
    fun localizeDigits_bengaliAndArabicMapEveryDigit() {
        assertEquals("২২৫৫", localizeDigits(2255, NameDisplayLanguage.BENGALI))
        // 987654312 covers every Arabic-Indic digit exactly once.
        assertEquals("٩٨٧٦٥٤٣١٢", localizeDigits(987654312, NameDisplayLanguage.ARABIC))
    }

    // ayahWord / ayahLabel

    @Test
    fun ayahWord_perLanguage() {
        assertEquals("ayat", ayahWord(NameDisplayLanguage.ENGLISH))
        assertEquals("আয়াত", ayahWord(NameDisplayLanguage.BENGALI))
        assertEquals("آية", ayahWord(NameDisplayLanguage.ARABIC))
    }

    @Test
    fun ayahLabel_usesLocalizedDigits() {
        assertEquals("2:255", ayahLabel(verse, NameDisplayLanguage.ENGLISH))
        assertEquals("২:২৫৫", ayahLabel(verse, NameDisplayLanguage.BENGALI))
    }

    // surahDisplayName fallback chain

    @Test
    fun surahDisplayName_preferredLanguage() {
        assertEquals("Al-Baqarah", surahDisplayName(surah(), NameDisplayLanguage.ENGLISH))
        assertEquals("আল-বাকারা", surahDisplayName(surah(), NameDisplayLanguage.BENGALI))
        assertEquals("البقرة", surahDisplayName(surah(), NameDisplayLanguage.ARABIC))
    }

    @Test
    fun surahDisplayName_fallsBackWhenTranslationMissing() {
        assertEquals("البقرة", surahDisplayName(surah(nameEnglish = null), NameDisplayLanguage.ENGLISH))
        assertEquals("Al-Baqarah", surahDisplayName(surah(nameBengali = null), NameDisplayLanguage.BENGALI))
        assertEquals("", surahDisplayName(surah(nameArabic = null), NameDisplayLanguage.ARABIC))
    }

    // arabicTextFor script selection

    @Test
    fun arabicTextFor_selectsMatchingColumn() {
        assertEquals("indopak-text", verse.arabicTextFor(ArabicScript.INDOPAK))
        assertEquals("uthmani-text", verse.arabicTextFor(ArabicScript.UTHMANI))
    }

    // The madda-wajib sign (U+089C) is unassigned in every Unicode version the
    // Android 12-and-below shapers carry, so those platforms draw broken words
    // without this substitution; from Android 13 the sign joins normally and
    // the text reaches the shaper as stored (ADR-0008).

    // 2:5's first word, as quran_core.db stores it — the sign is codepoint
    // U+089C, written as an escape because it is invisible in source.
    private val verseWithMaddaWaajib =
        verse.copy(arabicIndopak = "\u0627\u064F\u0648\u0644\u0670\u089C\u0649\u0655\u0650\u0643\u064E")

    @Test
    fun arabicTextFor_substitutesMaddaWaajibOnAndroid12AndBelow() {
        val substituted = "\u0627\u064F\u0648\u0644\u0670\u0653\u0649\u0655\u0650\u0643\u064E"
        listOf(Build.VERSION_CODES.R, Build.VERSION_CODES.S, Build.VERSION_CODES.S_V2).forEach { sdk ->
            assertEquals(substituted, verseWithMaddaWaajib.arabicTextFor(ArabicScript.INDOPAK, sdk))
        }
    }

    @Test
    fun arabicTextFor_keepsMaddaWaajibOnAndroid13AndUp() {
        assertEquals(
            verseWithMaddaWaajib.arabicIndopak,
            verseWithMaddaWaajib.arabicTextFor(ArabicScript.INDOPAK, Build.VERSION_CODES.TIRAMISU),
        )
    }

    @Test
    fun arabicTextFor_leavesTextWithoutTheSignAlone() {
        listOf(Build.VERSION_CODES.R, Build.VERSION_CODES.TIRAMISU).forEach { sdk ->
            assertEquals("indopak-text", verse.arabicTextFor(ArabicScript.INDOPAK, sdk))
            assertEquals("uthmani-text", verse.arabicTextFor(ArabicScript.UTHMANI, sdk))
        }
    }

    @Test
    fun arabicTextFor_substitutesInEitherColumn() {
        // No verse carries the sign in the uthmani column today; the same door
        // covers it if one ever does.
        val withSignInBoth = verseWithMaddaWaajib.copy(arabicUthmani = "\u089C")
        assertEquals("\u0653", withSignInBoth.arabicTextFor(ArabicScript.UTHMANI, Build.VERSION_CODES.R))
        assertEquals("\u089C", withSignInBoth.arabicTextFor(ArabicScript.UTHMANI, Build.VERSION_CODES.TIRAMISU))
    }
}