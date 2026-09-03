package com.shrekbytes.waqfah

import com.shrekbytes.waqfah.data.local.core.SurahEntity
import com.shrekbytes.waqfah.data.local.core.VerseEntity
import com.shrekbytes.waqfah.data.model.ReadingMode
import com.shrekbytes.waqfah.data.model.UserPreferences
import com.shrekbytes.waqfah.ui.reading.ReadingSession
import com.shrekbytes.waqfah.ui.theme.AppTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
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
// out. Every port is a fake over plain mutable state and virtual time drives
// the delays, so the machine's ordering — the mutex, the render skip, the
// mark-read-under-lock — is exercised exactly the way the reading card drives
// it in production. Five verses of one surah stand in for the mushaf.
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
    // probe; one firstUnread/randomUnread call happens per fresh session.
    private var surahQueries = 0
    private var startingVerseLoads = 0

    // Opened only by tests that stall inside the lock: the mark-read race and
    // the preference-emission-during-a-step ordering. Both are one-shot: a
    // render's preview probes call the same ports, and they must not stall.
    private var isReadDelayMs = 0L
    private var nextDelayMs = 0L

    private fun TestScope.session() = ReadingSession(
        preferences = prefs,
        downloadedIds = downloadedIds,
        progressReset = resetSignal,
        verseById = { id -> verses.firstOrNull { it.id == id } },
        nextVerse = {
            val stall = nextDelayMs
            nextDelayMs = 0
            if (stall > 0) delay(stall)
            val afterId = it
            verses.firstOrNull { v -> v.id > afterId } ?: verses.first()
        },
        previousVerse = { beforeId -> verses.lastOrNull { it.id < beforeId } ?: verses.last() },
        firstUnreadVerse = { startingVerseLoads++; verses.firstOrNull { it.id !in readIds } },
        randomUnreadVerse = { startingVerseLoads++; verses.filter { it.id !in readIds }.randomOrNull() ?: verses.random() },
        firstVerse = { verses.first() },
        surah = { surahQueries++; if (it == 1) surah1 else null },
        totalVerseCount = { verses.size },
        readVerseIds = { readIds.toList() },
        isRead = {
            if (isReadDelayMs > 0) delay(isReadDelayMs)
            it in readIds
        },
        markRead = { readIds += it },
        unmarkRead = { readIds -= it },
        countRead = { readIds.size },
        // Mirrors ReadingProgressRepository.resetAll: clears, then bumps the
        // signal the session's own collector watches.
        resetAll = { readIds.clear(); resetSignal.value++ },
        translationText = { meta, verseId -> translationTexts[meta.id to verseId] },
        setReadingMode = { setReadingModeCalls += it; prefs.value = prefs.value.copy(readingMode = it) },
        scope = backgroundScope,
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
}
