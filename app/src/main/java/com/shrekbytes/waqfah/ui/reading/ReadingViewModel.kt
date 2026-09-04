package com.shrekbytes.waqfah.ui.reading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shrekbytes.waqfah.data.repository.MonitoredAppsRepository
import com.shrekbytes.waqfah.data.repository.QuranRepository
import com.shrekbytes.waqfah.data.repository.ReadingProgressRepository
import com.shrekbytes.waqfah.data.repository.SettingsRepository
import com.shrekbytes.waqfah.data.repository.TranslationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject

// HomeScreen and ReadingScreen live in separate Activities (MainActivity and
// TriggerActivity), so each gets its own instance. Everything that must survive
// across them — read status — is persisted in Room, not held in memory.
//
// The reading machine itself lives in ReadingSession; this class is only its
// Android adapter: it wires repositories to the session's ports and exposes the
// session through ViewModel-shaped members. Every behavioural question —
// stepping, rendering, mark-read, completion — is answered (and tested) there.
@HiltViewModel
class ReadingViewModel @Inject constructor(
    quranRepository: QuranRepository,
    translationRepository: TranslationRepository,
    readingProgressRepository: ReadingProgressRepository,
    private val monitoredAppsRepository: MonitoredAppsRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val session = ReadingSession(
        // filterNotNull: the session's first emission renders, so nothing
        // renders until prefs are loaded — same wait the cold flow imposed.
        preferences = settingsRepository.loadedPreferences.filterNotNull(),
        downloadedIds = translationRepository.downloadedIds,
        progressReset = readingProgressRepository.progressReset,
        verseById = quranRepository::getVerseById,
        nextVerse = quranRepository::getNextVerse,
        previousVerse = quranRepository::getPreviousVerse,
        firstUnreadVerse = quranRepository::getFirstUnreadVerse,
        randomUnreadVerse = quranRepository::getRandomUnreadVerse,
        firstVerse = quranRepository::getFirstVerse,
        surah = quranRepository::getSurah,
        totalVerseCount = quranRepository::totalVerseCount,
        readVerseIds = readingProgressRepository::getReadVerseIds,
        isRead = readingProgressRepository::isRead,
        markRead = readingProgressRepository::markRead,
        unmarkRead = readingProgressRepository::unmarkRead,
        countRead = readingProgressRepository::countRead,
        resetAll = readingProgressRepository::resetAll,
        translationText = translationRepository::getText,
        setReadingMode = settingsRepository::setReadingMode,
        scope = viewModelScope,
    )

    val uiState: StateFlow<ReadingUiState> = session.uiState

    // Awaited mid-gesture by the card to sequence swipe animation, verse swap,
    // and offset reset strictly.
    suspend fun next() = session.next()
    suspend fun previous() = session.previous()

    fun markCurrentRead() = session.markCurrentRead()
    fun dismissCompletion() = session.dismissCompletion()
    fun startOver() = session.startOver()
    fun switchModeAndRestart() = session.switchModeAndRestart()
    suspend fun jumpToVerse(verseId: Int) = session.jumpToVerse(verseId)
    fun cycleTranslationSource(forward: Boolean) = session.cycleTranslationSource(forward)
    fun resetTranslationSource() = session.resetTranslationSource()

    // The interstitial's "you opened <app>" caption — label resolution is
    // adapter work (package-manager lookups), the state write is the session's.
    fun setTriggeredPackage(packageName: String?) = viewModelScope.launch {
        val label = packageName?.let { monitoredAppsRepository.getAppLabel(it) ?: it }
        session.setTriggeredAppLabel(label)
    }
}
