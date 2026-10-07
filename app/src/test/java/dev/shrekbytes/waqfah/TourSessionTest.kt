package dev.shrekbytes.waqfah

import dev.shrekbytes.waqfah.ui.tour.TOUR_STEP_TASKS
import dev.shrekbytes.waqfah.ui.tour.TourBackResult
import dev.shrekbytes.waqfah.ui.tour.TourHost
import dev.shrekbytes.waqfah.ui.tour.TourReadingFacts
import dev.shrekbytes.waqfah.ui.tour.TourSession
import dev.shrekbytes.waqfah.ui.tour.TourTaskKind
import dev.shrekbytes.waqfah.ui.tour.TourUiState
import dev.shrekbytes.waqfah.ui.tour.tourVisible
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Tests the TourSession at its interface: reading facts pushed in, verbs
// called, uiState out — the same seam the overlay and MainScreen drive. The
// pinned invariants are the ones the overlay's composable comments used to
// confess: the anchor's blank-state guard, completion-by-jump-only, the
// reset-on-re-entry rule, the back priority, and the skip-persists-nothing
// gate (ADR-0003).
@OptIn(ExperimentalCoroutinesApi::class)
class TourSessionTest {

    private val completed = MutableStateFlow<Boolean?>(null)

    private fun TestScope.session(
        tasks: List<TourTaskKind?>,
        onFinished: () -> Unit = {},
    ): TourSession {
        val s = TourSession(tasks, completed, onFinished, backgroundScope)
        runCurrent()
        return s
    }

    private fun facts(
        isLoading: Boolean = false,
        ayahLabel: String = "2:255",
        translationSourceName: String? = "Sahih",
        translationText: String? = "text",
        markReadCount: Int = 0,
        hasTranslationAlternates: Boolean = true,
    ) = TourReadingFacts(
        isLoading,
        ayahLabel,
        translationSourceName,
        translationText,
        markReadCount,
        hasTranslationAlternates,
    )

    // #33: with auto-next on, the mark-read tap also moves the card, so the
    // transient isMarkedRead=true can be conflated away before the facts feed
    // sees it — and a state-based check auto-completed the step with no user
    // action whenever the anchor ayah was already read. The step therefore
    // keys on the monotonic mark counter: done ⇔ a mark landed past the
    // anchor, however fast the card moved on. (Replaces the old state-based
    // test `markRead_completesOnlyWhenAResolvedCardShowsRead`: its final
    // assertion — the marked state completing the step — is precisely the
    // contract this change retires.)
    @Test
    fun markRead_completesWhenAMarkLandsPastTheAnchor() = runTest {
        val s = session(listOf(TourTaskKind.MARK_READ))
        s.onReadingState(facts(isLoading = true, markReadCount = 7))
        assertFalse(s.uiState.value.taskDone) // loading: never the anchor, never done
        s.onReadingState(facts(markReadCount = 7)) // resolved: anchor = 7
        assertFalse(s.uiState.value.taskDone)
        s.onReadingState(facts(markReadCount = 8)) // a mark happened
        assertTrue(s.uiState.value.taskDone)
    }

    // A swipe or a jump that merely changes the ayah never completes the
    // step: only a mark does. Auto-next's advance carries a mark with it,
    // which is exactly what the counter captures.
    @Test
    fun markRead_changingTheAyahWithoutMarking_doesNotComplete() = runTest {
        val s = session(listOf(TourTaskKind.MARK_READ))
        s.onReadingState(facts(ayahLabel = "2:255", markReadCount = 3))
        s.onReadingState(facts(ayahLabel = "2:256", markReadCount = 3))
        assertFalse(s.uiState.value.taskDone)
    }

    @Test
    fun markRead_reenteringTheStep_reTakesTheAnchor() = runTest {
        val s = session(listOf(TourTaskKind.MARK_READ, null))
        s.onReadingState(facts(markReadCount = 0))
        s.onReadingState(facts(markReadCount = 1))
        assertTrue(s.uiState.value.taskDone)
        s.next()
        s.back() // re-enter MARK_READ: the anchor re-takes at the current count
        assertFalse(s.uiState.value.taskDone)
    }

    @Test
    fun changeAyah_anchorsWhenTheStepBecomesCurrent() = runTest {
        val s = session(listOf(TourTaskKind.CHANGE_AYAH))
        s.onReadingState(facts(ayahLabel = "2:255"))
        s.next()
        assertFalse(s.uiState.value.taskDone)
        s.onReadingState(facts(ayahLabel = "2:256"))
        assertTrue(s.uiState.value.taskDone)
    }

