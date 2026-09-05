package com.shrekbytes.waqfah.ui.tour

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// One TryIt practice on the live reading card.
enum class TourTaskKind { MARK_READ, CHANGE_AYAH, SWITCH_TRANSLATION, GO_TO_AYAH }

enum class TourBackResult { CLOSE_PICKER, PREVIOUS_STEP, SKIPPED }

// The reading-card facts the machine needs, pushed by the overlay on every
// reading emission. Deliberately raw: derivation — the shown translation, the
// switch hints — happens inside the session.
data class TourReadingFacts(
    val isLoading: Boolean,
    val ayahLabel: String,
    val translationSourceName: String?,
    val translationText: String?,
    val isMarkedRead: Boolean,
    val hasTranslationAlternates: Boolean,
)

data class TourUiState(
    val stepIndex: Int = 0,
    val taskDone: Boolean = false,
    val goToPickerOpen: Boolean = false,
    // SWITCH_TRANSLATION hints, derived from the facts; the overlay applies
    // them on that step's page only.
    val isTranslationDisabled: Boolean = false,
    val showTranslationFallback: Boolean = false,
    val openedManually: Boolean = false,
    val autoShowAllowed: Boolean = false,
)

// The tour's machine (see CONTEXT.md): it owns the current step, decides when
// a TryIt task is done from the reading facts the overlay pushes in, and owns
// dismissal — finishing persists completion, skipping persists nothing
// (ADR-0003). The overlay is its rendering adapter and MainScreen's
// tourVisible gate its visibility adapter.
//
// The rules that used to live as composable conventions are invariants here:
//  - the task anchor is captured when a TryIt step becomes current, and
//    re-captured whenever loading resolves afterwards, so a blank card is
//    never the baseline;
//  - GO_TO_AYAH completes only via onJumpedInPicker — a swipe or a header tap
//    can never finish the step;
//  - the go-to picker and a completed jump are reset only when the step
//    actually changes — picker churn on the same step never does;
//  - back closes the picker first, then steps back, then — on the first
//    step — skips;
//  - skip() never reaches onFinished; only next() on the last step does.
//
// All verbs must be called on the main thread: the machine is plain state,
// not synchronized.
class TourSession(
    private val tasks: List<TourTaskKind?>,
    hasCompletedTour: Flow<Boolean?>,
    private val onFinished: () -> Unit,
    scope: CoroutineScope,
) {

    private var stepIndex = 0
    private var goToPickerOpen = false
    private var jumpedFromPicker = false
    private var openedManually = false
    private var dismissedThisSession = false
    private var persistedComplete: Boolean? = null

    // The task anchor: the reading-card values the current TryIt step compares
    // against. anchorCurrent marks the anchor as belonging to this step entry,
    // so a stale one from a previous visit is never consulted.
    private var anchorAyah: String? = null
    private var anchorTranslation: String? = null
    private var anchorCurrent = false

    private var lastFacts: TourReadingFacts? = null
    private var lastPushedLoading: Boolean? = null

    private val _uiState = MutableStateFlow(TourUiState())
    val uiState: StateFlow<TourUiState> = _uiState.asStateFlow()

    init {
        scope.launch {
            hasCompletedTour.collect { persisted ->
                persistedComplete = persisted
                recompute()
            }
        }
    }

    // --- events -------------------------------------------------------------

    fun onOpenedManually() {
        openedManually = true
        recompute()
    }

    fun onReadingState(facts: TourReadingFacts) {
        val loadingResolved = lastPushedLoading == true && !facts.isLoading
        lastPushedLoading = facts.isLoading
        lastFacts = facts
        if (!facts.isLoading && tasks[stepIndex] != null && (!anchorCurrent || loadingResolved)) {
            captureAnchor(facts)
        }
        recompute()
    }

    fun next() {
        if (stepIndex == tasks.lastIndex) {
            // The only path to persistence: finishing — exactly once, however
            // many times the button fires before the gate tears the overlay
            // down (dismissal closes it).
            if (!dismissedThisSession) {
                onFinished()
                dismiss()
            }
        } else {
            enterStep(stepIndex + 1)
        }
    }

    fun back(): TourBackResult = when {
        goToPickerOpen -> {
            goToPickerOpen = false
            recompute()
            TourBackResult.CLOSE_PICKER
        }
        stepIndex > 0 -> {
            enterStep(stepIndex - 1)
            TourBackResult.PREVIOUS_STEP
        }
        else -> {
            dismiss()
            TourBackResult.SKIPPED
        }
    }

    fun skip() = dismiss()

    fun openGoToPicker() {
        goToPickerOpen = true
        recompute()
    }

    fun closeGoToPicker() {
        goToPickerOpen = false
        recompute()
    }

    // The only thing that can complete GO_TO_AYAH: a jump made inside the
    // embedded picker.
    fun onJumpedInPicker() {
        jumpedFromPicker = true
        goToPickerOpen = false
        recompute()
    }

    // --- internals ----------------------------------------------------------

    private fun enterStep(index: Int) {
        stepIndex = index
        // A genuine step change collapses the sandbox picker and any jump, and
        // retires the anchor — coming back to the step starts it fresh. Picker
        // churn or a jump never lands here (no step change), so a jump survives.
        goToPickerOpen = false
        jumpedFromPicker = false
        anchorCurrent = false
        lastFacts?.takeIf { !it.isLoading && tasks[stepIndex] != null }?.let(::captureAnchor)
        recompute()
    }

    private fun captureAnchor(facts: TourReadingFacts) {
        anchorCurrent = true
        anchorAyah = facts.ayahLabel
        anchorTranslation = facts.translationSourceName ?: facts.translationText
    }

    private fun dismiss() {
        // Dismissal never persists: a skipped or backed-out tour stays
        // incomplete and re-offers next launch (ADR-0003).
        openedManually = false
        dismissedThisSession = true
        recompute()
    }

    private fun taskDone(): Boolean = when (tasks[stepIndex]) {
        TourTaskKind.GO_TO_AYAH -> jumpedFromPicker
        TourTaskKind.MARK_READ -> lastFacts?.let { !it.isLoading && it.isMarkedRead } ?: false
        TourTaskKind.CHANGE_AYAH -> lastFacts?.let { anchorCurrent && it.ayahLabel != anchorAyah } ?: false
        TourTaskKind.SWITCH_TRANSLATION -> lastFacts?.let { anchorCurrent && shownTranslation(it) != anchorTranslation } ?: false
        null -> false
    }

    // What the card shows as the translation: the named source if one is
    // active, otherwise the text itself. One definition — the anchor and the
    // switch hints both go through it.
    private fun shownTranslation(facts: TourReadingFacts): String? =
        facts.translationSourceName ?: facts.translationText

    private fun recompute() {
        val facts = lastFacts
        _uiState.value = TourUiState(
            stepIndex = stepIndex,
            taskDone = taskDone(),
            goToPickerOpen = goToPickerOpen,
            isTranslationDisabled = facts != null && !facts.isLoading && facts.translationText == null,
            showTranslationFallback = facts != null && facts.translationText != null && !facts.hasTranslationAlternates,
            openedManually = openedManually,
            autoShowAllowed = persistedComplete == false && !dismissedThisSession,
        )
    }
}

// The tour's visibility gate, one tested home: the tour shows over the Home
// tab when opened manually, or auto-shows while it has never been finished
// and hasn't been dismissed this session. Unresolved preferences keep
// autoShowAllowed shut, so the gate waits instead of flashing.
fun tourVisible(onHome: Boolean, ui: TourUiState): Boolean =
    onHome && (ui.openedManually || ui.autoShowAllowed)
