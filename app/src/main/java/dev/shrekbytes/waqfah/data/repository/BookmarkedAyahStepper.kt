package dev.shrekbytes.waqfah.data.repository

import dev.shrekbytes.waqfah.data.bookmark.BookmarkCollection
import dev.shrekbytes.waqfah.data.local.core.VerseEntity
import dev.shrekbytes.waqfah.data.model.ReadingMode
import javax.inject.Inject
import javax.inject.Singleton

// The bookmarks card's stepping (see CONTEXT.md's "Bookmarked-ayah stepper"):
// the collection-scoped sibling of VerseSelection, not an extension of it.
// Where VerseSelection answers "what verse comes next in the mushaf", this
// answers "what bookmarked verse comes next" — the collection in Quran order,
// wrapping inside it so stepping never dead-ends, and null when there is
// nothing saved. It reads the collection as one snapshot per verb and holds
// no copy of it, so an ayah saved or unsaved anywhere is picked up on the
// next step.
//
// Reading mode and read status are deliberately not its business: a bookmark
// is a bookmark whether it has been read or not, so start() ignores both and
// there is no everything-read fallback to fall back to. That is why it is a
// sibling module rather than a branch inside VerseSelection.
@Singleton
class BookmarkedAyahStepper @Inject constructor(
    private val lookups: VerseLookups,
    private val bookmarks: BookmarkCollection,
) : VerseSequence {

    override val isMushafWide = false

    // A fresh session opens on the collection's first ayah in Quran order —
    // the order the collection is stored in, never the order it was saved in.
    override suspend fun start(mode: ReadingMode, readIds: Set<Int>): VerseEntity? =
        bookmarks.savedVerseIdsSnapshot().firstOrNull()?.let { lookups.getVerseById(it) }

    override suspend fun next(afterId: Int): VerseEntity? {
        val saved = bookmarks.savedVerseIdsSnapshot()
        // Past the last saved ayah the walk wraps to the first — the collection
        // is a loop, not a dead end. Empty collection: no id, no verse.
        val id = saved.firstOrNull { it > afterId } ?: saved.firstOrNull() ?: return null
        return lookups.getVerseById(id)
    }

    override suspend fun previous(beforeId: Int): VerseEntity? {
        val saved = bookmarks.savedVerseIdsSnapshot()
        val id = saved.lastOrNull { it < beforeId } ?: saved.lastOrNull() ?: return null
        return lookups.getVerseById(id)
    }
}
