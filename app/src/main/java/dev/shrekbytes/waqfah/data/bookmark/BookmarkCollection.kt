package dev.shrekbytes.waqfah.data.bookmark

import kotlinx.coroutines.flow.Flow

// The user's bookmark collection (see CONTEXT.md): one verse each, in Quran
// order, separate from read progress in both directions — marking a verse read
// never removes it, and wiping progress never empties it. Same split as
// monitored-app state: this is the domain seam, the Room adapter behind it
// (data/repository/BookmarkCollectionRepository) owns the storage.
//
// Only the bookmark toggle changes the collection.
interface BookmarkCollection {
    // The collection as one observable set of verse ids. Every surface that
    // shows an ayah's saved state reads this rather than caching its own copy,
    // so an ayah saved on the interstitial cannot show as unsaved on Home.
    val savedVerseIds: Flow<Set<Int>>

    // Save a verse to, or remove it from, the collection. Saving an already
    // saved verse is not an error and must not duplicate it.
    suspend fun toggle(verseId: Int)

    // Membership query for one verse.
    suspend fun isSaved(verseId: Int): Boolean

    // Snapshot read of the whole collection, in Quran order (ascending verse
    // id), for callers that need it in one shot rather than as a flow.
    suspend fun savedVerseIdsSnapshot(): List<Int>
}
