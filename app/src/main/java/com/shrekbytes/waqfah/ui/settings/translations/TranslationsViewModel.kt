package com.shrekbytes.waqfah.ui.settings.translations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shrekbytes.waqfah.data.model.TranslationCatalog
import com.shrekbytes.waqfah.data.model.TranslationLanguage
import com.shrekbytes.waqfah.data.model.TranslationMeta
import com.shrekbytes.waqfah.data.repository.SettingsRepository
import com.shrekbytes.waqfah.data.repository.TranslationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TranslationRowState(
    val meta: TranslationMeta,
    val isDownloaded: Boolean,
    val isActive: Boolean,
    val isDownloading: Boolean,
)

@HiltViewModel
class TranslationsViewModel @Inject constructor(
    private val translationRepository: TranslationRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val downloadingId = MutableStateFlow<String?>(null)

    // Ticks up after every download()/delete() so the derived rows below re-read
    // TranslationRepository.isDownloaded() from disk. A real app might instead
    // track downloaded ids as rows in Room for proper reactivity — fine as a
    // simple nudge for now, since downloads/deletes are rare, user-initiated actions.
    private val refreshTrigger = MutableStateFlow(0)

    private val rowFlows = mutableMapOf<TranslationLanguage, StateFlow<List<TranslationRowState>>>()

    fun rowsFor(language: TranslationLanguage): StateFlow<List<TranslationRowState>> =
        rowFlows.getOrPut(language) {
            combine(settingsRepository.preferences, downloadingId, refreshTrigger) { prefs, downloading, _ ->
                val activeId = if (language == TranslationLanguage.ENGLISH) prefs.activeTranslationEnglish else prefs.activeTranslationBengali
                TranslationCatalog.all.filter { it.language == language }.map { meta ->
                    TranslationRowState(
                        meta = meta,
                        isDownloaded = translationRepository.isDownloaded(meta),
                        isActive = meta.id == activeId,
                        isDownloading = downloading == meta.id,
                    )
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        }

    fun select(meta: TranslationMeta) = viewModelScope.launch {
        settingsRepository.setActiveTranslation(meta.language, meta.id)
    }

    fun download(meta: TranslationMeta) = viewModelScope.launch {
        downloadingId.value = meta.id
        try {
            translationRepository.download(meta)
        } finally {
            downloadingId.value = null
            refreshTrigger.update { it + 1 }
        }
    }

    fun delete(meta: TranslationMeta) = viewModelScope.launch {
        translationRepository.delete(meta)
        refreshTrigger.update { it + 1 }
    }
}
