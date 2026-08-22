package com.shrekbytes.waqfah.ui.reading

import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shrekbytes.waqfah.data.local.core.VerseEntity
import com.shrekbytes.waqfah.data.model.AidLanguage
import com.shrekbytes.waqfah.data.model.ArabicScript
import com.shrekbytes.waqfah.data.model.NameDisplayLanguage
import com.shrekbytes.waqfah.data.model.ReadingMode
import com.shrekbytes.waqfah.data.model.TranslationCatalog
import com.shrekbytes.waqfah.data.model.TranslationLanguage
import com.shrekbytes.waqfah.data.model.TranslationMeta
import com.shrekbytes.waqfah.data.model.UserPreferences
import com.shrekbytes.waqfah.data.repository.MonitoredAppsRepository
import com.shrekbytes.waqfah.data.repository.QuranRepository
import com.shrekbytes.waqfah.data.repository.ReadingProgressRepository
import com.shrekbytes.waqfah.data.repository.SettingsRepository
import com.shrekbytes.waqfah.data.repository.TranslationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// HomeScreen and ReadingScreen live in separate Activities now (MainActivity
// and TriggerActivity respectively — see TriggerActivity's doc comment for
// why), so each gets its own instance of this ViewModel; they're not sharing
// one via Activity-scoped Hilt injection the way they used to. That's fine:
// everything that needs to survive across them (current verse position via
// SettingsRepository.lastViewedVerseId, read status via
// ReadingProgressRepository) is already persisted, not held only in memory.
@HiltViewModel
class ReadingViewModel @Inject constructor(
    private val quranRepository: QuranRepository,
    private val translationRepository: TranslationRepository,
    private val readingProgressRepository: ReadingProgressRepository,
    private val monitoredAppsRepository: MonitoredAppsRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private var currentVerse: VerseEntity? = null
    private var latestPrefs = UserPreferences()

    // Session-local "compare translations" override for the current ayah —
    // null means "show the real default" (prefs.activeTranslationEnglish/
    // Bengali). Set by cycleTranslationSource(), cleared by
    // resetTranslationSource() and by step() on every next()/previous(), so
    // it never outlives the ayah it was opened on and never touches the
    // persisted default.
    private var translationOverrideId: String? = null

    private val _uiState = MutableStateFlow(ReadingUiState())
    val uiState: StateFlow<ReadingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.preferences.collect { prefs ->
                latestPrefs = prefs
                if (currentVerse == null) currentVerse = loadStartingVerse(prefs)
                render(prefs)
            }
        }
    }

    fun next() = step { quranRepository.getNextVerse(it) }
    fun previous() = step { quranRepository.getPreviousVerse(it) }

    // Toggles the current ayah's read status — tapping an already-marked
    // ayah unmarks it again, rather than read status only ever going one
    // way. Only affects the current ayah — doesn't move on to another one.
    // Whether a *later* session shows a fresh ayah is handled separately by
    // skipAlreadyRead() below, when a new ReadingViewModel instance loads.
    fun markCurrentRead() = viewModelScope.launch {
        val verse = currentVerse ?: return@launch
        val newIsRead = !_uiState.value.isMarkedRead
        // Update the UI first, write to Room after — an optimistic update.
        // Waiting on the suspend DB write before showing any visual feedback
        // is what made this feel laggy: the animation didn't even start until
        // the write finished.
        _uiState.update { it.copy(isMarkedRead = newIsRead) }
        if (newIsRead) {
            readingProgressRepository.markRead(verse.id)
        } else {
            readingProgressRepository.unmarkRead(verse.id)
        }
    }

    fun resume() = viewModelScope.launch { settingsRepository.setAppActive(true) }

    // Steps the reading screen's translation preview to the next/previous
    // *downloaded* translation for the active display language, wrapping
    // around. This only ever updates translationOverrideId — the user's real
    // default (settingsRepository) is never touched, so it's a pure "let me
    // peek at another wording" action.
    fun cycleTranslationSource(forward: Boolean) = viewModelScope.launch {
        val lang = when (latestPrefs.translationDisplay) {
            AidLanguage.NONE -> return@launch
            AidLanguage.ENGLISH -> TranslationLanguage.ENGLISH
            AidLanguage.BENGALI -> TranslationLanguage.BENGALI
        }
        val downloaded = TranslationCatalog.all.filter { it.language == lang && translationRepository.isDownloaded(it) }
        if (downloaded.size < 2) return@launch
        val currentId = translationOverrideId ?: activeTranslation(lang, latestPrefs).id
        val currentIndex = downloaded.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
        val step = if (forward) 1 else -1
        translationOverrideId = downloaded[(currentIndex + step + downloaded.size) % downloaded.size].id
        render(latestPrefs)
    }

    // Drops the preview back to the real default — called when the user
    // closes the on-screen switcher, so "compare mode" always ends back on
    // the actual setting rather than whatever was last previewed.
    fun resetTranslationSource() = viewModelScope.launch {
        if (translationOverrideId == null) return@launch
        translationOverrideId = null
        render(latestPrefs)
    }

    fun setTriggeredPackage(packageName: String?) = viewModelScope.launch {
        val label = packageName?.let { monitoredAppsRepository.getAppLabel(it) ?: it }
        _uiState.update { it.copy(triggeredAppLabel = label) }
    }

    fun openTriggeredApp(packageName: String, launch: () -> Unit) = viewModelScope.launch {
        monitoredAppsRepository.recordShown(packageName)
        launch()
    }

    private fun step(load: suspend (Int) -> VerseEntity?) = viewModelScope.launch {
        val fromId = currentVerse?.id ?: return@launch
        currentVerse = load(fromId)
        currentVerse?.let { settingsRepository.setLastViewedVerseId(it.id) }
        // A fresh ayah always starts on the real default translation — any
        // compare-mode preview was specific to the ayah being left behind.
        translationOverrideId = null
        render(latestPrefs)
    }

    // Reading-mode note: this only picks the *starting* verse for a fresh
    // session (new ReadingViewModel instance — see the class doc comment).
    // The prev/next arrows always step sequentially by id regardless of mode
    // — "next ayah" reads as sequential motion even in random mode, since
    // the prototype doesn't otherwise define what "random-mode next" would
    // mean.
    //
    // Sequential mode resumes from lastViewedVerseId so you pick up where
    // you left off. Random mode deliberately ignores it and always rolls a
    // fresh random ayah — resuming here would make Random behave exactly
    // like Sequential after the very first launch (once lastViewedVerseId is
    // set, which happens below on every load), which was the bug: Random
    // effectively only ever randomized once, ever.
    private suspend fun loadStartingVerse(prefs: UserPreferences): VerseEntity? {
        val start = when (prefs.readingMode) {
            ReadingMode.RANDOM -> quranRepository.getRandomVerse()
            ReadingMode.SEQUENTIAL -> prefs.lastViewedVerseId?.let { quranRepository.getVerseById(it) }
                ?: quranRepository.getFirstVerse()
        }
        val landed = skipAlreadyRead(start)
        // Save the landing spot so sequential mode's *next* fresh load
        // resumes from here directly, instead of re-scanning past the same
        // read verses again. (Random mode overwrites this again next time
        // anyway, since it never reads it back.)
        if (landed != null && landed.id != prefs.lastViewedVerseId) {
            settingsRepository.setLastViewedVerseId(landed.id)
        }
        return landed
    }

    // markCurrentRead() keeps lastViewedVerseId pointing at an unread ayah in
    // the normal case, but the saved position can still land on an
    // already-read one — e.g. the user stepped backward with the arrows and
    // left the app there. Step forward past it rather than show it again.
    // Bounded so a fully-read Quran can't loop forever.
    private suspend fun skipAlreadyRead(start: VerseEntity?): VerseEntity? {
        var verse = start
        repeat(MAX_SKIP_STEPS) {
            val candidate = verse ?: return null
            if (!readingProgressRepository.isRead(candidate.id)) return candidate
            verse = quranRepository.getNextVerse(candidate.id)
        }
        return verse
    }

    private suspend fun render(prefs: UserPreferences) {
        val verse = currentVerse ?: return
        val surah = quranRepository.getSurah(verse.surahNo)
        val isRead = readingProgressRepository.isRead(verse.id)

        val translationLanguage = when (prefs.translationDisplay) {
            AidLanguage.NONE -> null
            AidLanguage.ENGLISH -> TranslationLanguage.ENGLISH
            AidLanguage.BENGALI -> TranslationLanguage.BENGALI
        }
        // Downloaded alternatives for the active display language — what
        // cycleTranslationSource() cycles through and what decides whether
        // the reading screen's switcher shows at all. Catalog entries that
        // aren't actually downloaded have no local text to preview, so
        // they're excluded here rather than only checked at cycle-time.
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
                surahName = surah?.let { surahDisplayName(it, prefs.surahNameLanguage) } ?: "",
                surahNameDirection = if (prefs.surahNameLanguage == NameDisplayLanguage.ARABIC) LayoutDirection.Rtl else LayoutDirection.Ltr,
                ayahLabel = "${localizeDigits(verse.surahNo, prefs.surahNameLanguage)}:${localizeDigits(verse.ayahNo, prefs.surahNameLanguage)}",
                totalLabel = surah?.let { "${localizeDigits(it.ayahCount, prefs.surahNameLanguage)} ${ayahWord(prefs.surahNameLanguage)}" } ?: "",
                arabicText = when (prefs.arabicScript) {
                    ArabicScript.INDOPAK -> verse.arabicIndopak
                    ArabicScript.UTHMANI -> verse.arabicUthmani
                },
                arabicFont = prefs.arabicFont,
                arabicFontSize = prefs.arabicFontSize,
                translitText = translitText,
                translitFontSize = prefs.translitFontSize,
                translationText = translationText,
                translationFontSize = prefs.translationFontSize,
                translationSourceName = shownMeta?.name,
                translationHasAlternates = downloadedTranslations.size > 1,
                isMarkedRead = isRead,
                // triggeredAppLabel intentionally untouched — owned by setTriggeredPackage()
            )
        }
    }

    private fun activeTranslation(lang: TranslationLanguage, prefs: UserPreferences): TranslationMeta {
        val id = if (lang == TranslationLanguage.ENGLISH) prefs.activeTranslationEnglish else prefs.activeTranslationBengali
        return TranslationCatalog.all.first { it.language == lang && it.id == id }
    }

    private companion object {
        const val MAX_SKIP_STEPS = 50
    }
}
