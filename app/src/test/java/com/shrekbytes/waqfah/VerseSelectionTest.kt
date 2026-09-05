package com.shrekbytes.waqfah

import com.shrekbytes.waqfah.data.local.core.VerseEntity
import com.shrekbytes.waqfah.data.model.ReadingMode
import com.shrekbytes.waqfah.data.repository.VerseLookups
import com.shrekbytes.waqfah.data.repository.VerseSelection
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Tests verse selection at its interface: the four verbs over an in-memory
// backing, with a seeded Random so even the random paths are deterministic.
// Every everything-read fallback and both wrap-arounds are pinned here —
// callers (the reading machine, the Surahs-and-ayahs list) must never
// re-branch on these outcomes.
class VerseSelectionTest {

    private fun verse(id: Int, surahNo: Int, ayahNo: Int) = VerseEntity(
        id = id,
        surahNo = surahNo,
        ayahNo = ayahNo,
        arabicIndopak = "ar-$id",
        arabicUthmani = "ut-$id",
        bnTransliteration = "tr-bn-$id",
        enTransliteration = "tr-en-$id",
    )

    // Two surahs stand in for the mushaf: surah 1 holds ids 1..3, surah 2
    // holds ids 4..5, so in-surah scans and mushaf-wide wrap diverge.
    private val verses = listOf(
        verse(1, 1, 1),
        verse(2, 1, 2),
        verse(3, 1, 3),
        verse(4, 2, 1),
        verse(5, 2, 2),
    )

    private inner class FakeLookups(private val rows: List<VerseEntity> = verses) : VerseLookups {
        override suspend fun getVerseById(id: Int) = rows.firstOrNull { it.id == id }
        override suspend fun getAllVerseIds() = rows.map { it.id }
        override suspend fun getVerseIdsForSurah(surahNo: Int) =
            rows.filter { it.surahNo == surahNo }.map { it.id }
        override suspend fun getFirstVerse() = rows.firstOrNull()
        override suspend fun getLastVerse() = rows.lastOrNull()
        override suspend fun getNextVerse(afterId: Int) = rows.firstOrNull { it.id > afterId }
        override suspend fun getPreviousVerse(beforeId: Int) = rows.lastOrNull { it.id < beforeId }
    }

    private fun selection(rows: List<VerseEntity> = verses, seed: Int = 7) =
        VerseSelection(FakeLookups(rows), Random(seed))

    @Test
    fun sequentialStart_returnsLowestUnread() = runTest {
        assertEquals(3, selection().start(ReadingMode.SEQUENTIAL, setOf(1, 2, 4, 5))?.id)
    }

    @Test
    fun sequentialStart_allRead_fallsBackToFirstVerse() = runTest {
        assertEquals(1, selection().start(ReadingMode.SEQUENTIAL, setOf(1, 2, 3, 4, 5))?.id)
    }

    @Test
    fun sequentialStart_emptyStore_returnsNull() = runTest {
        assertNull(selection(rows = emptyList()).start(ReadingMode.SEQUENTIAL, emptySet()))
    }

    @Test
    fun randomStart_returnsAnUnreadVerse() = runTest {
        val id = selection().start(ReadingMode.RANDOM, setOf(1, 2, 3))?.id
        assertTrue(id in setOf(4, 5))
    }

    @Test
    fun randomStart_sameSeed_samePick() = runTest {
        val first = selection(seed = 7).start(ReadingMode.RANDOM, setOf(1, 2, 3))?.id
        val second = selection(seed = 7).start(ReadingMode.RANDOM, setOf(1, 2, 3))?.id
        assertEquals(first, second)
    }

    @Test
    fun randomStart_allRead_returnsAnyVerse() = runTest {
        val id = selection().start(ReadingMode.RANDOM, setOf(1, 2, 3, 4, 5))?.id
        assertTrue(id in setOf(1, 2, 3, 4, 5))
    }

    @Test
    fun randomStart_emptyStore_returnsNull() = runTest {
        assertNull(selection(rows = emptyList()).start(ReadingMode.RANDOM, emptySet()))
    }

    @Test
    fun continueInSurah_returnsFirstUnreadInSurah() = runTest {
        assertEquals(2, selection().continueInSurah(1, setOf(1, 4, 5))?.id)
    }

    @Test
    fun continueInSurah_surahAllRead_fallsBackToSurahFirstAyah() = runTest {
        assertEquals(1, selection().continueInSurah(1, setOf(1, 2, 3))?.id)
    }

    @Test
    fun continueInSurah_unknownSurah_returnsNull() = runTest {
        assertNull(selection().continueInSurah(114, emptySet()))
    }

    @Test
    fun next_lastVerse_wrapsToFirst() = runTest {
        assertEquals(1, selection().next(5)?.id)
    }

    @Test
    fun next_midMushaf_stepsById() = runTest {
        assertEquals(4, selection().next(3)?.id)
    }

    @Test
    fun previous_firstVerse_wrapsToLast() = runTest {
        assertEquals(5, selection().previous(1)?.id)
    }

    @Test
    fun previous_midMushaf_stepsById() = runTest {
        assertEquals(3, selection().previous(4)?.id)
    }

    @Test
    fun stepping_emptyStore_returnsNull() = runTest {
        val empty = selection(rows = emptyList())
        assertNull(empty.next(3))
        assertNull(empty.previous(3))
    }
}
