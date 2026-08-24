package com.shrekbytes.waqfah.ui.reading

import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shrekbytes.waqfah.data.local.core.VerseEntity
import com.shrekbytes.waqfah.data.model.AidLanguage
import com.shrekbytes.waqfah.data.model.NameDisplayLanguage
import com.shrekbytes.waqfah.data.model.ReadingMode
import com.shrekbytes.waqfah.data.model.TranslationCatalog
import com.shrekbytes.waqfah.data.model.TranslationLanguage
import com.shrekbytes.waqfah.data.model.TranslationMeta
import com.shrekbytes.waqfah.data.model.UserPreferences
import com.shrekbytes.waqfah.data.model.toTranslationLanguage
import com.shrekbytes.waqfah.data.repository.MonitoredAppsRepository
import com.shrekbytes.waqfah.data.repository.QuranRepository
import com.shrekbytes.waqfah.data.repository.ReadingProgressRepository
import com.shrekbytes.waqfah.data.repository.SettingsRepository
import com.shrekbytes.waqfah.data.repository.TranslationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

// HomeScreen and ReadingScreen live in separate Activities (MainActivity and
// TriggerActivity), so each gets its own instance. Everything that must survive
// across them — read status — is persisted in Room, not held in memory.
@HiltViewModel
class ReadingViewModel @Inject constructor(
    private val quranRepository: QuranRepository,
    private val translationRepository: TranslationRepository,
    private val readingProgressRepository: ReadingProgressRepository,
    private val monitoredAppsRepository: MonitoredAppsRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    // Serializes every mutation of currentVerse / translationOverrideId and the
    // renders that read them. next()/previous() are awaited mid-gesture from the
    // UI's own coroutine scope, so rapid swipes — or a preferences emission
    // landing mid-step — would otherwise interleave step()/render() calls and
    // let a stale render overwrite the newer verse.
    private val mutationMutex = Mutex()

    private var currentVerse: VerseEntity? = null
    private var latestPrefs = UserPreferences()

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
        viewModelScope.launch {
            settingsRepository.preferences.collect { prefs ->
                // Changing the persisted default must win over any session-local
                // compare-mode peek — otherwise switching translations looks
                // like it "didn't update" while the old override still renders.
                val defaultChanged = prefs.activeTranslationEnglish != latestPrefs.activeTranslationEnglish ||
                    prefs.activeTranslationBengali != latestPrefs.activeTranslationBengali
                latestPrefs = prefs
                mutationMutex.withLock {
                    if (defaultChanged) translationOverrideId = null
                    if (currentVerse == null) currentVerse = loadStartingVerse(prefs)
                    render(prefs)
                }
            }
        }
    }

    // Suspend so ReadingCard can await these mid-gesture to sequence the swipe
    // animation, verse swap, and offset reset strictly.
    suspend fun next() = mutationMutex.withLock { step { quranRepository.getNextVerse(it) } }
    suspend fun previous() = mutationMutex.withLock { step { quranRepository.getPreviousVerse(it) } }

    fun markCurrentRead() = viewModelScope.launch {
        val newIsRead = !_uiState.value.isMarkedRead
        // Optimistic UI update first — awaiting the DB write delays feedback.
        _uiState.update { it.copy(isMarkedRead = newIsRead) }
        mutationMutex.withLock {
            val verse = currentVerse ?: return@withLock
            if (newIsRead) {
                readingProgressRepository.markRead(verse.id)
            } else {
                readingProgressRepository.unmarkRead(verse.id)
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
    fun startOver() = viewModelScope.launch {
        readingProgressRepository.resetAll()
        beginFreshSession()
    }

    fun switchModeAndRestart() = viewModelScope.launch {
        val newMode = if (latestPrefs.readingMode == ReadingMode.SEQUENTIAL) ReadingMode.RANDOM else ReadingMode.SEQUENTIAL
        settingsRepository.setReadingMode(newMode)
        latestPrefs = latestPrefs.copy(readingMode = newMode)
        readingProgressRepository.resetAll()
        beginFreshSession()
    }

    private suspend fun beginFreshSession() {
        completionDismissed = false
        mutationMutex.withLock {
            currentVerse = loadStartingVerse(latestPrefs)
            render(latestPrefs)
        }
    }

    private suspend fun isEverythingRead(): Boolean =
        readingProgressRepository.countRead() >= totalVerseCount()

    // The bundled Quran database ships whole with every app update, so its size
    // never changes at runtime — fetch it once instead of on every render.
    private var cachedTotalVerseCount: Int? = null

    private suspend fun totalVerseCount(): Int =
        cachedTotalVerseCount ?: quranRepository.totalVerseCount().also { cachedTotalVerseCount = it }

    // Marking the last unread ayah completes the Quran mid-session too.
    private suspend fun refreshCompletionState() {
        val allRead = isEverythingRead()
        _uiState.update { it.copy(isCompleted = allRead && !completionDismissed) }
    }

    fun resume() = viewModelScope.launch { settingsRepository.setAppActive(true) }

    // Steps the preview to the next/previous downloaded translation for the
    // active display language, wrapping around. Never touches the persisted
    // default — pure "peek at another wording" for this ayah only.
    fun cycleTranslationSource(forward: Boolean) = viewModelScope.launch {
        val lang = latestPrefs.translationDisplay.toTranslationLanguage() ?: return@launch
        mutationMutex.withLock {
            val downloaded = TranslationCatalog.all.filter { it.language == lang && translationRepository.isDownloaded(it) }
            if (downloaded.size < 2) return@withLock
            val currentId = translationOverrideId ?: activeTranslation(lang, latestPrefs).id
            val currentIndex = downloaded.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
            val stepDir = if (forward) 1 else -1
            translationOverrideId = downloaded[(currentIndex + stepDir + downloaded.size) % downloaded.size].id
            render(latestPrefs)
        }
    }

    // Drops the preview back to the real default when the switcher closes.
    fun resetTranslationSource() = viewModelScope.launch {
        mutationMutex.withLock {
            if (translationOverrideId == null) return@withLock
            translationOverrideId = null
            render(latestPrefs)
        }
    }

    fun setTriggeredPackage(packageName: String?) = viewModelScope.launch {
        val label = packageName?.let { monitoredAppsRepository.getAppLabel(it) ?: it }
        _uiState.update { it.copy(triggeredAppLabel = label) }
    }

    // Records that the interstitial session ended (dismissed via back or the
    // open-app button). Refreshes the per-app shown timestamp so cooldown/
    // interval bookkeeping stays accurate; revealing the underlying task is
    // handled by the caller finishing the activity.
    fun dismissInterstitial(packageName: String, onDismissed: () -> Unit) = viewModelScope.launch {
        monitoredAppsRepository.recordShown(packageName)
        onDismissed()
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
        val readIds = readingProgressRepository.getReadVerseIds().toHashSet()
        return when (prefs.readingMode) {
            ReadingMode.RANDOM -> quranRepository.getRandomUnreadVerse(readIds)
            ReadingMode.SEQUENTIAL -> quranRepository.getFirstUnreadVerse(readIds) ?: quranRepository.getFirstVerse()
        }
    }

    // Caller must hold mutationMutex.
    private suspend fun render(prefs: UserPreferences) {
        val verse = currentVerse ?: return

        // The independent lookups run concurrently — sequentially a render
        // costs ~6 DB round-trips (per swipe, and per settings tick).
        coroutineScope {
            val surahDeferred = async { quranRepository.getSurah(verse.surahNo) }
            val isReadDeferred = async { readingProgressRepository.isRead(verse.id) }
            val allReadDeferred = async { isEverythingRead() }
            val nextPreviewDeferred =
                async { quranRepository.getNextVerse(verse.id)?.let { buildPreview(it, prefs) } }
            val previousPreviewDeferred =
                async { quranRepository.getPreviousVerse(verse.id)?.let { buildPreview(it, prefs) } }

            val translationLanguage = prefs.translationDisplay.toTranslationLanguage()
            // Downloaded alternatives decide whether the switcher shows at all;
            // catalog entries without a local file have nothing to preview.
            val downloadedTranslations = translationLanguage
                ?.let { lang -> TranslationCatalog.all.filter { it.language == lang && translationRepository.isDownloaded(it) } }
                .orEmpty()
            val defaultMeta = translationLanguage?.let { activeTranslation(it, prefs) }
            val shownMeta = downloadedTranslations.find { it.id == translationOverrideId } ?: defaultMeta
            val translationText = shownMeta?.let { translationRepository.getText(it, verse.id) }
            val translitText = when (prefs.pronunciation) {
                AidLanguage.NONE -> null
                AidLanguage.ENGLISH -> verse.enTransliteration
                AidLanguage.BENGALI -> verse.bnTransliteration
            }

            _uiState.update { current ->
                current.copy(
                    isLoading = false,
                    isPaused = !prefs.appActive,
                    surahName = surahDeferred.await()?.let { surahDisplayName(it, prefs.surahNameLanguage) } ?: "",
                    surahNameDirection = if (prefs.surahNameLanguage == NameDisplayLanguage.ARABIC) LayoutDirection.Rtl else LayoutDirection.Ltr,
                    ayahLabel = ayahLabel(verse, prefs.surahNameLanguage),
                    totalLabel = surahDeferred.await()?.let { "${localizeDigits(it.ayahCount, prefs.surahNameLanguage)} ${ayahWord(prefs.surahNameLanguage)}" } ?: "",
                    arabicText = verse.arabicTextFor(prefs.arabicScript),
                    arabicFont = prefs.arabicFont,
                    arabicFontSize = prefs.arabicFontSize,
                    translitText = translitText,
                    translitFontSize = prefs.translitFontSize,
                    translationText = translationText,
                    translationFontSize = prefs.translationFontSize,
                    translationSourceName = shownMeta?.name,
                    translationHasAlternates = downloadedTranslations.size > 1,
                    isMarkedRead = isReadDeferred.await(),
                    readingMode = prefs.readingMode,
                    isCompleted = allReadDeferred.await() && !completionDismissed,
                    // triggeredAppLabel untouched — owned by setTriggeredPackage()
                    nextPreview = nextPreviewDeferred.await(),
                    previousPreview = previousPreviewDeferred.await(),
                )
            }
        }
    }

    // Like render(), minus what only applies to the ayah actually being read
    // (override mode, read status, header) and always with the real default
    // translation — a peeked neighbour isn't in compare mode.
    private suspend fun buildPreview(verse: VerseEntity, prefs: UserPreferences): AyahPreview {
        val translationLanguage = prefs.translationDisplay.toTranslationLanguage()
        val meta = translationLanguage?.let { activeTranslation(it, prefs) }
        val translationText = meta?.let { translationRepository.getText(it, verse.id) }
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
            translationText = translationText,
            translationFontSize = prefs.translationFontSize,
        )
    }

    private fun activeTranslation(lang: TranslationLanguage, prefs: UserPreferences): TranslationMeta {
        val id = if (lang == TranslationLanguage.ENGLISH) prefs.activeTranslationEnglish else prefs.activeTranslationBengali
        return TranslationCatalog.all.first { it.language == lang && it.id == id }
    }

}
