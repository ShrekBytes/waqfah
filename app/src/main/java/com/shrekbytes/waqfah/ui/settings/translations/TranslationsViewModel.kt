package com.shrekbytes.waqfah.ui.settings.translations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shrekbytes.waqfah.data.model.TranslationCatalog
import com.shrekbytes.waqfah.data.model.TranslationLanguage
import com.shrekbytes.waqfah.data.model.TranslationMeta
import com.shrekbytes.waqfah.data.repository.SettingsRepository
import com.shrekbytes.waqfah.data.repository.TranslationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
    // 0f..1f while downloading with a known Content-Length; null otherwise.
    val downloadProgress: Float?,
    // Set after a failed download, cleared when a retry starts.
    val errorMessage: String?,
)

@HiltViewModel
class TranslationsViewModel @Inject constructor(
    private val translationRepository: TranslationRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    // Keyed by id — downloads for different translations run concurrently.
    private val downloadingIds = MutableStateFlow<Set<String>>(emptySet())
    private val downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    private val downloadErrors = MutableStateFlow<Map<String, String>>(emptyMap())

    // Bumped after every download()/delete() so rows re-read isDownloaded() from
    // disk — a simple nudge instead of tracking downloads in Room.
    private val refreshTrigger = MutableStateFlow(0)

    private val rowFlows = mutableMapOf<TranslationLanguage, StateFlow<List<TranslationRowState>>>()

    fun rowsFor(language: TranslationLanguage): StateFlow<List<TranslationRowState>> =
        rowFlows.getOrPut(language) {
            combine(
                settingsRepository.preferences,
                downloadingIds,
                downloadProgress,
                downloadErrors,
                refreshTrigger,
            ) { prefs, downloading, progress, errors, _ ->
                val activeId = if (language == TranslationLanguage.ENGLISH) prefs.activeTranslationEnglish else prefs.activeTranslationBengali
                TranslationCatalog.all.filter { it.language == language }.map { meta ->
                    TranslationRowState(
                        meta = meta,
                        isDownloaded = translationRepository.isDownloaded(meta),
                        isActive = meta.id == activeId,
                        isDownloading = meta.id in downloading,
                        downloadProgress = progress[meta.id],
                        errorMessage = errors[meta.id],
                    )
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        }

    fun select(meta: TranslationMeta) = viewModelScope.launch {
        settingsRepository.setActiveTranslation(meta.language, meta.id)
    }

    fun download(meta: TranslationMeta) = viewModelScope.launch {
        downloadingIds.update { it + meta.id }
        downloadErrors.update { it - meta.id }
        downloadProgress.update { it - meta.id }
        try {
            translationRepository.download(meta) { fraction ->
                downloadProgress.update { it + (meta.id to fraction) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            downloadErrors.update { it + (meta.id to (e.message ?: "Download failed")) }
        } finally {
            downloadingIds.update { it - meta.id }
            downloadProgress.update { it - meta.id }
            refreshTrigger.update { it + 1 }
        }
    }

    fun delete(meta: TranslationMeta) = viewModelScope.launch {
        // The UI never offers Delete for bundled translations, but guard here
        // too so a bundled fallback can never be wiped.
        if (meta.isBundled) return@launch
        translationRepository.delete(meta)
        refreshTrigger.update { it + 1 }
    }
}
