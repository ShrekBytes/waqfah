package com.shrekbytes.waqfah

import com.shrekbytes.waqfah.ui.tour.TOUR_STEP_TASKS
import com.shrekbytes.waqfah.ui.tour.TourBackResult
import com.shrekbytes.waqfah.ui.tour.TourReadingFacts
import com.shrekbytes.waqfah.ui.tour.TourSession
import com.shrekbytes.waqfah.ui.tour.TourTaskKind
import com.shrekbytes.waqfah.ui.tour.TourUiState
import com.shrekbytes.waqfah.ui.tour.tourVisible
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
        isMarkedRead: Boolean = false,
        hasTranslationAlternates: Boolean = true,
    ) = TourReadingFacts(
        isLoading,
        ayahLabel,
        translationSourceName,
        translationText,
        isMarkedRead,
        hasTranslationAlternates,
    )

    @Test
    fun markRead_completesOnlyWhenAResolvedCardShowsRead() = runTest {
        val s = session(listOf(TourTaskKind.MARK_READ))
        s.onReadingState(facts(isLoading = true, isMarkedRead = true))
        assertFalse(s.uiState.value.taskDone)
        s.onReadingState(facts(isMarkedRead = false))
        assertFalse(s.uiState.value.taskDone)
        s.onReadingState(facts(isMarkedRead = true))
        assertTrue(s.uiState.value.taskDone)
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
        assertFalse(tourVisible(onHome = true, s.uiState.value))
    }

    // ADR-0003: skipping persists nothing — the tour re-offers next launch.
    @Test
    fun skip_dismissesAndNeverPersists() = runTest {
        var finished = 0
        completed.value = false
        val s = session(listOf(null), onFinished = { finished++ })
        s.onOpenedManually()
        assertTrue(tourVisible(onHome = true, s.uiState.value))
        s.skip()
        assertFalse(tourVisible(onHome = true, s.uiState.value))
        assertEquals(0, finished)
    }

    @Test
    fun finishOnLastStep_persistsOnceAndDismisses() = runTest {
        var finished = 0
        val s = session(listOf(null), onFinished = { finished++ })
        // Unresolved preferences: the gate waits instead of flashing the tour.
        assertFalse(tourVisible(onHome = true, s.uiState.value))
        completed.value = false
        runCurrent()
        assertTrue(tourVisible(onHome = true, s.uiState.value))
        s.next() // the only step is the last one: finishing
        assertEquals(1, finished)
        assertFalse(tourVisible(onHome = true, s.uiState.value))
    }

    @Test
    fun gate_manualOpenShowsOnHomeOnly_evenAfterCompletion() = runTest {
        completed.value = true
        val s = session(listOf(null))
        // finished once: auto-show off...
        assertFalse(tourVisible(onHome = true, s.uiState.value))
        // ...but a manual open still shows, on the Home tab only
        s.onOpenedManually()
        assertTrue(tourVisible(onHome = true, s.uiState.value))
        assertFalse(tourVisible(onHome = false, s.uiState.value))
        assertFalse(tourVisible(onHome = false, TourUiState(autoShowAllowed = true)))
        assertTrue(tourVisible(onHome = true, TourUiState(autoShowAllowed = true)))
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
