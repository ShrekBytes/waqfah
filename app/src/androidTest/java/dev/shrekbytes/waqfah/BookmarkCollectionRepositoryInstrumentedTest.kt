package dev.shrekbytes.waqfah

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.shrekbytes.waqfah.data.local.appstate.BookmarkVerseEntity
import dev.shrekbytes.waqfah.data.local.appstate.WaqfahAppDatabase
import dev.shrekbytes.waqfah.data.repository.BookmarkCollectionRepository
import dev.shrekbytes.waqfah.data.repository.ReadingProgressRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

// Tests the Room adapter behind BookmarkCollection. The database is in memory,
// while the real schema and transaction behavior remain the same as production.
@RunWith(AndroidJUnit4::class)
class BookmarkCollectionRepositoryInstrumentedTest {

    private lateinit var database: WaqfahAppDatabase
    private var now = 1_000L
    private lateinit var collection: BookmarkCollectionRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WaqfahAppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        collection = BookmarkCollectionRepository(database) { now }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun toggle_savesThenUnsaves_andReadsBackThroughMembership() = runBlocking {
        collection.toggle(7)
        assertTrue(collection.isSaved(7))
        assertEquals(setOf(7), collection.savedVerseIds.first())

        collection.toggle(7)
        assertFalse(collection.isSaved(7))
        assertEquals(emptySet<Int>(), collection.savedVerseIds.first())
    }

    @Test
    fun savingTwice_isNotAnError_andDoesNotDuplicate() = runBlocking {
        collection.toggle(7)

        now = 9_000L
        collection.toggle(7)
        collection.toggle(7)

        assertEquals(listOf(7), collection.savedVerseIdsSnapshot())
        assertEquals(setOf(7), collection.savedVerseIds.first())
    }

    // The IGNORE path on its own, without a toggle masking it: two inserts of
    // the same verse leave one row, and the first write's saved_at survives —
    // a repeated save must not rewrite it.
    @Test
    fun insertIfAbsent_twice_keepsOneRowAndTheOriginalSavedAt() = runBlocking {
        val dao = database.bookmarkDao()
        dao.insertIfAbsent(BookmarkVerseEntity(7, savedAt = 1_000L))
        dao.insertIfAbsent(BookmarkVerseEntity(7, savedAt = 9_000L))

        assertEquals(listOf(7), collection.savedVerseIdsSnapshot())
        database.openHelper.readableDatabase
            .query("SELECT saved_at FROM bookmark_verses WHERE verse_id = 7")
            .use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals(1_000L, cursor.getLong(0))
            }
    }

    @Test
    fun observableSet_republishesOnEveryChange() = runBlocking {
        assertEquals(emptySet<Int>(), collection.savedVerseIds.first())

        collection.toggle(4)
        assertEquals(setOf(4), collection.savedVerseIds.first())

        collection.toggle(9)
        assertEquals(setOf(4, 9), collection.savedVerseIds.first())

        collection.toggle(4)
        assertEquals(setOf(9), collection.savedVerseIds.first())
    }

    @Test
    fun snapshot_isQuranOrder_regardlessOfSaveOrder() = runBlocking {
        collection.toggle(9)
        collection.toggle(4)
        collection.toggle(7)

        assertEquals(listOf(4, 7, 9), collection.savedVerseIdsSnapshot())
    }

    @Test
    fun concurrentToggles_areSerializedByTheRoomTransaction() = runBlocking {
        coroutineScope {
            listOf(
                launch(Dispatchers.Default) { collection.toggle(7) },
                launch(Dispatchers.Default) { collection.toggle(7) },
            ).joinAll()
        }

        assertEquals(emptySet<Int>(), collection.savedVerseIds.first())
    }

    // The one thing this table must never do: resetting read progress wipes read
    // history and must leave the collection untouched (see CONTEXT.md's Bookmark
    // collection — the two facts are independent in both directions).
    @Test
    fun resettingProgress_leavesTheCollectionIntact() = runBlocking {
        val progress = ReadingProgressRepository(database) { now }
        collection.toggle(7)
        collection.toggle(9)
        progress.markRead(7)
        progress.markRead(9)
        assertEquals(2, progress.countRead())

        progress.resetAll()

        assertEquals(0, progress.countRead())
        assertEquals(listOf(7, 9), collection.savedVerseIdsSnapshot())
        assertTrue(collection.isSaved(7))
    }

    // The reverse direction: removing a bookmark is the only thing that removes
    // a bookmark — marking a saved verse read never does.
    @Test
    fun markingASavedVerseRead_doesNotRemoveIt() = runBlocking {
        val progress = ReadingProgressRepository(database) { now }
        collection.toggle(7)

        progress.markRead(7)

        assertEquals(listOf(7), collection.savedVerseIdsSnapshot())
        assertTrue(collection.isSaved(7))
    }

    // Persistence across a process restart is what the table buys: reopen the
    // same file and the collection is still there. In-memory Room can't be
    // reopened, so this uses a real on-disk database.
    @Test
    fun collection_survivesAClosedAndReopenedDatabase() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "bookmark-persistence-test.db"
        context.deleteDatabase(name)
        try {
            val first = Room.databaseBuilder(context, WaqfahAppDatabase::class.java, name).build()
            BookmarkCollectionRepository(first) { now }.apply {
                toggle(7)
                toggle(9)
            }
            first.close()

            val second = Room.databaseBuilder(context, WaqfahAppDatabase::class.java, name).build()
            try {
                val reopened = BookmarkCollectionRepository(second) { now }
                assertEquals(listOf(7, 9), reopened.savedVerseIdsSnapshot())
                assertTrue(reopened.isSaved(9))
            } finally {
                second.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
