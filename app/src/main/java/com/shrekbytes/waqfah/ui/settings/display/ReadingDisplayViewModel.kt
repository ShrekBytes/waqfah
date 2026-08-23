package com.shrekbytes.waqfah.ui.settings.display

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shrekbytes.waqfah.data.model.AidLanguage
import com.shrekbytes.waqfah.data.model.ArabicFont
import com.shrekbytes.waqfah.data.model.ArabicScript
import com.shrekbytes.waqfah.data.model.NameDisplayLanguage
import com.shrekbytes.waqfah.data.model.ReadingMode
import com.shrekbytes.waqfah.data.model.UserPreferences
import com.shrekbytes.waqfah.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReadingDisplayViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val prefs: StateFlow<UserPreferences> = settingsRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

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
    fun setArabicFontSize(size: Int) = viewModelScope.launch { settingsRepository.setArabicFontSize(size.coerceIn(11, 33)) }
    fun setPronunciation(lang: AidLanguage) = viewModelScope.launch { settingsRepository.setPronunciation(lang) }
    fun setTranslitFontSize(size: Int) = viewModelScope.launch { settingsRepository.setTranslitFontSize(size.coerceIn(11, 33)) }
    fun setTranslationDisplay(lang: AidLanguage) = viewModelScope.launch { settingsRepository.setTranslationDisplay(lang) }
    fun setTranslationFontSize(size: Int) = viewModelScope.launch { settingsRepository.setTranslationFontSize(size.coerceIn(11, 33)) }
}
