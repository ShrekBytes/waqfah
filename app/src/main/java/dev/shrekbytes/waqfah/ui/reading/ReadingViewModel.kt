package dev.shrekbytes.waqfah.ui.reading

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.shrekbytes.waqfah.data.bookmark.BookmarkCollection
import dev.shrekbytes.waqfah.data.installedapp.InstalledAppCatalog
import dev.shrekbytes.waqfah.data.repository.ReadingProgressRepository
import dev.shrekbytes.waqfah.data.repository.SettingsRepository
import dev.shrekbytes.waqfah.data.repository.TranslationRepository
import dev.shrekbytes.waqfah.data.repository.VerseSelection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject

// HomeScreen and ReadingScreen live in separate Activities (MainActivity and
// TriggerActivity), so each gets its own instance. Everything that must survive
// across them — read status and the bookmark collection — is persisted in Room,
// not held in memory.
//
// The reading machine itself lives in ReadingSession; this class is only its
// Android adapter: it hands the session its three signals, exposes the session
// for every verb and state read, and resolves the interstitial's package label.
// Every behavioural question — stepping, rendering, mark-read, completion — is
// answered (and tested) there. The session's probes arrive through
// ReadingPorts, provided by AppModule (DefaultReadingPorts).
@HiltViewModel
class ReadingViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    translationRepository: TranslationRepository,
    readingProgressRepository: ReadingProgressRepository,
    private val installedAppCatalog: InstalledAppCatalog,
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

    // The interstitial's "you opened <app>" caption — label resolution is
    // adapter work (package-manager lookups), the state write is the session's.
    fun setTriggeredPackage(packageName: String?) = viewModelScope.launch {
        val label = packageName?.let { installedAppCatalog.labelFor(it) ?: it }
        session.setTriggeredAppLabel(label)
    }

    private companion object {
        private const val TAG = "ReadingViewModel"
    }
}
