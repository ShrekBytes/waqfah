package dev.shrekbytes.waqfah.ui.reading

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.shrekbytes.waqfah.data.bookmark.BookmarkCollection
import dev.shrekbytes.waqfah.data.repository.ReadingProgressRepository
import dev.shrekbytes.waqfah.data.repository.SettingsRepository
import dev.shrekbytes.waqfah.data.repository.TranslationRepository
import dev.shrekbytes.waqfah.data.repository.VerseSelection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

// The reading session behind every Home-side surface — the Home tab, the tour's
// practice card and the surah picker — all of which walk the whole Quran. The
// interstitial has a ViewModel of its own (InterstitialReadingViewModel),
// because it is the one surface that follows the reading mode's Bookmarks
// option; this class is deliberately handed the plain mushaf selection, so the
// chip can never move Home off the Quran.
//
// Everything that must survive across activities — read status and the bookmark
// collection — is persisted in Room, not held in memory.
//
// The reading machine itself lives in ReadingSession; this class is only its
// Android adapter: it hands the session its three signals and exposes the
// session for every verb and state read. Every behavioural question — stepping,
// rendering, mark-read, completion — is answered (and tested) there. The
// session's probes arrive through ReadingPorts, provided by AppModule
// (DefaultReadingPorts).
@HiltViewModel
class ReadingViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    translationRepository: TranslationRepository,
    readingProgressRepository: ReadingProgressRepository,
    ports: ReadingPorts,
    verseSelection: VerseSelection,
    bookmarks: BookmarkCollection,
) : ViewModel() {

    // The session's host scope: the same job as viewModelScope (so clearing
    // the ViewModel still cancels the session) plus one exception handler —
    // an unexpected Room/DataStore failure (disk pressure, SQLITE_BUSY, an
    // IO error) inside any render, step or mark-read must log and keep the
    // last good UI state, never kill the process.
    private val sessionScope = CoroutineScope(
        viewModelScope.coroutineContext + CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "Unhandled error in reading session coroutine", throwable)
        },
    )

    val session = ReadingSession(
        // filterNotNull: the session's first emission renders, so nothing
        // renders until prefs are loaded — same wait the cold flow imposed.
        preferences = settingsRepository.loadedPreferences.filterNotNull(),
        downloadedIds = translationRepository.downloadedIds,
        progressReset = readingProgressRepository.progressReset,
        ports = ports,
        verseSelection = verseSelection,
        bookmarks = bookmarks,
        scope = sessionScope,
    )

    // Which action-row controls the hosts draw — the reader's Advanced
    // hide-settings as presentation state. The session deliberately never
    // sees these: they change what the card draws, never what it renders
    // (ReadingRelevanceTest pins that).
    val readingControls: StateFlow<ReadingControlsVisibility> = settingsRepository.loadedPreferences
        .filterNotNull()
        .map(ReadingControlsVisibility::of)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReadingControlsVisibility())

    private companion object {
        private const val TAG = "ReadingViewModel"
    }
}
