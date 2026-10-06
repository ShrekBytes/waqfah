package dev.shrekbytes.waqfah.ui.reading

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.shrekbytes.waqfah.data.bookmark.BookmarkCollection
import dev.shrekbytes.waqfah.data.installedapp.InstalledAppCatalog
import dev.shrekbytes.waqfah.data.repository.ModeAwareVerseSequence
import dev.shrekbytes.waqfah.data.repository.ReadingProgressRepository
import dev.shrekbytes.waqfah.data.repository.SettingsRepository
import dev.shrekbytes.waqfah.data.repository.TranslationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject

// The interstitial's own ReadingSession: the same reading machine as
// ReadingViewModel's, handed the mode-aware sequence instead of the mushaf's, so
// the pause screen reads whichever collection the reading mode names while every
// other surface stays on the whole Quran.
//
// A ViewModel of its own because the sequence is the one thing a host decides
// and it is fixed at construction. ReadingViewModel is shared by the Home tab,
// the tour's practice card and the surah picker — all of them the whole Quran
// whatever the mode says — so handing it the mode-aware sequence would make Home
// follow the chip, which is exactly what it must not do. TriggerActivity hosted
// a ReadingViewModel; it hosts this instead and nothing else about the screen
// changes. Like its two siblings this is only the session's Android adapter, and
// the wiring below mirrors ReadingViewModel's on purpose: the scope must be tied
// to this ViewModel's own lifetime, and a shared builder would only forward the
// same arguments to the same constructor.
@HiltViewModel
class InterstitialReadingViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    translationRepository: TranslationRepository,
    readingProgressRepository: ReadingProgressRepository,
    private val installedAppCatalog: InstalledAppCatalog,
    ports: ReadingPorts,
    verseSequence: ModeAwareVerseSequence,
    bookmarks: BookmarkCollection,
) : ViewModel() {

    // The session's host scope, with the same exception handler its siblings
    // give theirs: a Room/DataStore failure inside a render, step or mark-read
    // must log and keep the last good card, never kill the process.
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
        verseSelection = verseSequence,
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
        private const val TAG = "InterstitialReadingViewModel"
    }
}
