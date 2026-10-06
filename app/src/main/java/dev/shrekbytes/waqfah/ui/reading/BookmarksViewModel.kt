package dev.shrekbytes.waqfah.ui.reading

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.shrekbytes.waqfah.data.bookmark.BookmarkCollection
import dev.shrekbytes.waqfah.data.repository.BookmarkedAyahStepper
import dev.shrekbytes.waqfah.data.repository.ReadingProgressRepository
import dev.shrekbytes.waqfah.data.repository.SettingsRepository
import dev.shrekbytes.waqfah.data.repository.TranslationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterNotNull
import javax.inject.Inject

// The Bookmarks tab's own ReadingSession (ADR-0005): the same reading machine
// as ReadingViewModel's, handed the collection-scoped stepper instead of the
// mushaf's, so the two tabs hold independent positions and switching tabs
// moves neither card. Everything else — rendering, mark-read, the bookmark
// toggle, the empty state — is the session's, unchanged.
//
// A ViewModel of its own rather than a second session inside ReadingViewModel:
// TriggerActivity hosts a ReadingViewModel too, and it would otherwise build a
// bookmarks session it can never show. Like ReadingViewModel, this is only the
// session's Android adapter, and the wiring below mirrors its sibling's on
// purpose — the scope must be tied to this ViewModel's own lifetime, and a
// shared builder would only forward the same seven arguments to the same
// constructor.
@HiltViewModel
class BookmarksViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    translationRepository: TranslationRepository,
    readingProgressRepository: ReadingProgressRepository,
    ports: ReadingPorts,
    stepper: BookmarkedAyahStepper,
    bookmarks: BookmarkCollection,
) : ViewModel() {

    // The session's host scope, with the same exception handler
    // ReadingViewModel gives it: a Room/DataStore failure inside a render,
    // step or toggle must log and keep the last good card, never kill the
    // process. ReadingSession itself does no Android logging, which is what
    // keeps it JVM-testable.
    private val sessionScope = CoroutineScope(
        viewModelScope.coroutineContext + CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "Unhandled error in reading session coroutine", throwable)
        },
    )

    val session = ReadingSession(
        preferences = settingsRepository.loadedPreferences.filterNotNull(),
        downloadedIds = translationRepository.downloadedIds,
        progressReset = readingProgressRepository.progressReset,
        ports = ports,
        verseSelection = stepper,
        bookmarks = bookmarks,
        scope = sessionScope,
    )

    private companion object {
        private const val TAG = "BookmarksViewModel"
    }
}
