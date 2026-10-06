package dev.shrekbytes.waqfah

import dev.shrekbytes.waqfah.data.bookmark.BookmarkCollection
import dev.shrekbytes.waqfah.data.local.core.SurahEntity
import dev.shrekbytes.waqfah.data.local.core.VerseEntity
import dev.shrekbytes.waqfah.data.model.ReadingMode
import dev.shrekbytes.waqfah.data.model.TranslationMeta
import dev.shrekbytes.waqfah.data.model.UserPreferences
import dev.shrekbytes.waqfah.data.repository.BookmarkedAyahStepper
import dev.shrekbytes.waqfah.data.repository.VerseLookups
import dev.shrekbytes.waqfah.data.repository.VerseSelection
import dev.shrekbytes.waqfah.data.repository.VerseSequence
import dev.shrekbytes.waqfah.ui.reading.ReadingPorts
import dev.shrekbytes.waqfah.ui.reading.ReadingSession
import dev.shrekbytes.waqfah.ui.theme.AppTheme
import kotlin.random.Random
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Tests the ReadingSession at its interface: flows in, verbs called, uiState
// out. Every port is a fake over plain mutable state, verse movement goes
// through a real VerseSelection over a fake VerseLookups, and virtual time
// drives the delays, so the machine's ordering — the mutex, the render skip,
// the mark-read-under-lock — is exercised exactly the way the reading card
// drives it in production. Five verses of one surah stand in for the mushaf.
@OptIn(ExperimentalCoroutinesApi::class)
class ReadingSessionTest {

    private companion object {
        const val VERSE_COUNT = 5
        const val SAHIH = "sahih"
        const val PICKTHALL = "pickthall"
    }