    // The overlay's old bug: a snapshot taken while the card was still blank
    // made the resolved card look like a change. The anchor must be taken —
    // or re-taken — only once loading resolves.
    @Test
    fun changeAyah_anchorFromALoadingCard_isRetakenOnceLoadingResolves() = runTest {
        val s = session(listOf(TourTaskKind.CHANGE_AYAH))
        s.onReadingState(facts(isLoading = true, ayahLabel = ""))
        s.next() // entered while loading
        s.onReadingState(facts(isLoading = false, ayahLabel = "2:255"))
        assertFalse(s.uiState.value.taskDone)
        s.onReadingState(facts(ayahLabel = "2:256"))
        assertTrue(s.uiState.value.taskDone)
    }

    @Test
    fun changeAyah_reloadingMidStep_reTakesTheAnchor() = runTest {
        val s = session(listOf(TourTaskKind.CHANGE_AYAH))
        s.onReadingState(facts(ayahLabel = "2:255"))
        s.next()
        s.onReadingState(facts(ayahLabel = "2:256"))
        assertTrue(s.uiState.value.taskDone)
        // a mid-step reload resolves elsewhere: the anchor moves with it
        s.onReadingState(facts(isLoading = true, ayahLabel = "2:256"))
        s.onReadingState(facts(isLoading = false, ayahLabel = "2:257"))
        assertFalse(s.uiState.value.taskDone)
        s.onReadingState(facts(ayahLabel = "2:258"))
        assertTrue(s.uiState.value.taskDone)
    }

    @Test
    fun goToAyah_completesOnlyByAJumpInsideThePicker() = runTest {
        val s = session(listOf(TourTaskKind.GO_TO_AYAH))
        s.onReadingState(facts())
        s.next()
        // a swipe-only ayah change can never complete the step
        s.onReadingState(facts(ayahLabel = "2:256"))
        assertFalse(s.uiState.value.taskDone)
        // nor can opening the picker, or closing it without jumping
        s.openGoToPicker()
        assertTrue(s.uiState.value.goToPickerOpen)
        s.closeGoToPicker()
        assertFalse(s.uiState.value.taskDone)
        s.openGoToPicker()
        s.onJumpedInPicker()
        assertTrue(s.uiState.value.taskDone)
        assertFalse(s.uiState.value.goToPickerOpen)
    }

    @Test
    fun goToAyah_reenteringTheStep_resetsCompletionAndPicker() = runTest {
        val s = session(listOf(null, TourTaskKind.GO_TO_AYAH, null))
        s.onReadingState(facts())
        s.next() // go-to step
        s.onJumpedInPicker()
        assertTrue(s.uiState.value.taskDone)
        s.next()
        s.back() // re-enter the go-to step
        assertFalse(s.uiState.value.taskDone)
        assertFalse(s.uiState.value.goToPickerOpen)
    }

    // The regression the old overlay comment confessed: a reset keyed on
    // recomposition would wipe the jump flag right after the jump, because a
    // jump recomposes without a step change. Only a genuine step change resets.
    @Test
    fun goToAyah_pickerChurn_neverResetsAJump() = runTest {
        val s = session(listOf(TourTaskKind.GO_TO_AYAH))
        s.onReadingState(facts())
        s.next()
        s.openGoToPicker()
        s.onJumpedInPicker()
        assertTrue(s.uiState.value.taskDone)
        s.openGoToPicker()
        s.closeGoToPicker()
        assertTrue(s.uiState.value.taskDone)
    }

    @Test
    fun back_closesPicker_thenStepsBack_thenSkips() = runTest {
        val s = session(listOf(null, TourTaskKind.GO_TO_AYAH, null))
        s.onReadingState(facts())
        s.next()
        s.openGoToPicker()
        assertEquals(TourBackResult.CLOSE_PICKER, s.back())
        assertEquals(1, s.uiState.value.stepIndex)
        assertEquals(TourBackResult.PREVIOUS_STEP, s.back())
        assertEquals(0, s.uiState.value.stepIndex)
        assertEquals(TourBackResult.SKIPPED, s.back())
        assertFalse(tourVisible(TourHost.HOME, s.uiState.value))
    }

    // ADR-0003: skipping persists nothing — the tour re-offers next launch.
    @Test
    fun skip_dismissesAndNeverPersists() = runTest {
        var finished = 0
        completed.value = false
        val s = session(listOf(null), onFinished = { finished++ })
        s.onOpenedManually()
        assertTrue(tourVisible(TourHost.FAQ, s.uiState.value))
        s.skip()
        assertFalse(tourVisible(TourHost.FAQ, s.uiState.value))
        assertEquals(0, finished)
    }

    // ADR-0003's finish half: a skipped tour the user reopens manually can
    // still be finished — the finish budget belongs to the showing, not to
    // the session-wide skip flag.
    @Test
    fun skip_thenManualReopen_finishPersists() = runTest {
        var finished = 0
        completed.value = false
        val s = session(listOf(TourTaskKind.MARK_READ, null), onFinished = { finished++ })
        s.onOpenedManually()
        s.skip()
        s.onOpenedManually()

        s.next() // walk to the last step
        s.next() // finish

        assertEquals(1, finished)
        assertFalse(tourVisible(TourHost.FAQ, s.uiState.value))
    }

