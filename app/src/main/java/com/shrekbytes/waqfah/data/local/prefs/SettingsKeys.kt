package com.shrekbytes.waqfah.data.local.prefs

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object SettingsKeys {
    val THEME = stringPreferencesKey("theme")
    val ACCENT_COLOR = stringPreferencesKey("accent_color")
    val READING_MODE = stringPreferencesKey("reading_mode")
    val SURAH_NAME_LANG = stringPreferencesKey("surah_name_lang")
    val ARABIC_SCRIPT = stringPreferencesKey("arabic_script")
    val ARABIC_FONT = stringPreferencesKey("arabic_font")
    val ARABIC_FONT_SIZE = intPreferencesKey("arabic_font_size")
    val PRONUNCIATION = stringPreferencesKey("pronunciation")
    val TRANSLIT_FONT_SIZE = intPreferencesKey("translit_font_size")
    val TRANSLATION_DISPLAY = stringPreferencesKey("translation_display")
    val TRANSLATION_FONT_SIZE = intPreferencesKey("translation_font_size")
    val ACTIVE_TRANSLATION_EN = stringPreferencesKey("active_translation_en")
    val ACTIVE_TRANSLATION_BN = stringPreferencesKey("active_translation_bn")
    val COOLDOWN_MINUTES = intPreferencesKey("cooldown_minutes")
    val APP_ACTIVE = booleanPreferencesKey("app_active")
    val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
    val LAST_VIEWED_VERSE_ID = intPreferencesKey("last_viewed_verse_id")
}
