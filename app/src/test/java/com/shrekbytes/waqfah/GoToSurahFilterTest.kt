package com.shrekbytes.waqfah

import com.shrekbytes.waqfah.data.local.core.SurahEntity
import com.shrekbytes.waqfah.ui.ayahpicker.SurahRow
import com.shrekbytes.waqfah.ui.ayahpicker.filterSurahRows
import org.junit.Assert.assertEquals
import org.junit.Test

// Pins the go-to list's search semantics: surah names match as case-insensitive
// substrings and the surah number exactly, both against the TRIMMED query so a
// stray keyboard space can't blank the list.
class GoToSurahFilterTest {

    private fun row(no: Int, english: String?, arabic: String? = null, bengali: String? = null) =
        SurahRow(
            SurahEntity(
                id = no,
                surahNo = no,
                nameArabic = arabic,
                nameEnglish = english,
                nameBengali = bengali,
                ayahCount = 7,
            ),
            readCount = 0,
        )

    private val rows = listOf(
        row(1, "Al-Fatihah", arabic = "الفاتحة", bengali = "আল-ফাতিহা"),
        row(2, "Al-Baqarah", arabic = "البقرة", bengali = "আল-বাকারা"),
        row(12, "Yusuf"),
    )

    private fun matchingSurahNos(query: String) = filterSurahRows(rows, query).map { it.surah.surahNo }

    @Test
    fun whitespaceAroundQuery_stillMatchesNames() {
        assertEquals(listOf(1), matchingSurahNos("Fatiha "))
        assertEquals(listOf(12), matchingSurahNos(" yusuf"))
    }

    @Test
    fun whitespaceAroundQuery_stillMatchesNumber() {
        assertEquals(listOf(2), matchingSurahNos(" 2 "))
    }

    @Test
    fun blankQuery_returnsAllRows() {
        assertEquals(rows, filterSurahRows(rows, "   "))
    }

    @Test
    fun number_matchesExactlyNotBySubstring() {
        assertEquals(listOf(2), matchingSurahNos("2"))
    }

    @Test
    fun names_matchCaseInsensitively() {
        assertEquals(listOf(2), matchingSurahNos("baqarah"))
    }
}