    private val verses = (1..VERSE_COUNT).map { id ->
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
    private val surah1 = SurahEntity(
        id = 1,
        surahNo = 1,
        nameArabic = "الفاتحة",
        nameEnglish = "Al-Fatiha",
        nameBengali = "আল-ফাতিহা",
        ayahCount = VERSE_COUNT,
    )

    private val prefs = MutableStateFlow(UserPreferences())
    private val downloadedIds = MutableStateFlow(setOf(PICKTHALL))
    private val resetSignal = MutableStateFlow(0)

    private val readIds = mutableSetOf<Int>()
    private val translationTexts = mutableMapOf<Pair<String, Int>, String>()
    private val setReadingModeCalls = mutableListOf<ReadingMode>()

    // One surah probe happens per render, so its count is the render-skip
    // probe; one id-list scan happens per fresh session, so its count is the
    // reload probe.
    private var surahQueries = 0
    private var startingVerseLoads = 0

    // Opened only by tests that stall inside the lock: the mark-read race and
    // the preference-emission-during-a-step ordering. Both are one-shot: a
    // render's preview probes call the same lookups, and they must not stall.
    private var isReadDelayMs = 0L
    private var nextDelayMs = 0L

    // Opened only by the failed-write test, to simulate a Room write throwing
    // (disk pressure, SQLITE_BUSY, an I/O error). Null means writes succeed.
    private var writeFailure: Throwable? = null

    // The collection as the session sees it: one published set, plus the verbs.
    // Failing the bookmark write is a separate trap from the read-status one so
    // the two seams can be failed independently.
    private var bookmarkWriteFailure: Throwable? = null

    private val bookmarks = object : BookmarkCollection {
        private val saved = MutableStateFlow<Set<Int>>(emptySet())

        override val savedVerseIds: Flow<Set<Int>> = saved

        override suspend fun toggle(verseId: Int) {
            bookmarkWriteFailure?.let { throw it }
            saved.update { if (verseId in it) it - verseId else it + verseId }
        }

        override suspend fun isSaved(verseId: Int): Boolean = verseId in saved.value

        override suspend fun savedVerseIdsSnapshot(): List<Int> = saved.value.sorted()
    }

    private val lookups = object : VerseLookups {
        override suspend fun getVerseById(id: Int) = verses.firstOrNull { it.id == id }
        override suspend fun getAllVerseIds(): List<Int> {
            startingVerseLoads++
            return verses.map { it.id }
        }
        override suspend fun getVerseIdsForSurah(surahNo: Int) =
            verses.filter { it.surahNo == surahNo }.map { it.id }
        override suspend fun getFirstVerse() = verses.firstOrNull()
        override suspend fun getLastVerse() = verses.lastOrNull()
        override suspend fun getNextVerse(afterId: Int): VerseEntity? {
            val stall = nextDelayMs
            nextDelayMs = 0
            if (stall > 0) delay(stall)
            return verses.firstOrNull { v -> v.id > afterId }
        }
        override suspend fun getPreviousVerse(beforeId: Int) =
            verses.lastOrNull { it.id < beforeId }
    }

    // Failures that reach the session's host scope. Only the failed-write test
    // installs the trap: production (ReadingViewModel's
    // CoroutineExceptionHandler) catches the rethrown write failure so it gets
    // logged, and without an equivalent here runTest would fail the test as an
    // unhandled background exception instead. Every other test keeps the plain
    // backgroundScope, unchanged.
    private val capturedFailures = mutableListOf<Throwable>()

    private fun TestScope.session(
        scope: CoroutineScope = backgroundScope,
        // The sequence the session walks. Defaults to the mushaf-wide
        // selection, so every pre-existing test drives that path exactly as it
        // did before the Bookmarks tab existed; the collection-scoped tests
        // below hand it the bookmarked-ayah stepper instead (ADR-0005).
        sequence: VerseSequence = VerseSelection(lookups, Random(0)),
    ) = ReadingSession(
        preferences = prefs,
        downloadedIds = downloadedIds,
        progressReset = resetSignal,
        ports = object : ReadingPorts {
            override suspend fun verseById(id: Int) = verses.firstOrNull { it.id == id }
            override suspend fun surah(surahNo: Int): SurahEntity? {
                surahQueries++
                return if (surahNo == 1) surah1 else null
            }
            override suspend fun totalVerseCount() = verses.size
            override suspend fun readVerseIds() = readIds.toList()
            override suspend fun isRead(verseId: Int): Boolean {
                if (isReadDelayMs > 0) delay(isReadDelayMs)
                return verseId in readIds
            }
            override suspend fun markRead(verseId: Int) {
                writeFailure?.let { throw it }
                readIds += verseId
            }
            override suspend fun unmarkRead(verseId: Int) {
                writeFailure?.let { throw it }
                readIds -= verseId
            }
            override suspend fun countRead() = readIds.size
            // Mirrors ReadingProgressRepository.resetAll: clears, then bumps the
            // signal the session's own collector watches.
            override suspend fun resetAll() { readIds.clear(); resetSignal.value++ }
            override suspend fun translationText(meta: TranslationMeta, verseId: Int) = translationTexts[meta.id to verseId]
            override suspend fun setReadingMode(mode: ReadingMode) {
                setReadingModeCalls += mode
                prefs.value = prefs.value.copy(readingMode = mode)
            }
        },
        verseSelection = sequence,
        bookmarks = bookmarks,
        scope = scope,
    )

    @Test
    fun firstLoad_sequential_opensOnLowestUnread() = runTest {
        readIds += setOf(1, 2)
        val session = session()
        runCurrent()

        val state = session.uiState.value
        assertFalse(state.isLoading)
        assertEquals("1:3", state.ayahLabel)
        assertEquals("Al-Fatiha", state.surahName)
        assertEquals(1, startingVerseLoads)
    }

    @Test
    fun firstLoad_sequential_allRead_fallsBackToFirstVerse() = runTest {
        readIds += (1..VERSE_COUNT).toSet()
        val session = session()
        runCurrent()

        assertEquals("1:1", session.uiState.value.ayahLabel)
    }

    @Test
    fun firstLoad_random_opensOnUnreadVerse() = runTest {
        prefs.value = UserPreferences(readingMode = ReadingMode.RANDOM)
        readIds += setOf(1, 2, 3)
        val session = session()
        runCurrent()

        assertTrue(session.uiState.value.ayahLabel in setOf("1:4", "1:5"))
    }

    // The historical bug: computing the mark-read decision outside the lock
    // let a swipe committing mid-gesture apply ayah A's tap to ayah B. Here
    // the tap enters its critical section and stalls on the DB probe while
    // the swipe queues behind the mutex — the tap must land on A.
    @Test
    fun markRead_tapStalledInLock_appliesToTappedVerseNotSwipedTo() = runTest {
        val session = session()
        runCurrent()
        assertEquals("1:1", session.uiState.value.ayahLabel)

        isReadDelayMs = 100
        session.markCurrentRead()
        runCurrent() // the tap holds the mutex, suspended in its probe
        val stepped = launch { session.next() } // the swipe queues behind it
        runCurrent()
        advanceTimeBy(100) // the tap completes; only then does the swipe step
        runCurrent()
        stepped.join()

        assertEquals(setOf(1), readIds)
        assertEquals("1:2", session.uiState.value.ayahLabel)
        assertFalse(session.uiState.value.isMarkedRead)
    }

    // markCurrentRead() flips isMarkedRead optimistically before the write, so
    // a failed write must roll that flip back — otherwise the pill disagrees
    // with the database until some unrelated event re-renders. Failures here
    // are simulated at the port seam, not by forcing real SQLite errors.
    //
    // This test was run red against the unfixed markCurrentRead(): the write
    // threw, readIds stayed empty, and the pill was left flipped true. Every
    // other verse is pre-read so a successful write would have completed the
    // Quran — the failed write must report neither.
    @Test
    fun markRead_writeFails_revertsTheOptimisticFlip() = runTest {
        // Same failure trap production has: the rethrow must reach the host,
        // where ReadingViewModel's CoroutineExceptionHandler logs it. Without
        // this, runTest would fail the test as an unhandled background
        // exception instead of letting the assertions run.
        readIds += setOf(1, 2, 3, 4)
        val session = session(
            scope = CoroutineScope(
                backgroundScope.coroutineContext + SupervisorJob() +
                    CoroutineExceptionHandler { _, t -> capturedFailures += t },
            ),
        )
        runCurrent()
        assertEquals("1:5", session.uiState.value.ayahLabel)
        assertFalse(session.uiState.value.isMarkedRead)
        assertFalse(session.uiState.value.isCompleted)

        val failure = IllegalStateException("disk full")
        writeFailure = failure
        session.markCurrentRead()
        runCurrent()

        assertEquals(setOf(1, 2, 3, 4), readIds) // the write never landed
        assertFalse(session.uiState.value.isMarkedRead) // and the pill agrees
        assertFalse(session.uiState.value.isCompleted) // nor did completion open
        assertEquals(listOf(failure), capturedFailures) // still surfaced to the host
    }

    @Test
    fun preferenceEmissions_irrelevantSkipRender_relevantReRender() = runTest {
        val session = session()
        runCurrent()
        val queriesAfterLoad = surahQueries

        prefs.value = prefs.value.copy(theme = AppTheme.DARK) // not rendered by the card
        runCurrent()
        assertEquals(queriesAfterLoad, surahQueries)

        prefs.value = prefs.value.copy(arabicFontSize = 30) // rendered
        runCurrent()
        assertEquals(queriesAfterLoad + 1, surahQueries)
        assertEquals(30, session.uiState.value.arabicFontSize)
    }

    @Test
    fun persistedDefaultChange_clearsCompareOverride() = runTest {
        translationTexts += (SAHIH to 1) to "say it"
        translationTexts += (PICKTHALL to 1) to "say it, pickthall"
        val session = session()
        runCurrent()
        assertEquals("Sahih International", session.uiState.value.translationSourceName)

        session.cycleTranslationSource(forward = true)
        runCurrent()
        assertEquals("Pickthall", session.uiState.value.translationSourceName)

        // The stored default moves to a translation that is not on disk, so
        // the active one falls back to bundled sahih — and the session-local
        // peek at pickthall must not survive the default changing.
        prefs.value = prefs.value.copy(activeTranslationEnglish = "yusufali")
        runCurrent()
        assertEquals("Sahih International", session.uiState.value.translationSourceName)
    }

    @Test
    fun compareOverride_cyclingWraps_stepClearsIt_manualResetRestoresDefault() = runTest {
        translationTexts += (SAHIH to 1) to "say it"
        translationTexts += (PICKTHALL to 1) to "say it, pickthall"
        translationTexts += (SAHIH to 2) to "say it 2"
        val session = session()
        runCurrent()

        // Available for English: bundled sahih + downloaded pickthall —
        // cycling forward wraps around the pair.
        session.cycleTranslationSource(forward = true)
        runCurrent()
        assertEquals("Pickthall", session.uiState.value.translationSourceName)
        session.cycleTranslationSource(forward = true)
        runCurrent()
        assertEquals("Sahih International", session.uiState.value.translationSourceName)

        // A peek never outlives its ayah.
        session.cycleTranslationSource(forward = true)
        runCurrent()
        session.next()
        runCurrent()
        assertEquals("1:2", session.uiState.value.ayahLabel)
        assertEquals("Sahih International", session.uiState.value.translationSourceName)

        // Closing the switcher drops back to the real default.
        session.cycleTranslationSource(forward = true)
        runCurrent()
        session.resetTranslationSource()
        runCurrent()
        assertEquals("Sahih International", session.uiState.value.translationSourceName)
    }

    // The fresh-session paths (Start Again / Switch Mode / an external reset)
    // all land on a new ayah — the compare peek must not ride along, or the
    // fresh session opens on a translation the user never chose for it.
    @Test
    fun freshSessionAfterComparePeek_rendersTheDefaultTranslation() = runTest {
        translationTexts += (SAHIH to 1) to "say it"
        translationTexts += (PICKTHALL to 1) to "say it, pickthall"
        readIds += setOf(1, 2)
        val session = session()
        runCurrent()
        assertEquals("1:3", session.uiState.value.ayahLabel)
        assertEquals("Sahih International", session.uiState.value.translationSourceName)

        session.cycleTranslationSource(forward = true)
        runCurrent()
        assertEquals("Pickthall", session.uiState.value.translationSourceName)

        session.startOver()
        runCurrent()
        assertEquals("1:1", session.uiState.value.ayahLabel)
        assertEquals("Sahih International", session.uiState.value.translationSourceName)
    }

    @Test
    fun jumpToVerse_retargetsWithoutTouchingReadHistory_thenStepsByGlobalId() = runTest {
        readIds += 1
        val session = session()
        runCurrent()
        assertEquals("1:2", session.uiState.value.ayahLabel)

        session.jumpToVerse(4)
        runCurrent()
        assertEquals("1:4", session.uiState.value.ayahLabel)
        assertEquals(setOf(1), readIds)

        session.next()
        runCurrent()
        assertEquals("1:5", session.uiState.value.ayahLabel)
    }

    @Test
    fun startOver_clearsHistory_andStartsFresh() = runTest {
        readIds += setOf(1, 2)
        val session = session()
        runCurrent()
        assertEquals("1:3", session.uiState.value.ayahLabel)

        session.startOver()
        runCurrent()
        assertTrue(readIds.isEmpty())
        assertEquals("1:1", session.uiState.value.ayahLabel)
    }

    @Test
    fun switchModeAndRestart_persistsTheOtherMode_andRestarts() = runTest {
        readIds += setOf(1, 2)
        val session = session()
        runCurrent()

        session.switchModeAndRestart()
        runCurrent()

        assertEquals(listOf(ReadingMode.RANDOM), setReadingModeCalls)
        assertTrue(readIds.isEmpty())
        assertTrue(session.uiState.value.ayahLabel in (1..VERSE_COUNT).map { "1:$it" })
    }

    @Test
    fun completion_appearsWhenLastUnreadMarked_closeSticksForTheSession() = runTest {
        readIds += setOf(1, 2, 3, 4)
        val session = session()
        runCurrent()
        assertEquals("1:5", session.uiState.value.ayahLabel)
        assertFalse(session.uiState.value.isCompleted)

        session.markCurrentRead()
        runCurrent()
        assertTrue(session.uiState.value.isCompleted)

        session.dismissCompletion()
        runCurrent() // the dismissal applies under the lock
        assertFalse(session.uiState.value.isCompleted)

        // Close is latched for the rest of the session: even unmarking and
        // re-marking the last verse does not re-open the popup. Only a fresh
        // session (startOver / external reset) re-evaluates completion.
        session.markCurrentRead()
        runCurrent()
        session.markCurrentRead()
        runCurrent()
        assertFalse(session.uiState.value.isCompleted)
        assertEquals((1..VERSE_COUNT).toSet(), readIds)
    }

    // The session's own resetAll() echoes through progressReset; before the
    // echo suppression this landed as a second, redundant reload.
    @Test
    fun startOver_reloadsExactlyOnce() = runTest {
        readIds += setOf(1, 2)
        val session = session()
        runCurrent()
        assertEquals(1, startingVerseLoads)

        session.startOver()
        runCurrent()

        assertEquals("1:1", session.uiState.value.ayahLabel)
        assertEquals(2, startingVerseLoads)
    }

    @Test
    fun switchModeAndRestart_reloadsExactlyOnce_andEchoesTheMode() = runTest {
        readIds += setOf(1, 2)
        val session = session()
        runCurrent()

        session.switchModeAndRestart()
        runCurrent()

        assertEquals(listOf(ReadingMode.RANDOM), setReadingModeCalls)
        assertEquals(ReadingMode.RANDOM, session.uiState.value.readingMode)
        assertEquals(2, startingVerseLoads)
    }

    // The echo suppression must not over-suppress: a genuine external reset
    // (Reset progress in Settings) after a self-initiated one still reloads.
    @Test
    fun externalReset_afterSelfReset_stillReloads() = runTest {
        val session = session()
        runCurrent()
        assertEquals("1:1", session.uiState.value.ayahLabel)

        session.startOver()
        runCurrent()
        assertEquals(2, startingVerseLoads)

        readIds += setOf(1, 2) // wiped again by the external reset
        resetSignal.value++
        runCurrent()

        assertEquals("1:3", session.uiState.value.ayahLabel)
        assertEquals(3, startingVerseLoads)
    }

    // A preference emission landing while a step holds the lock must not
    // interleave with it: the step renders with the old preferences, then the
    // emission re-renders with the new ones — neither effect lost, latestPrefs
    // never read mid-write.
    @Test
    fun preferenceEmission_duringStalledStep_appliesAfterIt() = runTest {
        val session = session()
        runCurrent()
        assertEquals("1:1", session.uiState.value.ayahLabel)
        assertEquals(26, session.uiState.value.arabicFontSize)

        nextDelayMs = 100
        val stepped = launch { session.next() }
        runCurrent() // the step holds the mutex, suspended in its port
        prefs.value = prefs.value.copy(arabicFontSize = 30)
        runCurrent() // the emission's collector queues behind the mutex
        advanceTimeBy(100) // the step completes and renders with the old size
        runCurrent()
        stepped.join()
        runCurrent() // the queued emission re-renders with the new size

        assertEquals("1:2", session.uiState.value.ayahLabel)
        assertEquals(30, session.uiState.value.arabicFontSize)
        assertEquals(1, startingVerseLoads) // a prefs emission never reloads
    }

    @Test
    fun downloadedTranslationsEmission_refreshesSwitcherAvailabilityWithoutReloading() = runTest {
        downloadedIds.value = emptySet() // only bundled sahih available
        val session = session()
        runCurrent()
        assertFalse(session.uiState.value.translationHasAlternates)

        downloadedIds.value = setOf(PICKTHALL)
        runCurrent()

        assertTrue(session.uiState.value.translationHasAlternates)
        assertEquals("1:1", session.uiState.value.ayahLabel) // same ayah: re-render, no reload
        assertEquals(1, startingVerseLoads)
    }

    @Test
    fun bookmarkToggle_savesThenUnsavesTheCurrentAyah() = runTest {
        val session = session()
        runCurrent()
        assertEquals("1:1", session.uiState.value.ayahLabel)
        assertFalse(session.uiState.value.isSaved)

        session.toggleBookmark()
        runCurrent()
        assertTrue(session.uiState.value.isSaved)
        assertTrue(bookmarks.isSaved(1))

        session.toggleBookmark()
        runCurrent()
        assertFalse(session.uiState.value.isSaved)
        assertFalse(bookmarks.isSaved(1))
    }

    // Saved state is a fact about an ayah, not about the card: stepping has to
    // show whichever ayah arrived, never the one that just left.
    @Test
    fun bookmark_steppingShowsEachAyahsOwnState() = runTest {
        val session = session()
        runCurrent()
        session.toggleBookmark() // saves 1:1
        runCurrent()

        session.next()
        runCurrent()
        assertEquals("1:2", session.uiState.value.ayahLabel)
        assertFalse(session.uiState.value.isSaved)

        session.previous()
        runCurrent()
        assertEquals("1:1", session.uiState.value.ayahLabel)
        assertTrue(session.uiState.value.isSaved)
    }

    // The collection is published as one observable set precisely so a save
    // made on another surface — the interstitial, another card — reaches this
    // one. Nothing here taps this session's toggle: the set is the only signal.
    @Test
    fun bookmark_changeMadeElsewhere_landsWithoutAReload() = runTest {
        val session = session()
        runCurrent()
        assertFalse(session.uiState.value.isSaved)

        bookmarks.toggle(1) // as another card would
        runCurrent()

        assertTrue(session.uiState.value.isSaved)
        assertEquals("1:1", session.uiState.value.ayahLabel) // same ayah: no reload
        assertEquals(1, startingVerseLoads)
    }

    // The deliberate divergence from mark-read: the toggle never flips ahead of
    // the write. A failed write leaves the store holding the old truth, so the
    // ribbon must keep showing that truth rather than the state the tap hoped
    // for — there is no optimistic flip to roll back, and none to leak.
    @Test
    fun bookmark_writeFails_leavesTheToggleOnWhatIsStored() = runTest {
        val session = session(
            scope = CoroutineScope(
                backgroundScope.coroutineContext + SupervisorJob() +
                    CoroutineExceptionHandler { _, t -> capturedFailures += t },
            ),
        )
        runCurrent()
        assertFalse(session.uiState.value.isSaved)

        val failure = IllegalStateException("disk full")
        bookmarkWriteFailure = failure
        session.toggleBookmark()
        runCurrent()

        assertFalse(bookmarks.isSaved(1)) // the write never landed
        assertFalse(session.uiState.value.isSaved) // and the ribbon agrees
        assertEquals(listOf(failure), capturedFailures) // still surfaced to the host
    }

    // The Bookmarks tab's session: the same machine, handed the
    // collection-scoped stepper instead of the mushaf-wide selection. Every
    // test here is about what that one difference does — which ayah a fresh
    // session opens on, how stepping moves, what an empty collection shows,
    // and which of the mushaf's bookkeeping the walk is still subject to.

    private fun TestScope.bookmarksSession(
        scope: CoroutineScope = backgroundScope,
    ) = session(scope = scope, sequence = BookmarkedAyahStepper(lookups, bookmarks))

    // Quran order, not order of saving: 4 was saved first, but the collection
    // opens on 2.
    @Test
    fun bookmarksSession_opensOnTheCollectionFirstAyahInQuranOrder() = runTest {
        bookmarks.toggle(4)
        bookmarks.toggle(2)
        val session = bookmarksSession()
        runCurrent()

        assertFalse(session.uiState.value.isLoading)
        assertEquals("1:2", session.uiState.value.ayahLabel)
        assertTrue(session.uiState.value.isSaved)
    }

    @Test
    fun bookmarksSession_steppingMovesOnlyBetweenSavedAyahs_wrappingAtBothEnds() = runTest {
        bookmarks.toggle(1)
        bookmarks.toggle(4)
        val session = bookmarksSession()
        runCurrent()
        assertEquals("1:1", session.uiState.value.ayahLabel)

        session.next()
        runCurrent()
        assertEquals("1:4", session.uiState.value.ayahLabel) // 2 and 3 are skipped

        session.next()
        runCurrent()
        assertEquals("1:1", session.uiState.value.ayahLabel) // wraps forward

        session.previous()
        runCurrent()
        assertEquals("1:4", session.uiState.value.ayahLabel) // and back
    }

    // An empty collection is a state to present, not a load that never
    // resolves — the card must not sit on a skeleton forever.
    @Test
    fun bookmarksSession_emptyCollection_showsTheEmptyState() = runTest {
        val session = bookmarksSession()
        runCurrent()

        assertFalse(session.uiState.value.isLoading)
        assertTrue(session.uiState.value.isEmpty)
    }

    // The session's content *is* the collection, so unsaving the ayah on
    // screen leaves nothing to show: the empty state, not a blank card and not
    // an unrelated ayah.
    @Test
    fun bookmarksSession_unsavingTheShownAyah_landsOnTheEmptyState() = runTest {
        bookmarks.toggle(3)
        val session = bookmarksSession()
        runCurrent()
        assertEquals("1:3", session.uiState.value.ayahLabel)

        session.toggleBookmark()
        runCurrent()

        assertFalse(bookmarks.isSaved(3))
        assertFalse(session.uiState.value.isLoading)
        assertTrue(session.uiState.value.isEmpty)
    }

    // The other half of that rule: while anything is still saved, unsaving the
    // ayah on screen leaves the card where it is — the tab does not teleport
    // to another ayah, and the next swipe continues from where the reader was.
    @Test
    fun bookmarksSession_unsavingOneOfSeveral_leavesTheCardWhereItIs() = runTest {
        bookmarks.toggle(1)
        bookmarks.toggle(3)
        val session = bookmarksSession()
        runCurrent()
        assertEquals("1:1", session.uiState.value.ayahLabel)

        session.toggleBookmark()
        runCurrent()

        assertEquals("1:1", session.uiState.value.ayahLabel)
        assertFalse(session.uiState.value.isSaved)
        assertFalse(session.uiState.value.isEmpty)

        session.next()
        runCurrent()
        assertEquals("1:3", session.uiState.value.ayahLabel)
    }

    // Filling an empty collection from elsewhere — Home's card or the
    // interstitial — must bring the tab to life rather than leave it stuck on
    // the empty-state message.
    @Test
    fun bookmarksSession_savingWhileEmpty_loadsTheCollectionFirstAyah() = runTest {
        val session = bookmarksSession()
        runCurrent()
        assertTrue(session.uiState.value.isEmpty)

        bookmarks.toggle(5) // as another card would
        runCurrent()

        assertFalse(session.uiState.value.isEmpty)
        assertEquals("1:5", session.uiState.value.ayahLabel)
        assertTrue(session.uiState.value.isSaved)
    }

    // The completion popup and the progress-wiping "Start Again" it offers
    // belong to walking the whole Quran. A collection-scoped session must never
    // raise them, however much of the mushaf has been read (#19's closeout
    // note asked this ticket to decide; this is the decision).
    @Test
    fun bookmarksSession_neverReportsQuranCompletion() = runTest {
        readIds += (1..VERSE_COUNT).toSet()
        bookmarks.toggle(1)
        val session = bookmarksSession()
        runCurrent()

        assertFalse(session.uiState.value.isCompleted)
    }

    // Read progress is the mushaf's bookkeeping: wiping it in Settings moves
    // Home, and must leave the Bookmarks card exactly where it was.
    @Test
    fun bookmarksSession_progressResetFromSettings_doesNotMoveTheCard() = runTest {
        bookmarks.toggle(1)
        bookmarks.toggle(3)
        val session = bookmarksSession()
        runCurrent()
        session.next()
        runCurrent()
        assertEquals("1:3", session.uiState.value.ayahLabel)

        resetSignal.value++ // "Reset progress" in Settings
        runCurrent()

        // A reload would re-land the session on the collection's first ayah,
        // 1:1 — the card is still on 1:3, so nothing reloaded.
        assertEquals("1:3", session.uiState.value.ayahLabel)
    }

    // The collection is not read progress: marking a saved ayah read must not
    // take it out of the tab.
    @Test
    fun bookmarksSession_markingASavedAyahRead_leavesItInTheCollection() = runTest {
        bookmarks.toggle(1)
        val session = bookmarksSession()
        runCurrent()

        session.markCurrentRead()
        runCurrent()

        assertEquals(setOf(1), readIds)
        assertTrue(session.uiState.value.isSaved)
        assertTrue(bookmarks.isSaved(1))
    }

    // Two hosts, two sessions, two positions: this is what makes the tabs
    // independent, so moving one card must never move the other (ADR-0005).
    @Test
    fun homeAndBookmarksSessions_holdIndependentPositions() = runTest {
        bookmarks.toggle(3)
        bookmarks.toggle(5)
        val home = session()
        val bookmarksTab = bookmarksSession()
        runCurrent()
        assertEquals("1:1", home.uiState.value.ayahLabel)
        assertEquals("1:3", bookmarksTab.uiState.value.ayahLabel)

        home.next()
        runCurrent()
        assertEquals("1:2", home.uiState.value.ayahLabel)
        assertEquals("1:3", bookmarksTab.uiState.value.ayahLabel)

        bookmarksTab.next()
        runCurrent()
        assertEquals("1:5", bookmarksTab.uiState.value.ayahLabel)
        assertEquals("1:2", home.uiState.value.ayahLabel)
    }
}
