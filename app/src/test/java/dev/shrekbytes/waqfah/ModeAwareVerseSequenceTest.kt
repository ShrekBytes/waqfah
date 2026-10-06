package dev.shrekbytes.waqfah

import dev.shrekbytes.waqfah.data.bookmark.BookmarkCollection
import dev.shrekbytes.waqfah.data.local.core.VerseEntity
import dev.shrekbytes.waqfah.data.model.ReadingMode
import dev.shrekbytes.waqfah.data.repository.BookmarkedAyahStepper
import dev.shrekbytes.waqfah.data.repository.ModeAwareVerseSequence
import dev.shrekbytes.waqfah.data.repository.VerseLookups
import dev.shrekbytes.waqfah.data.repository.VerseSelection
import kotlin.random.Random
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// The interstitial's sequence is the only one that is not a single collection:
// it is the mushaf or the bookmark collection, whichever the reading mode names,
// and it must latch that choice when a fresh session starts rather than read the
// mode on every verb. Both halves are already tested at their own interfaces
// (VerseSelectionTest, BookmarkedAyahStepperTest) — what is pinned here is only
// the choosing, which is the whole of this module's job.
class ModeAwareVerseSequenceTest {

    private val verses = (1..5).map { id ->
        VerseEntity(
            id = id,
            surahNo = 1,
            ayahNo = id,
            arabicIndopak = "ar-$id",
            arabicUthmani = "ut-$id",
            bnTransliteration = "tr-bn-$id",
            enTransliteration = "tr-en-$id",
        )
    }

    private class FakeBookmarkCollection : BookmarkCollection {
        private val saved = MutableStateFlow<Set<Int>>(emptySet())

        override val savedVerseIds: Flow<Set<Int>> = saved

        override suspend fun toggle(verseId: Int) {
            saved.update { if (verseId in it) it - verseId else it + verseId }
        }

        override suspend fun isSaved(verseId: Int): Boolean = verseId in saved.value

        override suspend fun savedVerseIdsSnapshot(): List<Int> = saved.value.sorted()
    }

    private inner class FakeLookups : VerseLookups {
        override suspend fun getVerseById(id: Int) = verses.firstOrNull { it.id == id }
        override suspend fun getAllVerseIds() = verses.map { it.id }
        override suspend fun getVerseIdsForSurah(surahNo: Int) = verses.map { it.id }
        override suspend fun getFirstVerse() = verses.firstOrNull()
        override suspend fun getLastVerse() = verses.lastOrNull()
        override suspend fun getNextVerse(afterId: Int) = verses.firstOrNull { it.id > afterId }
        override suspend fun getPreviousVerse(beforeId: Int) = verses.lastOrNull { it.id < beforeId }
    }

    private val bookmarks = FakeBookmarkCollection()
    private val lookups = FakeLookups()

    private fun sequence() = ModeAwareVerseSequence(
        mushaf = VerseSelection(lookups, Random(0)),
        bookmarks = BookmarkedAyahStepper(lookups, bookmarks),
    )

    @Test
    fun sequentialMode_walksTheMushaf() = runTest {
        val sequence = sequence()

        assertEquals(1, sequence.start(ReadingMode.SEQUENTIAL, emptySet())?.id)
        assertTrue(sequence.isMushafWide)
        assertEquals(2, sequence.next(1)?.id)
    }

    // Random picks a different *starting* verse; it is still the mushaf being
    // walked, so the session's mushaf-wide bookkeeping (completion, the
    // progress-reset re-land) applies exactly as it does under sequential.
    @Test
    fun randomMode_stillWalksTheMushaf() = runTest {
        val sequence = sequence()

        sequence.start(ReadingMode.RANDOM, emptySet())

        assertTrue(sequence.isMushafWide)
        assertEquals(2, sequence.next(1)?.id)
    }

    @Test
    fun bookmarksMode_walksTheCollection() = runTest {
        bookmarks.toggle(2)
        bookmarks.toggle(4)
        val sequence = sequence()

        assertEquals(2, sequence.start(ReadingMode.BOOKMARKS, emptySet())?.id)
        assertFalse(sequence.isMushafWide)
        // 3 is not saved, so the walk skips it — the collection, not the mushaf.
        assertEquals(4, sequence.next(2)?.id)
        assertEquals(2, sequence.previous(4)?.id)
    }

    @Test
    fun bookmarksMode_emptyCollection_returnsNull() = runTest {
        val sequence = sequence()

        assertNull(sequence.start(ReadingMode.BOOKMARKS, emptySet()))
        assertFalse(sequence.isMushafWide)
    }

    // The latch, which is the whole reason this is a class rather than a
    // preference read: the walk under way keeps its collection until the next
    // start() picks a new one. Without it a mode change mid-walk would send
    // next() into the other collection from an id belonging to the first.
    @Test
    fun switchingMode_landsOnTheNextStart_notTheWalkInProgress() = runTest {
        bookmarks.toggle(2)
        bookmarks.toggle(4)
        val sequence = sequence()

        sequence.start(ReadingMode.SEQUENTIAL, emptySet())
        assertTrue(sequence.isMushafWide)
        assertEquals(2, sequence.next(1)?.id) // still the mushaf

        sequence.start(ReadingMode.BOOKMARKS, emptySet())
        assertFalse(sequence.isMushafWide)
        assertEquals(4, sequence.next(2)?.id) // now the collection

        sequence.start(ReadingMode.SEQUENTIAL, emptySet())
        assertTrue(sequence.isMushafWide)
        assertEquals(2, sequence.next(1)?.id) // and back
    }

    // No walk has been chosen before the first start(), so there is nothing to
    // report but the mushaf. The session never reads this that early — it loads
    // a fresh session first — but the value must not be a lie if it does.
    @Test
    fun beforeAnyStart_reportsTheMushaf() {
        assertTrue(sequence().isMushafWide)
    }
}
