package dev.shrekbytes.waqfah.ui.bookmarks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.shrekbytes.waqfah.data.bookmark.BookmarkCollection
import dev.shrekbytes.waqfah.data.local.core.SurahEntity
import dev.shrekbytes.waqfah.data.model.ArabicFont
import dev.shrekbytes.waqfah.data.model.ArabicScript
import dev.shrekbytes.waqfah.data.model.NameDisplayLanguage
import dev.shrekbytes.waqfah.data.repository.QuranRepository
import dev.shrekbytes.waqfah.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BookmarksListUiState(
    val rows: List<BookmarkSurahRow> = emptyList(),
    val surahNameLanguage: NameDisplayLanguage = NameDisplayLanguage.ENGLISH,
    // The reader's Arabic settings, so the list's ayah text agrees with the card
    // they read on — same script column, same font.
    val arabicScript: ArabicScript = ArabicScript.INDOPAK,
    val arabicFont: ArabicFont = ArabicFont.DIGITAL_KHATT_INDOPAK,
    val isLoading: Boolean = true,
)

// The bookmarks list's data (#22). It reads the collection's observable set and
// the read-only Quran text and projects them through bookmarkSurahRows — the
// grouping and both orderings live there, not here, so this class is only the
// fetch and the preferences echo.
//
// Deliberately no ReadingSession: a row tap is the Bookmarks card's session's
// jump, and that session is handed to the screen by its host. Holding one here
// would be a third session, which is exactly the mistake the ticket warns about.
@HiltViewModel
class BookmarksListViewModel @Inject constructor(
    private val quranRepository: QuranRepository,
    private val bookmarks: BookmarkCollection,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookmarksListUiState())
    val uiState: StateFlow<BookmarksListUiState> = _uiState

    // The bundled Quran database ships whole with every app update, so the surah
    // table never changes at runtime — fetch it once per ViewModel, the same
    // pattern GoToSurahViewModel uses. Only the collection is re-read per
    // emission.
    private var cachedSurahs: List<SurahEntity>? = null

    init {
        viewModelScope.launch {
            combine(
                bookmarks.savedVerseIds,
                settingsRepository.loadedPreferences.filterNotNull(),
            ) { saved, prefs -> saved to prefs }
                .collect { (saved, prefs) ->
                    val surahs = cachedSurahs ?: quranRepository.getAllSurahs().also { cachedSurahs = it }
                    // The set arrives in no particular order; bookmarkSurahRows
                    // imposes Quran order. One query for the whole set, not one
                    // per saved ayah.
                    val verses = quranRepository.getVersesByIds(saved.toList())
                    _uiState.value = BookmarksListUiState(
                        rows = bookmarkSurahRows(surahs, verses),
                        surahNameLanguage = prefs.surahNameLanguage,
                        arabicScript = prefs.arabicScript,
                        arabicFont = prefs.arabicFont,
                        isLoading = false,
                    )
                }
        }
    }
}
