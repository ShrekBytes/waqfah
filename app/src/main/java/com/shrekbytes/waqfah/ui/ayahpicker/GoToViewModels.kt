package com.shrekbytes.waqfah.ui.ayahpicker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shrekbytes.waqfah.data.local.core.SurahEntity
import com.shrekbytes.waqfah.data.local.core.VerseEntity
import com.shrekbytes.waqfah.data.model.NameDisplayLanguage
import com.shrekbytes.waqfah.data.model.UserPreferences
import com.shrekbytes.waqfah.data.repository.QuranRepository
import com.shrekbytes.waqfah.data.repository.ReadingProgressRepository
import com.shrekbytes.waqfah.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SurahRow(
    val surah: SurahEntity,
    val readCount: Int,
)

@HiltViewModel
class GoToSurahViewModel @Inject constructor(
    private val quranRepository: QuranRepository,
    private val readingProgressRepository: ReadingProgressRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")

    private val _uiState = MutableStateFlow(GoToSurahUiState(isLoading = true))
    val uiState: StateFlow<GoToSurahUiState> = _uiState

    // Cache unfiltered rows so typing only filters in memory, not in the DB.
    private var cachedUnfilteredRows: List<SurahRow> = emptyList()
    private var cachedPrefs: UserPreferences? = null

    // The bundled Quran database ships whole with every app update, so surahs
    // and their verse ids never change at runtime — fetch each once per
    // ViewModel (same pattern as ReadingViewModel's cachedTotalVerseCount).
    // Only read progress is re-read per emission below.
    private var cachedSurahs: List<SurahEntity>? = null
    private var cachedVerseIdsBySurah: Map<Int, List<Int>>? = null

    private suspend fun surahs(): List<SurahEntity> =
        cachedSurahs ?: quranRepository.getAllSurahs().also { cachedSurahs = it }

    private suspend fun verseIdsBySurah(): Map<Int, List<Int>> =
        cachedVerseIdsBySurah ?: quranRepository.getAllVerseSurahPairs()
            .groupBy({ it.surahNo }, { it.verseId })
            .also { cachedVerseIdsBySurah = it }

    private suspend fun buildSurahRows(): List<SurahRow> {
        val readIds = readingProgressRepository.getReadVerseIds().toHashSet()
        val idsBySurah = verseIdsBySurah()
        return surahs().map { surah ->
            val read = idsBySurah[surah.surahNo].orEmpty().count { it in readIds }
            SurahRow(surah, read)
        }
    }

    init {
        viewModelScope.launch {
            // Base rows: refresh only when prefs or read progress changes (not on
            // every keystroke). filterNotNull holds rows until prefs are loaded.
            combine(
                settingsRepository.loadedPreferences.filterNotNull(),
                readingProgressRepository.readCount,
            ) { prefs, _ -> prefs }
                .collect { prefs ->
                    cachedPrefs = prefs
                    cachedUnfilteredRows = buildSurahRows()
                    emitFiltered(searchQuery.value, prefs)
                }
        }
        viewModelScope.launch {
            // In-memory filter – immediate, no DB, so no debounce needed.
            searchQuery.collect { query ->
                val prefs = cachedPrefs ?: return@collect
                emitFiltered(query, prefs)
            }
        }
    }

    private fun emitFiltered(query: String, prefs: UserPreferences) {
        val trimmed = query.trim()
        val filtered = if (trimmed.isBlank()) cachedUnfilteredRows else cachedUnfilteredRows.filter { row ->
            val surah = row.surah
            surah.nameEnglish?.contains(query, ignoreCase = true) == true ||
                surah.nameBengali?.contains(query, ignoreCase = true) == true ||
                surah.nameArabic?.contains(query, ignoreCase = true) == true ||
                surah.surahNo.toString() == trimmed
        }
        _uiState.value = GoToSurahUiState(
            query = query,
            rows = filtered,
            surahNameLanguage = prefs.surahNameLanguage,
            isLoading = false,
        )
    }

    fun setQuery(q: String) { searchQuery.value = q }

    // Actions for jumping to a verse — thin repo wrappers used straight from
    // the screen's taps (no state, so they live on the same ViewModel rather
    // than a separate facade).
    suspend fun getVerse(surahNo: Int, ayahNo: Int): VerseEntity? =
        quranRepository.getVerse(surahNo, ayahNo)

    suspend fun getReadIds(): Set<Int> = readingProgressRepository.getReadVerseIds().toHashSet()

    suspend fun getFirstUnreadInSurah(surahNo: Int, readIds: Set<Int>): VerseEntity? =
        quranRepository.getFirstUnreadVerseInSurah(surahNo, readIds)
}

data class GoToSurahUiState(
    val query: String = "",
    val rows: List<SurahRow> = emptyList(),
    val surahNameLanguage: NameDisplayLanguage = NameDisplayLanguage.ENGLISH,
    val isLoading: Boolean = true,
)
