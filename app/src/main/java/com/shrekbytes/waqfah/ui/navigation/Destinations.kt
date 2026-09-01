package com.shrekbytes.waqfah.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.shrekbytes.waqfah.ui.components.WaqfahTab
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

// @Serializable on the sealed interface itself (not just each destination) is
// what lets serializer() below discover every implementation automatically —
// this is Kotlin's closed/sealed polymorphism, no manual registration needed.
@Serializable
sealed interface WaqfahDestination : NavKey

@Serializable data object Welcome : WaqfahDestination
@Serializable data object OnboardReadingPrefs : WaqfahDestination
@Serializable data object OnboardChooseApps : WaqfahDestination
@Serializable data object OnboardPermissions : WaqfahDestination

// Home and Settings are tabs within this one destination (see MainScreen).
@Serializable data class Main(val initialTab: WaqfahTab = WaqfahTab.HOME) : WaqfahDestination

@Serializable data class ReadingDisplaySettings(val scrollToSection: String? = null) : WaqfahDestination {
    companion object {
        const val SECTION_TRANSLATION = "translation"
    }
}
// languageCode is TranslationLanguage.code ("en" / "bn").
@Serializable data class TranslationsSettings(val languageCode: String) : WaqfahDestination
@Serializable data object PermissionsSettings : WaqfahDestination
@Serializable data object AppsSettings : WaqfahDestination
@Serializable data object PermissionsRationale : WaqfahDestination

@Serializable data object GoToSurahList : WaqfahDestination
@Serializable data class GoToAyahOptions(val surahNo: Int) : WaqfahDestination

@Serializable data object About : WaqfahDestination
@Serializable data object PrivacyPolicy : WaqfahDestination
@Serializable data object Faq : WaqfahDestination
@Serializable data object Gratitude : WaqfahDestination
@Serializable data object Donate : WaqfahDestination

// rememberNavBackStack() alone only returns NavBackStack<NavKey>. This is the
// officially documented way to keep a back stack typed to an app's own sealed
// NavKey hierarchy instead ("Save and manage navigation state" > "Remember a
// back stack with subtypes of NavKey"), and it's what makes the stack survive
// rotation and process death: rememberSerializable finds WaqfahDestination's
// compiler-generated serializer, which already covers every @Serializable
// destination above — adding a new one above is all a new destination needs.
@Composable
fun rememberWaqfahNavBackStack(vararg elements: WaqfahDestination): NavBackStack<WaqfahDestination> {
    return rememberSerializable(serializer = serializer()) {
        NavBackStack(*elements)
    }
}
