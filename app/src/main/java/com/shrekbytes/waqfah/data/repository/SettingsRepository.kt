package com.shrekbytes.waqfah.data.repository

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.shrekbytes.waqfah.data.local.prefs.SettingsKeys
import com.shrekbytes.waqfah.data.model.AidLanguage
import com.shrekbytes.waqfah.data.model.AppLanguage
import com.shrekbytes.waqfah.data.model.ArabicFont
import com.shrekbytes.waqfah.data.model.ArabicScript
import com.shrekbytes.waqfah.data.model.NameDisplayLanguage
import com.shrekbytes.waqfah.data.model.PreferenceLimits
import com.shrekbytes.waqfah.data.model.ReadingMode
import com.shrekbytes.waqfah.data.model.TranslationLanguage
import com.shrekbytes.waqfah.data.model.UserPreferences
import com.shrekbytes.waqfah.ui.theme.AccentColor
import com.shrekbytes.waqfah.ui.theme.AppTheme
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "waqfah_settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    scope: CoroutineScope,
) {
    // Await-then-act readers (the monitor supervisor, the toggle flip) use
    // this cold flow: first() waits for DataStore's first emission.
    val preferences: Flow<UserPreferences> = context.settingsDataStore.data.map { it.toUserPreferences() }

    // Observers read this instead: null until DataStore's first emission —
    // "not yet loaded" is part of the type, so no caller invents its own
    // sentinel or inherits one by coincidence. See loadedIn for the contract,
    // pinned once by LoadedPreferencesTest.
    val loadedPreferences: StateFlow<UserPreferences?> = preferences.loadedIn(scope)

    suspend fun setTheme(theme: AppTheme) = edit { it[SettingsKeys.THEME] = theme.name }
    suspend fun setAccentColor(accent: AccentColor) = edit { it[SettingsKeys.ACCENT_COLOR] = accent.name }
    suspend fun setAppLanguage(language: AppLanguage) = edit { it[SettingsKeys.APP_LANGUAGE] = language.name }
    suspend fun setReadingMode(mode: ReadingMode) = edit { it[SettingsKeys.READING_MODE] = mode.name }
    suspend fun setSurahNameLanguage(lang: NameDisplayLanguage) = edit { it[SettingsKeys.SURAH_NAME_LANG] = lang.name }
    suspend fun setArabicScript(script: ArabicScript) = edit { it[SettingsKeys.ARABIC_SCRIPT] = script.name }
    suspend fun setArabicFont(font: ArabicFont) = edit { it[SettingsKeys.ARABIC_FONT] = font.name }
    suspend fun setArabicFontSize(size: Int) = edit { it[SettingsKeys.ARABIC_FONT_SIZE] = size }
    suspend fun setPronunciation(lang: AidLanguage) = edit { it[SettingsKeys.PRONUNCIATION] = lang.name }
    suspend fun setTranslitFontSize(size: Int) = edit { it[SettingsKeys.TRANSLIT_FONT_SIZE] = size }
    suspend fun setTranslationDisplay(lang: AidLanguage) = edit { it[SettingsKeys.TRANSLATION_DISPLAY] = lang.name }
    suspend fun setTranslationFontSize(size: Int) = edit { it[SettingsKeys.TRANSLATION_FONT_SIZE] = size }
    suspend fun setCooldownMinutes(minutes: Int) = edit { it[SettingsKeys.COOLDOWN_MINUTES] = minutes.coerceIn(PreferenceLimits.COOLDOWN_MIN_MINUTES, PreferenceLimits.COOLDOWN_MAX_MINUTES) }
    suspend fun setAppActive(active: Boolean) = edit { it[SettingsKeys.APP_ACTIVE] = active }
    suspend fun setOnboardingComplete(complete: Boolean) = edit { it[SettingsKeys.ONBOARDING_COMPLETE] = complete }
    suspend fun setFeatureTourComplete(complete: Boolean) = edit { it[SettingsKeys.FEATURE_TOUR_COMPLETE] = complete }

    // APP_LANGUAGE is only a UI-facing mirror of the selection; applying it to
    // the system is AppCompatDelegate.setApplicationLocales' job (see
    // SettingsViewModel.setAppLanguage), which also persists it itself and
    // recreates the running activities — no manual recreate() needed.

    suspend fun setActiveTranslation(language: TranslationLanguage, id: String) = edit {
        val key = if (language == TranslationLanguage.ENGLISH) SettingsKeys.ACTIVE_TRANSLATION_EN else SettingsKeys.ACTIVE_TRANSLATION_BN
        it[key] = id
    }

    private suspend fun edit(transform: (MutablePreferences) -> Unit) {
        context.settingsDataStore.edit(transform)
    }
}

internal fun Preferences.toUserPreferences() = UserPreferences(
    theme = enumOrDefault(SettingsKeys.THEME, AppTheme.SYSTEM),
    accentColor = enumOrDefault(SettingsKeys.ACCENT_COLOR, AccentColor.SAGE),
    appLanguage = enumOrDefault(SettingsKeys.APP_LANGUAGE, AppLanguage.SYSTEM),
    readingMode = enumOrDefault(SettingsKeys.READING_MODE, ReadingMode.SEQUENTIAL),
    surahNameLanguage = enumOrDefault(SettingsKeys.SURAH_NAME_LANG, NameDisplayLanguage.ENGLISH),
    arabicScript = enumOrDefault(SettingsKeys.ARABIC_SCRIPT, ArabicScript.INDOPAK),
    arabicFont = enumOrDefault(SettingsKeys.ARABIC_FONT, ArabicFont.DIGITAL_KHATT_INDOPAK),
    arabicFontSize = this[SettingsKeys.ARABIC_FONT_SIZE] ?: 26,
    pronunciation = enumOrDefault(SettingsKeys.PRONUNCIATION, AidLanguage.ENGLISH),
    translitFontSize = this[SettingsKeys.TRANSLIT_FONT_SIZE] ?: 18,
    translationDisplay = enumOrDefault(SettingsKeys.TRANSLATION_DISPLAY, AidLanguage.ENGLISH),
    translationFontSize = this[SettingsKeys.TRANSLATION_FONT_SIZE] ?: 18,
    activeTranslationEnglish = this[SettingsKeys.ACTIVE_TRANSLATION_EN] ?: "sahih",
    activeTranslationBengali = this[SettingsKeys.ACTIVE_TRANSLATION_BN] ?: "taisirul",
    cooldownMinutes = this[SettingsKeys.COOLDOWN_MINUTES] ?: 30,
    appActive = this[SettingsKeys.APP_ACTIVE] ?: true,
    hasCompletedOnboarding = this[SettingsKeys.ONBOARDING_COMPLETE] ?: false,
    hasCompletedFeatureTour = this[SettingsKeys.FEATURE_TOUR_COMPLETE] ?: false,
)

// Falls back to the default instead of crashing when the stored string no
// longer matches any enum constant (e.g. after a rename).
internal inline fun <reified T : Enum<T>> Preferences.enumOrDefault(key: Preferences.Key<String>, default: T): T =
    this[key]?.let { stored -> enumValues<T>().firstOrNull { it.name == stored } } ?: default

// The typed "not yet loaded" seam: null before the upstream's first value,
// the value after, updates carried through. Eagerly, so the app's one shared
// answer stays warm whether or not anyone is watching this instant.
internal fun Flow<UserPreferences>.loadedIn(scope: CoroutineScope): StateFlow<UserPreferences?> =
    map { it as UserPreferences? }.stateIn(scope, SharingStarted.Eagerly, null)
