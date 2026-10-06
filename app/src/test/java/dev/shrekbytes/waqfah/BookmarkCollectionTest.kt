package dev.shrekbytes.waqfah

import dev.shrekbytes.waqfah.data.bookmark.BookmarkCollection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Pins the bookmark collection's contract at the seam every surface consumes:
// the collection reads as one observable set, saving twice does not duplicate,
// unsaving removes, and the snapshot agrees with the flow. The store itself is
// the seam, so this drives a fake of it — persistence and the real SQL are the
// instrumented suite's job (BookmarkCollectionRepositoryInstrumentedTest).
@OptIn(ExperimentalCoroutinesApi::class)
class BookmarkCollectionTest {

    private class FakeBookmarkCollection : BookmarkCollection {
        private val saved = MutableStateFlow<Set<Int>>(emptySet())

        // Quran order: ascending verse id, not order of saving.
        override val savedVerseIds: Flow<Set<Int>> = saved.map { it.toSortedSet() }

        override suspend fun toggle(verseId: Int) {
            saved.update { if (verseId in it) it - verseId else it + verseId }
        }

        override suspend fun isSaved(verseId: Int): Boolean = saved.value.contains(verseId)

        override suspend fun savedVerseIdsSnapshot(): List<Int> = saved.value.sorted()
    }

    private val collection = FakeBookmarkCollection()

    @Test
    fun emptyCollection_readsAsAnEmptySet() = runTest {
        assertEquals(emptySet<Int>(), collection.savedVerseIds.first())
        assertFalse(collection.isSaved(7))
    }

    @Test
    fun toggle_savesThenUnsaves() = runTest {
        collection.toggle(7)
        assertTrue(collection.isSaved(7))
        assertEquals(setOf(7), collection.savedVerseIds.first())

        collection.toggle(7)
        assertFalse(collection.isSaved(7))
        assertEquals(emptySet<Int>(), collection.savedVerseIds.first())
    }

    @Test
    fun savingTwice_isNotAnError_andDoesNotDuplicate() = runTest {
        collection.toggle(7)
        collection.toggle(7)
        collection.toggle(7)

        assertTrue(collection.isSaved(7))
        assertEquals(listOf(7), collection.savedVerseIdsSnapshot())
    }

    // The observable set is load-bearing: a change to the collection emits to a
    // suspended collector with no manual refresh and no second read.
    @Test
    fun collection_emitsToACollector_onEveryChange() = runTest {
        val seen = mutableListOf<Set<Int>>()
        val collector = launch { collection.savedVerseIds.collect { seen += it } }
        runCurrent()
        assertEquals(listOf(emptySet<Int>()), seen)

        collection.toggle(4)
        runCurrent()
        collection.toggle(9)
        runCurrent()
        collection.toggle(9)
        runCurrent()
        collector.cancel()

        assertEquals(listOf(emptySet<Int>(), setOf(4), setOf(4, 9), setOf(4)), seen)
    }

    @Test
    fun snapshot_isQuranOrder_regardlessOfSaveOrder() = runTest {
        collection.toggle(9)
        collection.toggle(4)
        collection.toggle(7)

        assertEquals(listOf(4, 7, 9), collection.savedVerseIdsSnapshot())
        assertEquals(setOf(4, 7, 9), collection.savedVerseIds.first())
    }
}
