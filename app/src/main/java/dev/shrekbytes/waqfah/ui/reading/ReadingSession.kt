package dev.shrekbytes.waqfah.ui.reading

import dev.shrekbytes.waqfah.data.bookmark.BookmarkCollection
import dev.shrekbytes.waqfah.data.local.core.SurahEntity
import dev.shrekbytes.waqfah.data.local.core.VerseEntity
import dev.shrekbytes.waqfah.data.model.AidLanguage
import dev.shrekbytes.waqfah.data.model.NameDisplayLanguage
import dev.shrekbytes.waqfah.data.model.ReadingMode
import dev.shrekbytes.waqfah.data.model.TranslationLanguage
import dev.shrekbytes.waqfah.data.model.TranslationLibrary
import dev.shrekbytes.waqfah.data.model.TranslationMeta
import dev.shrekbytes.waqfah.data.model.UserPreferences
import dev.shrekbytes.waqfah.data.model.toTranslationLanguage
import dev.shrekbytes.waqfah.data.repository.VerseSequence
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// The reading machine shared by all three hosts — the Home tab, the Bookmarks
// tab and the interstitial (see CONTEXT.md). It owns the whole reading loop:
// stepping between verses, rendering the current one, marking verses read, the
// compare-translations peek, and the completion state — plus the ordering
// that keeps all of it consistent: every read and write of currentVerse,
// latestPrefs, translationOverrideId, completionDismissed and the
// render-signature bookkeeping happens behind one mutex — this module's
// internal invariant, not a convention callers must know about.
//
// Everything impure arrives through the constructor: the signals it
// subscribes to (preferences, downloaded translation ids, the progress-reset
// nudge) as flows, the bookmark collection — its published set and its toggle
// — as BookmarkCollection, verse movement as VerseSequence (the mushaf's
// selection, the bookmark collection's stepper, or the composite the
// interstitial is handed — see ADR-0006), and the
// remaining verse/progress/translation probes behind one interface —
// ReadingPorts — that DefaultReadingPorts adapts the repositories to. The
// whole machine is unit-testable with a fake ReadingPorts, a fake
// BookmarkCollection, a fake VerseLookups behind a real VerseSequence, and
// virtual time (see ReadingSessionTest).
class ReadingSession(
    private val preferences: Flow<UserPreferences>,
    private val downloadedIds: StateFlow<Set<String>>,
    // A monotonic counter the adapter bumps on every external progress wipe
    // (ReadingProgressRepository.progressReset). The session reads the value
    // to recognise the echo of its own resets — see lastSelfInitiatedReset.
    private val progressReset: StateFlow<Int>,
    private val ports: ReadingPorts,
    // The sequence this session walks: the whole mushaf on Home and on the
    // tour's practice card, the reader's bookmark collection on the Bookmarks
    // tab, and whichever the reading mode names on the interstitial. Which one
    // it was handed is the session's only difference between the hosts —
    // everything else it does is the same machine, and nothing below branches on
    // it except where read progress is concerned (see VerseSequence.isMushafWide).
    private val verseSelection: VerseSequence,
    // The bookmark collection (see CONTEXT.md). The session subscribes to the
    // set it publishes and reads membership back from the store — it holds no
    // copy of an ayah's saved state, so the toggle cannot drift from what is
    // actually stored, whichever surface saved the ayah (ADR-0005).
    private val bookmarks: BookmarkCollection,
    private val scope: CoroutineScope,
) {

    // One-line delegates so the machine body keeps calling the probes by
    // name — the seam's plumbing stays out of the mutex and render logic. These
    // live in the class body rather than the constructor because constructor
    // vals require explicit types on this compiler, which would resurrect the
    // function-type block this seam removed.
    private val verseById = ports::verseById
    private val surah = ports::surah
    private val totalVerseCount = ports::totalVerseCount
    private val readVerseIds = ports::readVerseIds
    private val isRead = ports::isRead
    private val markRead = ports::markRead
    private val unmarkRead = ports::unmarkRead
    private val countRead = ports::countRead
    private val resetAll = ports::resetAll
    private val translationText = ports::translationText
    private val setReadingMode = ports::setReadingMode

    // Serializes every mutation of currentVerse / translationOverrideId and the
    // renders that read them. next()/previous() are awaited mid-gesture from
    // the UI's own coroutine scope, so rapid swipes — or a preferences emission
    // landing mid-step — would otherwise interleave step()/render() calls and
    // let a stale render overwrite the newer verse.
    private val mutationMutex = Mutex()

    private var currentVerse: VerseEntity? = null
    private var latestPrefs = UserPreferences()

    // Signature of the last rendered preferences (see readingRenderSignature);
    // null until the first emission. Emissions that don't change it skip the
    // full render.
    private var lastRenderSignature: List<Any?>? = null

    // Session-local "compare translations" override for the current ayah; null
    // means show the real default. Cleared on every step() so it never outlives
    // the ayah it was opened on.
    private var translationOverrideId: String? = null

    // Session-local dismissal of the Quran-completed popup; Close keeps it
    // hidden until progress actually changes again.
    private var completionDismissed = false

    // The progressReset counter's value right after the session's own last
    // resetAll(); the collector skips emissions at or below it so the reset's
    // echo never reloads again. Guarded by mutationMutex. Monotonic-counter
    // arithmetic makes this conflations-safe: an external reset that bumps
    // past the recorded value always lands above it.
    private var lastSelfInitiatedReset = Int.MIN_VALUE

    private val _uiState = MutableStateFlow(ReadingUiState())
    val uiState: StateFlow<ReadingUiState> = _uiState.asStateFlow()

    init {
        scope.launch {
            preferences.collect { prefs ->
                mutationMutex.withLock {
                    // Changing the persisted default must win over any session-local
                    // compare-mode peek — otherwise switching translations looks
                    // like it "didn't update" while the old override still renders.
                    val defaultChanged = prefs.activeTranslationEnglish != latestPrefs.activeTranslationEnglish ||
                        prefs.activeTranslationBengali != latestPrefs.activeTranslationBengali
                    // The first emission always renders (it loads the starting
                    // verse); later ones only when something the card displays
                    // changed — unrelated writes (theme ticks, cooldown stepper,
                    // locale mirror…) skip the render instead of paying ~6 DB
                    // queries per emission.
                    val firstLoad = currentVerse == null
                    val signature = readingRenderSignature(prefs)
                    latestPrefs = prefs
                    if (!firstLoad && signature == lastRenderSignature) return@withLock
                    lastRenderSignature = signature
                    if (defaultChanged) translationOverrideId = null
                    if (currentVerse == null) currentVerse = loadStartingVerse(prefs)
                    // A selection with nothing to show is a state of its own,
                    // not a load that never resolves: the card says the
                    // collection is empty rather than showing a skeleton
                    // forever. Only a collection-scoped walk can be empty —
                    // the mushaf always has an ayah — so a null selection
                    // there leaves this path exactly as it was.
                    if (currentVerse == null && !verseSelection.isMushafWide) {
                        showEmptyStateLocked()
                    } else {
                        render(prefs)
                    }
                }
            }
        }
        // A download/delete/first-copy can land while this screen is already
        // alive (e.g. a translation finishes downloading in Settings, then the
        // user returns Home). The set dedupes, so this only fires when
        // availability actually changed — re-render so compare-switcher
        // availability reflects the new file instead of staying stale until
        // the next ayah change.
        scope.launch {
            downloadedIds.drop(1).collect {
                mutationMutex.withLock {
                    if (currentVerse != null) render(latestPrefs)
                }
            }
        }
        // Same idea for "Reset progress" in Settings: whatever this screen
        // shows is stale once someone else wipes the read history, so it must
        // not be left there until a restart. The session's own resets
        // (startOver / switchModeAndRestart) echo through this same signal —
        // but they reload synchronously under the lock and record the echo
        // below, so the collector skips their emission instead of paying a
        // second reload.
        scope.launch {
            progressReset.drop(1).collect { value ->
                mutationMutex.withLock {
                    if (value <= lastSelfInitiatedReset) return@withLock
                    if (currentVerse == null) return@withLock
                    // The wipe happened elsewhere, so whatever is on screen is
                    // stale either way. The mushaf-wide walk starts over from a
                    // fresh verse; a collection-scoped session is not a progress
                    // surface and must not move, so it re-renders instead — the
                    // ayah it shows may well have been in the wiped history, and
                    // the card must not keep claiming it was read.
                    if (verseSelection.isMushafWide) {
                        beginFreshSessionLocked()
                    } else {
                        render(latestPrefs)
                    }
                }
            }
        }
        // The collection's published set is the change signal for two things:
        // the saved state of the ayah on screen — a save made anywhere else,
        // the interstitial or another card, must land here without a re-render
        // or a manual refresh — and, on a collection-scoped session, the
        // collection's own emptiness, because there the collection *is* the
        // content. The set itself is not held; membership is read back from
        // the store, so no copy of it can go stale.
        scope.launch {
            bookmarks.savedVerseIds.collect { saved ->
                mutationMutex.withLock {
                    // The header's collection total (see ReadingCard) rides this
                    // same emission: it is a fact about the collection, not about
                    // the ayah on screen, so it is published here and not in
                    // render(), which runs on every swipe. An empty state below
                    // resets it to zero, which is what an empty collection means.
                    _uiState.update { it.copy(savedCount = saved.size) }
                    when {
                        // Nothing saved: the card has nothing to show and must
                        // say so, rather than keep rendering an ayah that is no
                        // longer saved — or step to an unrelated one.
                        !verseSelection.isMushafWide && saved.isEmpty() ->
                            showEmptyStateLocked()

                        // Filled again from empty — a save made on Home or in
                        // the interstitial: pick up the collection's first
                        // ayah. Before the first load the session is loading,
                        // not empty, so this cannot jump ahead of preferences.
                        currentVerse == null && _uiState.value.isEmpty ->
                            beginFreshSessionLocked()

                        else -> currentVerse?.let { verse -> refreshSavedStateLocked(verse) }
                    }
                }
            }
        }
    }

    // Suspend so the card can await these mid-gesture to sequence the swipe
    // animation, verse swap, and offset reset strictly.
    suspend fun next() = mutationMutex.withLock { step { verseSelection.next(it) } }
    suspend fun previous() = mutationMutex.withLock { step { verseSelection.previous(it) } }

    fun markCurrentRead() = scope.launch {
        mutationMutex.withLock {
            // Decision and target verse are captured under the same lock the
            // DB write uses. Computing the toggle from uiState OUTSIDE the
            // lock let a swipe committing mid-gesture apply ayah A's tap to
            // whichever ayah B had just become current. DB truth (one cheap
            // EXISTS) is the source instead of possibly-stale ui state.
            val verse = currentVerse ?: return@withLock
            val newIsRead = !isRead(verse.id)
            // Optimistic UI update before the write keeps feedback instant.
            _uiState.update { it.copy(isMarkedRead = newIsRead) }
            try {
                if (newIsRead) {
                    markRead(verse.id)
                } else {
                    unmarkRead(verse.id)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The optimistic flip is only correct while it is temporary.
                // A failed write leaves the DB holding the old truth, so the
                // pill must go back to it — otherwise it disagrees with the
                // database until something unrelated re-renders the card.
                // Completion is deliberately not refreshed: the write it would
                // have reflected never landed. Rethrown so the host scope's
                // CoroutineExceptionHandler (the ViewModel that hosts this
                // session) still logs the failure — this machine does no
                // Android logging of its own, which is what keeps it
                // JVM-testable.
                _uiState.update { it.copy(isMarkedRead = !newIsRead) }
                throw e
            }
            // The mark counts as an action the moment its write lands — the
            // tour's TryIt MARK_READ step detects marks through this counter,
            // which survives state conflation where the transient
            // isMarkedRead flip does not (#33). Unmarks never count.
            if (newIsRead) _uiState.update { it.copy(markReadCount = it.markReadCount + 1) }
            // Marking the last unread ayah completes the Quran mid-session
            // too. One computation serves both the popup and the advance
            // request below, so they can never disagree.
            val allRead = isEverythingRead()
            _uiState.update { it.copy(isCompleted = allRead && !completionDismissed) }
            // Auto-next (#33): the session owns the decision — setting on,
            // mark direction, not completing — and raises it as a request
            // rather than stepping: the card plays the mark confirmation
            // against the still-shown ayah, then fires next() itself so the
            // advance is seen instead of a teleport. An unmark, a failed
            // write, and the completing mark (the popup is that moment) never
            // raise it; any committed step clears it.
            _uiState.update {
                it.copy(pendingAutoAdvance = newIsRead && latestPrefs.autoNextOnMark && !allRead)
            }
        }
    }

    // The bookmark toggle (see CONTEXT.md). Verse-keyed like mark-read — the
    // decision and target verse are captured under the same lock as the write,
    // so a swipe committing mid-gesture cannot apply this tap to the wrong
    // ayah. Deliberately NOT optimistic, unlike mark-read: the collection is a
    // place users look things up, so the icon reflects what the store holds.
    // A failed write throws out of the lock leaving the icon on the state it
    // already had (the store's) and still reaches the host's handler, so there
    // is no optimistic flip to roll back.
    fun toggleBookmark() = scope.launch {
        mutationMutex.withLock {
            val verse = currentVerse ?: return@withLock
            bookmarks.toggle(verse.id)
            refreshSavedStateLocked(verse)
        }
    }

    // Caller must hold mutationMutex. Membership is asked of the store every
    // time rather than cached on this session, so two cards — and the
    // interstitial — can never disagree about a verse.
    private suspend fun refreshSavedStateLocked(verse: VerseEntity) {
        _uiState.update { it.copy(isSaved = bookmarks.isSaved(verse.id)) }
    }

    // Caller must hold mutationMutex. The selection answered "nothing to
    // show": a presentable state — an empty bookmark collection is the
    // invitation to start one (ADR-0005) — not a load that never resolves.
    // There is no verse to show, step from, or mark, so the session holds
    // none, and the state goes back to its initial values rather than a
    // patched copy of the last verse's: a leftover isMarkedRead or isSaved
    // would be a claim about an ayah that isn't there. triggeredAppLabel is
    // the host's, not the verse's, and survives; render() is what fills the
    // verse fields in again when one comes back.
    private fun showEmptyStateLocked() {
        currentVerse = null
        _uiState.update { current ->
            ReadingUiState(isLoading = false, isEmpty = true, triggeredAppLabel = current.triggeredAppLabel)
        }
    }

    // Launched so the flag flips under the same mutex render() reads it from.
    fun dismissCompletion() = scope.launch {
        mutationMutex.withLock {
            completionDismissed = true
            _uiState.update { it.copy(isCompleted = false) }
        }
    }

    // Both completion-popup reset paths wipe read history and land on a fresh
    // starting ayah — Start Again keeps the current mode, the switch moves to
    // the other mode first. Both reload synchronously under the lock and then
    // swallow the progressReset echo of their own resetAll(), so exactly one
    // reload happens; switchModeAndRestart also writes the mode echo into
    // latestPrefs under the same lock, so the fresh session picks it up
    // whichever collector runs first.
    fun startOver() = scope.launch {
        mutationMutex.withLock {
            resetAll()
            lastSelfInitiatedReset = progressReset.value
            beginFreshSessionLocked()
        }
    }

    fun switchModeAndRestart() = scope.launch {
        mutationMutex.withLock {
            val newMode = if (latestPrefs.readingMode == ReadingMode.SEQUENTIAL) ReadingMode.RANDOM else ReadingMode.SEQUENTIAL
            setReadingMode(newMode)
            latestPrefs = latestPrefs.copy(readingMode = newMode)
            resetAll()
            lastSelfInitiatedReset = progressReset.value
            beginFreshSessionLocked()
        }
    }

    // The interstitial's "you opened <app>" label is host garnish, not reading
    // state — the adapter resolves the label and hands it over; render()
    // deliberately never touches this field.
    fun setTriggeredAppLabel(label: String?) {
        _uiState.update { it.copy(triggeredAppLabel = label) }
    }

    // Caller must hold mutationMutex.
    private suspend fun beginFreshSessionLocked() {
        // The fresh session lands on a new ayah: the compare peek never
        // outlives the ayah it was opened on — the same rule step() and
        // jumpToVerse() apply.
        translationOverrideId = null
        completionDismissed = false
        currentVerse = loadStartingVerse(latestPrefs)
        render(latestPrefs)
        // A fresh session is a committed position change: it supersedes the
        // request the same way step() does.
        _uiState.update { it.copy(pendingAutoAdvance = false) }
    }

    // Jumps to an explicit verse without touching read history or sequential/
    // random position — "Surahs & ayahs" works for read or unread, and
    // next/previous still step by global id after the jump. Like a fresh
    // session but with an explicit target and a cleared translation peek.
    // Home-only by design; TriggerActivity keeps its own instance via a
    // separate Activity.
    suspend fun jumpToVerse(verseId: Int) {
        mutationMutex.withLock {
            val target = verseById(verseId) ?: return@withLock
            currentVerse = target
            translationOverrideId = null
            render(latestPrefs)
            _uiState.update { it.copy(pendingAutoAdvance = false) } // a jump supersedes the request
        }
    }

    // Read progress governs the mushaf-wide walk only: the every-ayah-read
    // completion event — and the progress-wiping "Start Again" it offers —
    // belongs to walking the whole Quran, so a collection-scoped session
    // never reports it (ADR-0005).
    private suspend fun isEverythingRead(): Boolean =
        verseSelection.isMushafWide && countRead() >= totalVerses()

    // The bundled Quran database ships whole with every app update, so its size
    // never changes at runtime — fetch it once instead of on every render.
    private var cachedTotalVerseCount: Int? = null

    private suspend fun totalVerses(): Int =
        cachedTotalVerseCount ?: totalVerseCount().also { cachedTotalVerseCount = it }

    // Steps the preview to the next/previous downloaded translation for the
    // active display language, wrapping around. Never touches the persisted
    // default — pure "peek at another wording" for this ayah only.
    fun cycleTranslationSource(forward: Boolean) = scope.launch {
        mutationMutex.withLock {
            val lang = latestPrefs.translationDisplay.toTranslationLanguage() ?: return@withLock
            val downloaded = downloadedIds.value
            val available = TranslationLibrary.available(lang, downloaded)
            if (available.size < 2) return@withLock
            val currentId = translationOverrideId
                ?: activeTranslation(lang, latestPrefs, downloaded).id
            val currentIndex = available.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
            val stepDir = if (forward) 1 else -1
            translationOverrideId = available[(currentIndex + stepDir + available.size) % available.size].id
            render(latestPrefs)
        }
    }

    // Drops the preview back to the real default when the switcher closes.
    fun resetTranslationSource() = scope.launch {
        mutationMutex.withLock {
            if (translationOverrideId == null) return@withLock
            translationOverrideId = null
            render(latestPrefs)
        }
    }

    // Caller must hold mutationMutex. Clears a pending auto-advance (#33):
    // a committed step — a manual swipe, or the card firing the request —
    // consumes it, so a late follow-through can never double-step.
    private suspend fun step(load: suspend (Int) -> VerseEntity?) {
        val fromId = currentVerse?.id ?: return
        currentVerse = load(fromId)
        // A fresh ayah always starts on the real default translation.
        translationOverrideId = null
        render(latestPrefs)
        _uiState.update { it.copy(pendingAutoAdvance = false) }
    }

    // Picks the *starting* verse of a fresh session only; prev/next always step
    // sequentially by id regardless of mode. Which verse that is — including
    // every everything-read fallback — is verse selection's decision, not a
    // branch here.
    private suspend fun loadStartingVerse(prefs: UserPreferences): VerseEntity? =
        verseSelection.start(prefs.readingMode, readVerseIds().toHashSet())

    // Pronunciation aid for a verse, or null when the user turned it off.
    private fun translitFor(verse: VerseEntity, prefs: UserPreferences): String? =
        when (prefs.pronunciation) {
            AidLanguage.NONE -> null
            AidLanguage.ENGLISH -> verse.enTransliteration
            AidLanguage.BENGALI -> verse.bnTransliteration
        }

    // The card's active translation for the display language — the stored
    // default when available, otherwise the language's bundled one (the one
    // question TranslationLibrary answers). Never null for a real language;
    // callers pair it with toTranslationLanguage()'s null for "no aid text".
    private fun activeTranslation(
        language: TranslationLanguage,
        prefs: UserPreferences,
        downloaded: Set<String>,
    ): TranslationMeta = TranslationLibrary.resolveActive(language, prefs.storedTranslationId(language), downloaded)

    // Caller must hold mutationMutex.
    private suspend fun render(prefs: UserPreferences) {
        val verse = currentVerse ?: return

        // One snapshot for the whole render: availability and the active
        // translation must agree even if a download lands mid-render —
        // shared with the next/prev previews too.
        val downloaded = downloadedIds.value

        // The independent lookups run concurrently — sequentially a render
        // costs ~6 DB round-trips (per swipe, and per settings tick).
        coroutineScope {
            val surahDeferred = async { surah(verse.surahNo) }
            val isReadDeferred = async { isRead(verse.id) }
            val isSavedDeferred = async { bookmarks.isSaved(verse.id) }
            val allReadDeferred = async { isEverythingRead() }
            val nextPreviewDeferred =
                async { verseSelection.next(verse.id)?.let { buildPreview(it, prefs, downloaded) } }
            val previousPreviewDeferred =
                async { verseSelection.previous(verse.id)?.let { buildPreview(it, prefs, downloaded) } }

            val translationLanguage = prefs.translationDisplay.toTranslationLanguage()
            val availableTranslations = translationLanguage
                ?.let { TranslationLibrary.available(it, downloaded) }
                .orEmpty()
            val defaultMeta = translationLanguage?.let { activeTranslation(it, prefs, downloaded) }
            val shownMeta = availableTranslations.find { it.id == translationOverrideId } ?: defaultMeta
            val translation = shownMeta?.let { translationText(it, verse.id) }
            val translitText = translitFor(verse, prefs)

            _uiState.update { current ->
                current.copy(
                    isLoading = false,
                    // An ayah is being rendered, so the sequence is not empty.
                    isEmpty = false,
                    surahName = surahDeferred.await()?.let { surahDisplayName(it, prefs.surahNameLanguage) } ?: "",
                    surahNameDirection = if (prefs.surahNameLanguage == NameDisplayLanguage.ARABIC) LayoutDirection.Rtl else LayoutDirection.Ltr,
                    surahNameLanguage = prefs.surahNameLanguage,
                    ayahLabel = ayahLabel(verse, prefs.surahNameLanguage),
                    totalLabel = surahDeferred.await()?.let { "${localizeDigits(it.ayahCount, prefs.surahNameLanguage)} ${ayahWord(prefs.surahNameLanguage)}" } ?: "",
                    arabicText = verse.arabicTextFor(prefs.arabicScript),
                    arabicFont = prefs.arabicFont,
                    arabicFontSize = prefs.arabicFontSize,
                    translitText = translitText,
                    translitFontSize = prefs.translitFontSize,
                    translationText = translation,
                    translationFontSize = prefs.translationFontSize,
                    translationSourceName = shownMeta?.name,
                    translationHasAlternates = availableTranslations.size > 1,
                    isMarkedRead = isReadDeferred.await(),
                    isSaved = isSavedDeferred.await(),
                    readingMode = prefs.readingMode,
                    isCompleted = allReadDeferred.await() && !completionDismissed,
                    // triggeredAppLabel untouched — owned by setTriggeredAppLabel()
                    nextPreview = nextPreviewDeferred.await(),
                    previousPreview = previousPreviewDeferred.await(),
                )
            }
        }
    }

    // Like render(), minus what only applies to the ayah actually being read
    // (override mode, read status, header) and always with the real default
    // translation — a peeked neighbour isn't in compare mode. The saved state
    // is deliberately not on that list: the peek draws the saved-mark too, so
    // it is read back per neighbour the same way render() reads it for the ayah
    // on screen. Shares render's downloadedIds snapshot so previews can't
    // disagree with the main ayah.
    private suspend fun buildPreview(
        verse: VerseEntity,
        prefs: UserPreferences,
        downloaded: Set<String>,
    ): AyahPreview {
        val translationLanguage = prefs.translationDisplay.toTranslationLanguage()
        val meta = translationLanguage?.let { activeTranslation(it, prefs, downloaded) }
        val translation = meta?.let { translationText(it, verse.id) }
        val translitText = translitFor(verse, prefs)
        return AyahPreview(
            ayahLabel = ayahLabel(verse, prefs.surahNameLanguage),
            arabicText = verse.arabicTextFor(prefs.arabicScript),
            arabicFont = prefs.arabicFont,
            arabicFontSize = prefs.arabicFontSize,
            translitText = translitText,
            translitFontSize = prefs.translitFontSize,
            translationText = translation,
            translationFontSize = prefs.translationFontSize,
            isSaved = bookmarks.isSaved(verse.id),
        )
    }
}

// Pure core of the reading session's preferences filter, extracted for unit
// testing: exactly the UserPreferences fields the reading card renders (or
// echoes in its UI state). Anything NOT listed here changes none of this
// screen's output, so its emissions skip the full re-render — see
// ReadingRelevanceTest, which pins both the inclusions and the exclusions.
// When a new preference starts affecting this card, add it here AND to that
// test; when one doesn't, leave it out.
internal fun readingRenderSignature(p: UserPreferences): List<Any?> = listOf(
    p.readingMode,
    p.surahNameLanguage,
    p.arabicScript,
    p.arabicFont,
    p.arabicFontSize,
    p.pronunciation,
    p.translitFontSize,
    p.translationDisplay,
    p.translationFontSize,
    p.activeTranslationEnglish,
    p.activeTranslationBengali,
)