    // Finishing one showing doesn't consume a later manual showing's Finish,
    // but within one showing the button still persists exactly once.
    @Test
    fun finish_thenManualReopen_finishesOnceMore() = runTest {
        var finished = 0
        completed.value = false
        val s = session(listOf(null), onFinished = { finished++ })
        s.next() // the single step is the last: finish
        assertEquals(1, finished)

        s.onOpenedManually()
        s.next() // finish again
        s.next() // the latch must swallow repeats before the overlay tears down
        assertEquals(2, finished)
    }

    @Test
    fun finishOnLastStep_persistsOnceAndDismisses() = runTest {
        var finished = 0
        val s = session(listOf(null), onFinished = { finished++ })
        // Unresolved preferences: the gate waits instead of flashing the tour.
        assertFalse(tourVisible(TourHost.HOME, s.uiState.value))
        completed.value = false
        runCurrent()
        assertTrue(tourVisible(TourHost.HOME, s.uiState.value))
        s.next() // the only step is the last one: finishing
        assertEquals(1, finished)
        assertFalse(tourVisible(TourHost.HOME, s.uiState.value))
    }

    @Test
    fun gate_manualOpenShowsOnFaqOnly_evenAfterCompletion() = runTest {
        completed.value = true
        val s = session(listOf(null))
        // finished once: auto-show off...
        assertFalse(tourVisible(TourHost.HOME, s.uiState.value))
        assertFalse(tourVisible(TourHost.FAQ, s.uiState.value))
        // ...but a manual open still shows — where it was opened, never Home
        s.onOpenedManually()
        assertTrue(tourVisible(TourHost.FAQ, s.uiState.value))
        assertFalse(tourVisible(TourHost.HOME, s.uiState.value))
    }

    @Test
    fun gate_autoShowsOnHomeOnly_andNeverAlongsideAManualOpen() = runTest {
        assertTrue(tourVisible(TourHost.HOME, TourUiState(autoShowAllowed = true)))
        assertFalse(tourVisible(TourHost.FAQ, TourUiState(autoShowAllowed = true)))
        // An unfinished tour opened from FAQ shows there alone: one overlay
        // at a time, even while auto-show is still allowed.
        val both = TourUiState(autoShowAllowed = true, openedManually = true)
        assertTrue(tourVisible(TourHost.FAQ, both))
        assertFalse(tourVisible(TourHost.HOME, both))
    }

    @Test
    fun switchTranslation_anchorsOnTheShownTranslation() = runTest {
        val s = session(listOf(TourTaskKind.SWITCH_TRANSLATION))
        s.onReadingState(facts(translationSourceName = "Sahih"))
        s.next()
        s.onReadingState(facts(translationSourceName = "Pickthall"))
        assertTrue(s.uiState.value.taskDone)
        // the anchor keys on the source name, not the text shown under it
        s.onReadingState(facts(translationSourceName = "Pickthall", translationText = "other"))
        assertTrue(s.uiState.value.taskDone)
    }

    @Test
    fun switchTranslation_unnamedTranslation_anchorsOnTheText() = runTest {
        val s = session(listOf(TourTaskKind.SWITCH_TRANSLATION))
        s.onReadingState(facts(translationSourceName = null, translationText = "t1"))
        s.next()
        assertFalse(s.uiState.value.taskDone)
        s.onReadingState(facts(translationSourceName = null, translationText = "t2"))
        assertTrue(s.uiState.value.taskDone)
    }

    @Test
    fun translationHints_disabledWhenMissing_fallbackWhenNoAlternates() = runTest {
        val s = session(listOf(TourTaskKind.SWITCH_TRANSLATION))
        s.onReadingState(facts(isLoading = true, translationText = null))
        assertFalse(s.uiState.value.isTranslationDisabled) // waits out loading
        s.onReadingState(facts(isLoading = false, translationText = null))
        assertTrue(s.uiState.value.isTranslationDisabled)
        assertFalse(s.uiState.value.showTranslationFallback)
        s.onReadingState(facts(translationText = "t", hasTranslationAlternates = false))
        assertFalse(s.uiState.value.isTranslationDisabled)
        assertTrue(s.uiState.value.showTranslationFallback)
    }

    @Test
    fun productionSteps_fourTryItTasksAmongReadOnlyStops() {
        assertEquals(
            listOf(
                null,
                TourTaskKind.MARK_READ,
                TourTaskKind.CHANGE_AYAH,
                TourTaskKind.SWITCH_TRANSLATION,
                TourTaskKind.GO_TO_AYAH,
                null,
                null,
            ),
            TOUR_STEP_TASKS,
        )
    }
}
