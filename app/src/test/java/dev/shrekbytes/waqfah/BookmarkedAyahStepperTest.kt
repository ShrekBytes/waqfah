package dev.shrekbytes.waqfah

import dev.shrekbytes.waqfah.data.bookmark.BookmarkCollection
import dev.shrekbytes.waqfah.data.local.core.VerseEntity
import dev.shrekbytes.waqfah.data.model.ReadingMode
import dev.shrekbytes.waqfah.data.repository.BookmarkedAyahStepper
import dev.shrekbytes.waqfah.data.repository.VerseLookups
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Tests the bookmarked-ayah stepper at its interface, the way VerseSelectionTest
// tests its sibling: which verse a fresh session opens on, and how next/previous
// move, wrap, and give up. Five verses stand in for the mushaf; the collection
// is the only thing that decides the walk, so every case states the collection
// and then asks what comes next.
class BookmarkedAyahStepperTest {

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

        // Quran order, like the store's: ascending id, not order of saving.
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
    private val stepper = BookmarkedAyahStepper(FakeLookups(), bookmarks)

    @Test
    fun start_opensOnTheCollectionFirstAyahInQuranOrder() = runTest {
        bookmarks.toggle(5)
        bookmarks.toggle(2)

        assertEquals(2, stepper.start(ReadingMode.SEQUENTIAL, emptySet())?.id)
    }

    // A bookmark is a bookmark whether or not it has been read, and the
    // sequential/random choice is the mushaf's question — not this walk's.
    @Test
    fun start_ignoresModeAndReadStatus() = runTest {
        bookmarks.toggle(3)

        assertEquals(3, stepper.start(ReadingMode.SEQUENTIAL, setOf(3))?.id)
        assertEquals(3, stepper.start(ReadingMode.RANDOM, setOf(1, 2, 3, 4, 5))?.id)
    }

    @Test
    fun start_emptyCollection_returnsNull() = runTest {
        assertNull(stepper.start(ReadingMode.SEQUENTIAL, emptySet()))
    }

    @Test
    fun next_stepsToTheNextSavedAyah_skippingUnsavedOnes() = runTest {
        bookmarks.toggle(1)
        bookmarks.toggle(4)

        assertEquals(4, stepper.next(1)?.id)
    }

    @Test
    fun next_lastSavedAyah_wrapsToTheFirst() = runTest {
        bookmarks.toggle(1)
        bookmarks.toggle(4)

        assertEquals(1, stepper.next(4)?.id)
    }

    @Test
    fun previous_firstSavedAyah_wrapsToTheLast() = runTest {
        bookmarks.toggle(1)
        bookmarks.toggle(4)

        assertEquals(4, stepper.previous(1)?.id)
    }

    @Test
    fun previous_stepsToThePreviousSavedAyah() = runTest {
        bookmarks.toggle(2)
        bookmarks.toggle(4)

        assertEquals(2, stepper.previous(4)?.id)
    }

    // One saved ayah is a collection of one: both directions land back on it
    // rather than dead-ending.
    @Test
    fun stepping_singleSavedAyah_staysOnIt() = runTest {
        bookmarks.toggle(3)

        assertEquals(3, stepper.next(3)?.id)
        assertEquals(3, stepper.previous(3)?.id)
    }

    @Test
    fun stepping_emptyCollection_returnsNull() = runTest {
        assertNull(stepper.next(3))
        assertNull(stepper.previous(3))
    }

    // The step is anchored on the ayah's id, not on its membership: an ayah
    // unsaved while it is on screen still steps into the collection rather
    // than dropping the walk.
    @Test
    fun stepping_fromAnAyahNoLongerSaved_stillLandsOnTheCollection() = runTest {
        bookmarks.toggle(1)
        bookmarks.toggle(5)

        assertEquals(5, stepper.next(2)?.id)
        assertEquals(1, stepper.previous(2)?.id)
    }
}
