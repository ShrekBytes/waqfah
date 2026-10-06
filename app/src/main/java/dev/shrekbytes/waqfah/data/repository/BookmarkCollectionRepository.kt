package dev.shrekbytes.waqfah.data.repository

import androidx.room.withTransaction
import dev.shrekbytes.waqfah.data.bookmark.BookmarkCollection
import dev.shrekbytes.waqfah.data.local.appstate.BookmarkVerseEntity
import dev.shrekbytes.waqfah.data.local.appstate.WaqfahAppDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class BookmarkCollectionRepository @Inject constructor(
    private val appDatabase: WaqfahAppDatabase,
    // Same injectable wall clock as the other appstate repositories — the data
    // layer has one clock idiom, not one per repository.
    @Named("wallClock") private val nowWall: () -> Long,
) : BookmarkCollection {

    private val dao by lazy { appDatabase.bookmarkDao() }

    override val savedVerseIds: Flow<Set<Int>> =
        dao.observeSavedVerseIds().map { it.toSet() }

    // The read-and-mutate sequence stays inside Room's transaction seam so two
    // rapid taps on the toggle cannot both observe the same membership — the
    // same discipline MonitoredAppStateRepository.toggle applies.
    override suspend fun toggle(verseId: Int) {
        appDatabase.withTransaction {
            if (dao.exists(verseId)) {
                dao.remove(verseId)
            } else {
                dao.insertIfAbsent(BookmarkVerseEntity(verseId, savedAt = nowWall()))
            }
        }
    }

    override suspend fun isSaved(verseId: Int): Boolean = dao.exists(verseId)

    override suspend fun savedVerseIdsSnapshot(): List<Int> = dao.getAllSavedVerseIds()
}
