package com.shrekbytes.waqfah.ui.navigation

import com.shrekbytes.waqfah.ui.components.WaqfahTab
import kotlinx.serialization.Serializable

sealed interface WaqfahDestination

@Serializable data object Welcome : WaqfahDestination
@Serializable data object OnboardReadingPrefs : WaqfahDestination
@Serializable data object OnboardChooseApps : WaqfahDestination
@Serializable data object OnboardPermissions : WaqfahDestination

// Home and Settings are tabs within this one destination (see MainScreen).
@Serializable data class Main(val initialTab: WaqfahTab = WaqfahTab.HOME) : WaqfahDestination

@Serializable data object ReadingDisplaySettings : WaqfahDestination
// languageCode is TranslationLanguage.code ("en" / "bn").
@Serializable data class TranslationsSettings(val languageCode: String) : WaqfahDestination
@Serializable data object PermissionsSettings : WaqfahDestination
@Serializable data object AppsSettings : WaqfahDestination
