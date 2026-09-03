package com.shrekbytes.waqfah.ui.reading

import com.shrekbytes.waqfah.data.local.core.SurahEntity
import com.shrekbytes.waqfah.data.local.core.VerseEntity
import com.shrekbytes.waqfah.data.model.AidLanguage
import com.shrekbytes.waqfah.data.model.NameDisplayLanguage
import com.shrekbytes.waqfah.data.model.ReadingMode
import com.shrekbytes.waqfah.data.model.TranslationLibrary
import com.shrekbytes.waqfah.data.model.TranslationMeta
import com.shrekbytes.waqfah.data.model.UserPreferences
import com.shrekbytes.waqfah.data.model.toTranslationLanguage
import androidx.compose.ui.unit.LayoutDirection
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

// The reading machine shared by both hosts — the Home tab and the interstitial
// (see CONTEXT.md). It owns the whole reading loop: stepping between verses,
// rendering the current one, marking verses read, the compare-translations
// peek, and the completion state — plus the ordering that keeps all of it
// consistent: every mutation of the current verse and every render that reads
// it is serialized behind one mutex, which is this module's internal
// invariant, not a convention callers must know about.
//
// Everything impure arrives through the constructor as a flow or a function:
// the three input signals (preferences, downloaded translation ids, the
// progress-reset nudge), the verse/progress/translation probes, and the scope
// hosting the collectors. ReadingViewModel is the Android adapter that wires
// repositories to those ports, so the whole machine is unit-testable with
// fake ports and virtual time (see ReadingSessionTest).
class ReadingSession(
    private val preferences: Flow<UserPreferences>,
    private val downloadedIds: StateFlow<Set<String>>,
    private val progressReset: Flow<Int>,
    private val verseById: suspend (Int) -> VerseEntity?,
    private val nextVerse: suspend (Int) -> VerseEntity?,
    private val previousVerse: suspend (Int) -> VerseEntity?,
    private val firstUnreadVerse: suspend (Set<Int>) -> VerseEntity?,
    private val randomUnreadVerse: suspend (Set<Int>) -> VerseEntity?,
    private val firstVerse: suspend () -> VerseEntity?,
    private val surah: suspend (Int) -> SurahEntity?,
    private val totalVerseCount: suspend () -> Int,
    private val readVerseIds: suspend () -> List<Int>,
    private val isRead: suspend (Int) -> Boolean,
    private val markRead: suspend (Int) -> Unit,
    private val unmarkRead: suspend (Int) -> Unit,
    private val countRead: suspend () -> Int,
    private val resetAll: suspend () -> Unit,
    private val translationText: suspend (TranslationMeta, Int) -> String?,
    private val setReadingMode: suspend (ReadingMode) -> Unit,
    private val scope: CoroutineScope,
) {

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

    private val _uiState = MutableStateFlow(ReadingUiState())
    val uiState: StateFlow<ReadingUiState> = _uiState.asStateFlow()

    init {
        scope.launch {
            preferences.collect { prefs ->
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
                if (!firstLoad && signature == lastRenderSignature) return@collect
                lastRenderSignature = signature
                mutationMutex.withLock {
                    if (defaultChanged) translationOverrideId = null
                    if (currentVerse == null) currentVerse = loadStartingVerse(prefs)
                    render(prefs)
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
        // Same idea for "Reset progress" in Settings: if this screen is already
        // showing an ayah, jump to a fresh starting verse instead of leaving
        // stale read/completion state on screen until a restart.
        // startOver()/switchModeAndRestart() also land here as a harmless
        // extra reload since they trigger this same signal themselves.
        scope.launch {
            progressReset.drop(1).collect {
                if (currentVerse != null) beginFreshSession()
            }
        }
    }

    // Suspend so the card can await these mid-gesture to sequence the swipe
    // animation, verse swap, and offset reset strictly.
    suspend fun next() = mutationMutex.withLock { step { nextVerse(it) } }
    suspend fun previous() = mutationMutex.withLock { step { previousVerse(it) } }

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
            if (newIsRead) {
                markRead(verse.id)
            } else {
                unmarkRead(verse.id)
            }
            refreshCompletionState()
        }
    }

    fun dismissCompletion() {
        completionDismissed = true
        _uiState.update { it.copy(isCompleted = false) }
    }

    // Both completion-popup reset paths wipe read history and land on a fresh
    // starting ayah — Start Again keeps the current mode, the switch moves to
    // the other mode first.
    fun startOver() = scope.launch {
        resetAll()
        beginFreshSession()
    }

    fun switchModeAndRestart() = scope.launch {
        val newMode = if (latestPrefs.readingMode == ReadingMode.SEQUENTIAL) ReadingMode.RANDOM else ReadingMode.SEQUENTIAL
        setReadingMode(newMode)
        latestPrefs = latestPrefs.copy(readingMode = newMode)
        resetAll()
        beginFreshSession()
    }

    // The interstitial's "you opened <app>" label is host garnish, not reading
    // state — the adapter resolves the label and hands it over; render()
    // deliberately never touches this field.
    fun setTriggeredAppLabel(label: String?) {
        _uiState.update { it.copy(triggeredAppLabel = label) }
    }

    private suspend fun beginFreshSession() {
        completionDismissed = false
        mutationMutex.withLock {
            currentVerse = loadStartingVerse(latestPrefs)
            render(latestPrefs)
        }
    }

    // Jumps to an explicit verse without touching read history or sequential/
    // random position — "Surahs & ayahs" works for read or unread, and
    // next/previous still step by global id after the jump. Mirrors beginFreshSession but
    // with an explicit target and cleared translation peek. Home-only by design;
    // TriggerActivity keeps its own instance via a separate Activity.
    suspend fun jumpToVerse(verseId: Int) {
        mutationMutex.withLock {
            val target = verseById(verseId) ?: return@withLock
            currentVerse = target
            translationOverrideId = null
            render(latestPrefs)
        }
    }

    private suspend fun isEverythingRead(): Boolean =
        countRead() >= totalVerses()

    // The bundled Quran database ships whole with every app update, so its size
    // never changes at runtime — fetch it once instead of on every render.
    private var cachedTotalVerseCount: Int? = null

    private suspend fun totalVerses(): Int =
        cachedTotalVerseCount ?: totalVerseCount().also { cachedTotalVerseCount = it }

    // Marking the last unread ayah completes the Quran mid-session too.
    private suspend fun refreshCompletionState() {
        val allRead = isEverythingRead()
        _uiState.update { it.copy(isCompleted = allRead && !completionDismissed) }
    }

    // Steps the preview to the next/previous downloaded translation for the
    // active display language, wrapping around. Never touches the persisted
    // default — pure "peek at another wording" for this ayah only.
    fun cycleTranslationSource(forward: Boolean) = scope.launch {
        val lang = latestPrefs.translationDisplay.toTranslationLanguage() ?: return@launch
        mutationMutex.withLock {
            val downloaded = downloadedIds.value
            val available = TranslationLibrary.available(lang, downloaded)
            if (available.size < 2) return@withLock
            val currentId = translationOverrideId
                ?: TranslationLibrary.resolveActive(lang, latestPrefs.storedTranslationId(lang), downloaded).id
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

    // Caller must hold mutationMutex.
    private suspend fun step(load: suspend (Int) -> VerseEntity?) {
        val fromId = currentVerse?.id ?: return
        currentVerse = load(fromId)
        // A fresh ayah always starts on the real default translation.
        translationOverrideId = null
        render(latestPrefs)
    }

    // Picks the *starting* verse of a fresh session only; prev/next always step
    // sequentially by id regardless of mode. Sequential opens on the lowest
    // ayah not yet marked read — marking a later ayah while leaving earlier
    // ones unmarked never strands an unread ayah behind. Random opens on any
    // unread ayah. When everything is already read, Sequential falls back to
    // the very first ayah so there is still content behind the popup.
    private suspend fun loadStartingVerse(prefs: UserPreferences): VerseEntity? {
        val readIds = readVerseIds().toHashSet()
        return when (prefs.readingMode) {
            ReadingMode.RANDOM -> randomUnreadVerse(readIds)
            ReadingMode.SEQUENTIAL -> firstUnreadVerse(readIds) ?: firstVerse()
        }
    }

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
            val allReadDeferred = async { isEverythingRead() }
            val nextPreviewDeferred =
                async { nextVerse(verse.id)?.let { buildPreview(it, prefs, downloaded) } }
            val previousPreviewDeferred =
                async { previousVerse(verse.id)?.let { buildPreview(it, prefs, downloaded) } }

            val translationLanguage = prefs.translationDisplay.toTranslationLanguage()
            val availableTranslations = translationLanguage
                ?.let { TranslationLibrary.available(it, downloaded) }
                .orEmpty()
            val defaultMeta = translationLanguage?.let {
                TranslationLibrary.resolveActive(it, prefs.storedTranslationId(it), downloaded)
            }
            val shownMeta = availableTranslations.find { it.id == translationOverrideId } ?: defaultMeta
            val translation = shownMeta?.let { translationText(it, verse.id) }
            val translitText = when (prefs.pronunciation) {
                AidLanguage.NONE -> null
                AidLanguage.ENGLISH -> verse.enTransliteration
                AidLanguage.BENGALI -> verse.bnTransliteration
            }

            _uiState.update { current ->
                current.copy(
                    isLoading = false,
                    surahName = surahDeferred.await()?.let { surahDisplayName(it, prefs.surahNameLanguage) } ?: "",
                    surahNameDirection = if (prefs.surahNameLanguage == NameDisplayLanguage.ARABIC) LayoutDirection.Rtl else LayoutDirection.Ltr,
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
    // translation — a peeked neighbour isn't in compare mode. Shares render's
    // downloadedIds snapshot so previews can't disagree with the main ayah.
    private suspend fun buildPreview(
        verse: VerseEntity,
        prefs: UserPreferences,
        downloaded: Set<String>,
    ): AyahPreview {
        val translationLanguage = prefs.translationDisplay.toTranslationLanguage()
        val meta = translationLanguage?.let {
            TranslationLibrary.resolveActive(it, prefs.storedTranslationId(it), downloaded)
        }
        val translation = meta?.let { translationText(it, verse.id) }
        val translitText = when (prefs.pronunciation) {
            AidLanguage.NONE -> null
            AidLanguage.ENGLISH -> verse.enTransliteration
            AidLanguage.BENGALI -> verse.bnTransliteration
        }
        return AyahPreview(
            ayahLabel = ayahLabel(verse, prefs.surahNameLanguage),
            arabicText = verse.arabicTextFor(prefs.arabicScript),
            arabicFont = prefs.arabicFont,
            arabicFontSize = prefs.arabicFontSize,
            translitText = translitText,
            translitFontSize = prefs.translitFontSize,
            translationText = translation,
            translationFontSize = prefs.translationFontSize,
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
