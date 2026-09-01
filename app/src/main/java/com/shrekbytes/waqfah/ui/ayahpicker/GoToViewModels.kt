package com.shrekbytes.waqfah.ui.ayahpicker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shrekbytes.waqfah.data.local.core.SurahEntity
import com.shrekbytes.waqfah.data.local.core.VerseEntity
import com.shrekbytes.waqfah.data.repository.QuranRepository
import com.shrekbytes.waqfah.data.repository.ReadingProgressRepository
import com.shrekbytes.waqfah.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SurahRow(
    val surah: SurahEntity,
    val readCount: Int,
    val total: Int,
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

    // Cache unfiltered rows so typing only filters in memory, not 114 DB queries per keystroke.
    private var cachedUnfilteredRows: List<SurahRow> = emptyList()
    private var cachedPrefs: com.shrekbytes.waqfah.data.model.UserPreferences? = null

    init {
        viewModelScope.launch {
            // Base rows: refresh only when prefs or read progress changes (not on every keystroke).
            kotlinx.coroutines.flow.combine(
                settingsRepository.preferences,
                readingProgressRepository.readCount,
            ) { p, _ -> p }
                .collect { prefs ->
                    cachedPrefs = prefs
                    val surahs = quranRepository.getAllSurahs()
                    val readIds = readingProgressRepository.getReadVerseIds().toHashSet()
                    cachedUnfilteredRows = surahs.map { surah ->
                        val ids = quranRepository.getVerseIdsForSurah(surah.surahNo)
                        val read = ids.count { it in readIds }
                        SurahRow(surah, read, surah.ayahCount)
                    }
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

    private fun emitFiltered(query: String, prefs: com.shrekbytes.waqfah.data.model.UserPreferences) {
        val filtered = if (query.isBlank()) cachedUnfilteredRows else cachedUnfilteredRows.filter { row ->
            val surah = row.surah
            val nameEn = surah.nameEnglish ?: ""
            val nameBn = surah.nameBengali ?: ""
            val nameAr = surah.nameArabic ?: ""
            val surahNoStr = surah.surahNo.toString()
            nameEn.contains(query, ignoreCase = true) ||
                nameBn.contains(query, ignoreCase = true) ||
                nameAr.contains(query, ignoreCase = true) ||
                surahNoStr == query.trim()
        }
        _uiState.value = GoToSurahUiState(
            query = query,
            rows = filtered,
            surahNameLanguage = prefs.surahNameLanguage,
            isLoading = false,
        )
    }

    fun setQuery(q: String) { searchQuery.value = q }

    // Actions for jumping to a verse — thin repos-wraps used straight from the
    // screen's taps (no state, so they live on the same ViewModel rather than a
    // separate facade).
    suspend fun getVerse(surahNo: Int, ayahNo: Int): VerseEntity? =
        quranRepository.getVerse(surahNo, ayahNo)

    suspend fun getReadIds(): Set<Int> = readingProgressRepository.getReadVerseIds().toHashSet()

    suspend fun getFirstUnreadInSurah(surahNo: Int, readIds: Set<Int>): VerseEntity? =
        quranRepository.getFirstUnreadVerseInSurah(surahNo, readIds)
}

data class GoToSurahUiState(
    val query: String = "",
    val rows: List<SurahRow> = emptyList(),
    val surahNameLanguage: com.shrekbytes.waqfah.data.model.NameDisplayLanguage = com.shrekbytes.waqfah.data.model.NameDisplayLanguage.ENGLISH,
    val isLoading: Boolean = true,
)
