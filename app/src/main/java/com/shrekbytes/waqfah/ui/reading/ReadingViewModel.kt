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

// HomeScreen and ReadingScreen live in separate Activities (MainActivity and
// TriggerActivity), so each gets its own instance. Everything that must survive
// across them — reading position and read status — is persisted in
// DataStore/Room, not held in memory.
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

    // Session-local "compare translations" override for the current ayah; null
    // means show the real default. Cleared on every step() so it never outlives
    // the ayah it was opened on.
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

    // Suspend so ReadingCard can await these mid-gesture to sequence the swipe
    // animation, verse swap, and offset reset strictly.
    suspend fun next() = step { quranRepository.getNextVerse(it) }
    suspend fun previous() = step { quranRepository.getPreviousVerse(it) }

    fun markCurrentRead() = viewModelScope.launch {
        val verse = currentVerse ?: return@launch
        val newIsRead = !_uiState.value.isMarkedRead
        // Optimistic UI update first — awaiting the DB write delays feedback.
        _uiState.update { it.copy(isMarkedRead = newIsRead) }
        if (newIsRead) {
            readingProgressRepository.markRead(verse.id)
        } else {
            readingProgressRepository.unmarkRead(verse.id)
        }
    }

    fun resume() = viewModelScope.launch { settingsRepository.setAppActive(true) }

    // Steps the preview to the next/previous downloaded translation for the
    // active display language, wrapping around. Never touches the persisted
    // default — pure "peek at another wording" for this ayah only.
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
        val stepDir = if (forward) 1 else -1
        translationOverrideId = downloaded[(currentIndex + stepDir + downloaded.size) % downloaded.size].id
        render(latestPrefs)
    }

    // Drops the preview back to the real default when the switcher closes.
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

    private suspend fun step(load: suspend (Int) -> VerseEntity?) {
        val fromId = currentVerse?.id ?: return
        currentVerse = load(fromId)
        currentVerse?.let { settingsRepository.setLastViewedVerseId(it.id) }
        // A fresh ayah always starts on the real default translation.
        translationOverrideId = null
        render(latestPrefs)
    }

    // Picks the *starting* verse of a fresh session only; prev/next always step
    // sequentially by id regardless of mode. Sequential resumes from
    // lastViewedVerseId; Random ignores it deliberately (resuming would make it
    // behave exactly like Sequential after the first launch).
    private suspend fun loadStartingVerse(prefs: UserPreferences): VerseEntity? {
        val start = when (prefs.readingMode) {
            ReadingMode.RANDOM -> quranRepository.getRandomVerse()
            ReadingMode.SEQUENTIAL -> prefs.lastViewedVerseId?.let { quranRepository.getVerseById(it) }
                ?: quranRepository.getFirstVerse()
        }
        val landed = skipAlreadyRead(start)
        // Save the landing spot so sequential mode's next fresh load resumes
        // from here instead of re-scanning past the same read verses.
        if (landed != null && landed.id != prefs.lastViewedVerseId) {
            settingsRepository.setLastViewedVerseId(landed.id)
        }
        return landed
    }

    // The saved position can land on an already-read verse (e.g. after stepping
    // backward); skip forward past it, bounded so a fully-read Quran can't loop.
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

        // Refreshed every render so display-setting changes update peek content
        // too, ready before a swipe even starts.
        val nextPreview = quranRepository.getNextVerse(verse.id)?.let { buildPreview(it, prefs) }
        val previousPreview = quranRepository.getPreviousVerse(verse.id)?.let { buildPreview(it, prefs) }

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
                // triggeredAppLabel untouched — owned by setTriggeredPackage()
                nextPreview = nextPreview,
                previousPreview = previousPreview,
            )
        }
    }

    // Like render(), minus what only applies to the ayah actually being read
    // (override mode, read status, header) and always with the real default
    // translation — a peeked neighbour isn't in compare mode.
    private suspend fun buildPreview(verse: VerseEntity, prefs: UserPreferences): AyahPreview {
        val translationLanguage = when (prefs.translationDisplay) {
            AidLanguage.NONE -> null
            AidLanguage.ENGLISH -> TranslationLanguage.ENGLISH
            AidLanguage.BENGALI -> TranslationLanguage.BENGALI
        }
        val meta = translationLanguage?.let { activeTranslation(it, prefs) }
        val translationText = meta?.let { translationRepository.getText(it, verse.id) }
        val translitText = when (prefs.pronunciation) {
            AidLanguage.NONE -> null
            AidLanguage.ENGLISH -> verse.enTransliteration
            AidLanguage.BENGALI -> verse.bnTransliteration
        }
        return AyahPreview(
            ayahLabel = "${localizeDigits(verse.surahNo, prefs.surahNameLanguage)}:${localizeDigits(verse.ayahNo, prefs.surahNameLanguage)}",
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
        )
    }

    private fun activeTranslation(lang: TranslationLanguage, prefs: UserPreferences): TranslationMeta {
        val id = if (lang == TranslationLanguage.ENGLISH) prefs.activeTranslationEnglish else prefs.activeTranslationBengali
        return TranslationCatalog.all.first { it.language == lang && it.id == id }
    }

    private companion object {
        const val MAX_SKIP_STEPS = 50
    }
}
