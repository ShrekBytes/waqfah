package dev.shrekbytes.waqfah.data.repository

import dev.shrekbytes.waqfah.data.local.core.VerseEntity
import dev.shrekbytes.waqfah.data.model.ReadingMode
import javax.inject.Inject

// The interstitial's verse sequence (see CONTEXT.md's "Verse sequence"): the
// mushaf, or the bookmark collection in Quran order, chosen by the reading mode.
// The two sequences it composes are single-collection by construction; this is
// the composite the pause screen needs, because the mode chip is a live user
// setting there and that screen alone is asked to follow it.
//
// Which one it walks is latched at start() rather than read per verb. The mode
// arrives as an argument to start() and nowhere else, and latching is what makes
// "changing the chip lands on the next fresh session" true of the sequence as
// well as of the session: the walk already under way keeps the collection it
// began on, so next()/previous() can never straddle the two because the mode
// changed mid-step. The session reaches isMushafWide before it steps, so the
// latch is always set by the time anything reads it.
//
// The latch is a plain field, not @Volatile, and this class does no locking of
// its own: every read and write of it happens under the session's
// mutationMutex — start() is reached from loadStartingVerse, which the session
// only calls holding that lock, and isMushafWide is read from the collectors and
// render(), which take the same lock. It is therefore safe only inside one
// session and must never be shared between them.
//
// Deliberately not @Singleton: it holds the latched choice, so two sessions must
// never share one instance. Hilt hands every injection site a fresh one, which
// is exactly one per interstitial session.
class ModeAwareVerseSequence @Inject constructor(
    private val mushaf: VerseSelection,
    private val bookmarks: BookmarkedAyahStepper,
) : VerseSequence {

    private var walksBookmarks = false

    // Before the first start() this reports the mushaf. That is the honest
    // answer — no walk has been chosen yet — and it is also the one every
    // reader wants: the session only asks after a fresh session has loaded.
    override val isMushafWide: Boolean get() = !walksBookmarks

    override suspend fun start(mode: ReadingMode, readIds: Set<Int>): VerseEntity? {
        walksBookmarks = mode == ReadingMode.BOOKMARKS
        return active().start(mode, readIds)
    }

    override suspend fun next(afterId: Int): VerseEntity? = active().next(afterId)

    override suspend fun previous(beforeId: Int): VerseEntity? = active().previous(beforeId)

    private fun active(): VerseSequence = if (walksBookmarks) bookmarks else mushaf
}
