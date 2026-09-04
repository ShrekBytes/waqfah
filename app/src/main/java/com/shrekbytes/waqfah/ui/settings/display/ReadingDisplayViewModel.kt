package com.shrekbytes.waqfah.ui.settings.display

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shrekbytes.waqfah.data.model.AidLanguage
import com.shrekbytes.waqfah.data.model.ArabicFont
import com.shrekbytes.waqfah.data.model.ArabicScript
import com.shrekbytes.waqfah.data.model.NameDisplayLanguage
import com.shrekbytes.waqfah.data.model.ReadingMode
import com.shrekbytes.waqfah.data.model.UserPreferences
import com.shrekbytes.waqfah.data.model.PreferenceLimits
import com.shrekbytes.waqfah.data.repository.SettingsRepository
import com.shrekbytes.waqfah.data.repository.TranslationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReadingDisplayViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    translationRepository: TranslationRepository,
) : ViewModel() {

    // "Not yet loaded" renders as the persisted defaults, knowingly: null maps
    // to UserPreferences() here (and seeds this flow), so no frame flashes a
    // different value than the user's fallbacks would produce.
    val prefs: StateFlow<UserPreferences> = settingsRepository.loadedPreferences
        .map { it ?: UserPreferences() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    // Disk truth for resolving the stored translation ids to the active
    // translation names shown on this screen.
    val downloadedIds: StateFlow<Set<String>> = translationRepository.downloadedIds

    fun setReadingMode(mode: ReadingMode) = viewModelScope.launch { settingsRepository.setReadingMode(mode) }
    fun setSurahNameLanguage(lang: NameDisplayLanguage) = viewModelScope.launch { settingsRepository.setSurahNameLanguage(lang) }

    // The two scripts have disjoint font lists; a font picked for one draws the
    // wrong glyphs for the other, so switching script moves the selection onto
    // the new script's first font.
    fun setArabicScript(script: ArabicScript) = viewModelScope.launch {
        settingsRepository.setArabicScript(script)
        val currentFont = prefs.value.arabicFont
        if (currentFont.script != script) {
            settingsRepository.setArabicFont(ArabicFont.entries.first { it.script == script })
        }
    }

    fun setArabicFont(font: ArabicFont) = viewModelScope.launch { settingsRepository.setArabicFont(font) }
    fun setArabicFontSize(size: Int) = viewModelScope.launch { settingsRepository.setArabicFontSize(size.coerceIn(PreferenceLimits.FONT_SIZE_MIN, PreferenceLimits.FONT_SIZE_MAX)) }
    fun setPronunciation(lang: AidLanguage) = viewModelScope.launch { settingsRepository.setPronunciation(lang) }
    fun setTranslitFontSize(size: Int) = viewModelScope.launch { settingsRepository.setTranslitFontSize(size.coerceIn(PreferenceLimits.FONT_SIZE_MIN, PreferenceLimits.FONT_SIZE_MAX)) }
    fun setTranslationDisplay(lang: AidLanguage) = viewModelScope.launch { settingsRepository.setTranslationDisplay(lang) }
    fun setTranslationFontSize(size: Int) = viewModelScope.launch { settingsRepository.setTranslationFontSize(size.coerceIn(PreferenceLimits.FONT_SIZE_MIN, PreferenceLimits.FONT_SIZE_MAX)) }
}
