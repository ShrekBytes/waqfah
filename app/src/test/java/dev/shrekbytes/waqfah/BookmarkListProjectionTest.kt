package dev.shrekbytes.waqfah

import dev.shrekbytes.waqfah.data.local.core.SurahEntity
import dev.shrekbytes.waqfah.data.local.core.VerseEntity
import dev.shrekbytes.waqfah.ui.bookmarks.bookmarkSurahRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Pins the bookmarks list's shape: the pure projection over the collection and
// the read-only Quran text (#22, Seam 2 of #17). Nothing here touches a
// database — the caller hands in the surahs and the saved verses it already
// fetched, and this answers what the list shows.
//
// Every input below is deliberately shuffled. The collection reaches the app as
// one observable set with no ORDER BY (BookmarkDao.observeSavedVerseIds), so the
// projection must impose Quran order itself rather than inherit it from the
// caller. A test that passed ordered input would not pin that.
class BookmarkListProjectionTest {

    private fun surah(no: Int, ayahCount: Int = 7) = SurahEntity(
        id = no,
        surahNo = no,
        nameArabic = "ar-$no",
        nameEnglish = "en-$no",
        nameBengali = "bn-$no",
        ayahCount = ayahCount,
    )

    private fun verse(id: Int, surahNo: Int, ayahNo: Int) = VerseEntity(
        id = id,
        surahNo = surahNo,
        ayahNo = ayahNo,
        arabicIndopak = "indopak-$id",
        arabicUthmani = "uthmani-$id",
        bnTransliteration = "bn-tr-$id",
        enTransliteration = "en-tr-$id",
    )

    @Test
    fun emptyCollection_yieldsNoRows() {
        assertTrue(bookmarkSurahRows(listOf(surah(1), surah(2)), emptyList()).isEmpty())
    }

    // The point of the list: 114 rows of which two hold something is a scroll
    // through nothing.
    @Test
    fun surahsWithNothingSaved_areDropped() {
        val rows = bookmarkSurahRows(
            surahs = listOf(surah(1), surah(2), surah(3)),
            savedVerses = listOf(verse(1, 1, 1), verse(20, 3, 2)),
        )

        assertEquals(listOf(1, 3), rows.map { it.surah.surahNo })
    }

    @Test
    fun surahsAreOrderedBySurahNumber_notByInputOrder() {
        val rows = bookmarkSurahRows(
            surahs = listOf(surah(9), surah(2), surah(5)),
            savedVerses = listOf(verse(30, 5, 1), verse(10, 2, 1), verse(50, 9, 1)),
        )

        assertEquals(listOf(2, 5, 9), rows.map { it.surah.surahNo })
    }

    @Test
    fun versesWithinASurah_areOrderedByAyahNumber_notByInputOrder() {
        val rows = bookmarkSurahRows(
            surahs = listOf(surah(2)),
            // Saved out of order — the list still reads the way the surah does.
            savedVerses = listOf(verse(12, 2, 5), verse(8, 2, 1), verse(10, 2, 3)),
        )

        assertEquals(listOf(1, 3, 5), rows.single().savedAyahs.map { it.ayahNo })
    }

    // Both counts travel on the row: the surah's own length (from the Quran
    // text) and how much of it the reader kept (from the collection).
    @Test
    fun eachRowCarriesTheSurahsTotalAyahCountAndItsSavedCount() {
        val rows = bookmarkSurahRows(
            surahs = listOf(surah(2, ayahCount = 286), surah(18, ayahCount = 110)),
            savedVerses = listOf(verse(10, 2, 3), verse(12, 2, 5), verse(100, 18, 1)),
        )

        val alBaqarah = rows.first { it.surah.surahNo == 2 }
        assertEquals(286, alBaqarah.surah.ayahCount)
        assertEquals(2, alBaqarah.savedCount)

        val alKahf = rows.first { it.surah.surahNo == 18 }
        assertEquals(110, alKahf.surah.ayahCount)
        assertEquals(1, alKahf.savedCount)
    }

    // A saved id with no verse row, or a verse whose surah has no row, is
    // dropped rather than rendered as a nameless row.
    @Test
    fun savedVerseWithNoSurahRow_isDropped() {
        val rows = bookmarkSurahRows(
            surahs = listOf(surah(1)),
            savedVerses = listOf(verse(1, 1, 1), verse(9999, 99, 1)),
        )

        assertEquals(listOf(1), rows.map { it.surah.surahNo })
        assertEquals(1, rows.single().savedCount)
    }

    @Test
    fun rowsCarryTheVerseEntitiesThemselves_soTheListCanRenderTheirText() {
        val rows = bookmarkSurahRows(
            surahs = listOf(surah(1)),
            savedVerses = listOf(verse(1, 1, 1)),
        )

        val saved = rows.single().savedAyahs.single()
        assertEquals(1, saved.ayahNo)
        assertTrue(saved.arabicIndopak.isNotEmpty())
        assertTrue(saved.arabicUthmani.isNotEmpty())
    }
}
