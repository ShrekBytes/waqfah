package com.shrekbytes.waqfah

import com.shrekbytes.waqfah.data.local.core.SurahEntity
import com.shrekbytes.waqfah.data.local.core.VerseEntity
import com.shrekbytes.waqfah.data.model.ArabicScript
import com.shrekbytes.waqfah.data.model.NameDisplayLanguage
import com.shrekbytes.waqfah.ui.reading.ayahLabel
import com.shrekbytes.waqfah.ui.reading.ayahWord
import com.shrekbytes.waqfah.ui.reading.arabicTextFor
import com.shrekbytes.waqfah.ui.reading.localizeDigits
import com.shrekbytes.waqfah.ui.reading.surahDisplayName
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
}