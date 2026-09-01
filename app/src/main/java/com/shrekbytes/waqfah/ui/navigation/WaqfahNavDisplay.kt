package com.shrekbytes.waqfah.ui.navigation

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.hilt.navigation.compose.hiltViewModel
import com.shrekbytes.waqfah.ui.components.WaqfahTab
import com.shrekbytes.waqfah.ui.about.AboutScreen
import com.shrekbytes.waqfah.ui.about.DonateScreen
import com.shrekbytes.waqfah.ui.about.FaqScreen
import com.shrekbytes.waqfah.ui.about.GratitudeScreen
import com.shrekbytes.waqfah.ui.about.PrivacyPolicyScreen
import com.shrekbytes.waqfah.ui.ayahpicker.GoToSurahScreen
import com.shrekbytes.waqfah.ui.main.MainScreen
import com.shrekbytes.waqfah.ui.reading.ReadingViewModel
import com.shrekbytes.waqfah.ui.onboarding.OnboardChooseAppsScreen
import com.shrekbytes.waqfah.ui.onboarding.OnboardPermissionsScreen
import com.shrekbytes.waqfah.ui.onboarding.OnboardReadingPrefsScreen
import com.shrekbytes.waqfah.ui.onboarding.OnboardWelcomeScreen
import com.shrekbytes.waqfah.ui.settings.apps.AppsScreen
import com.shrekbytes.waqfah.ui.settings.display.ReadingDisplayScreen
import com.shrekbytes.waqfah.ui.settings.permissions.PermissionsRationaleScreen
import com.shrekbytes.waqfah.ui.settings.permissions.PermissionsScreen
import com.shrekbytes.waqfah.ui.settings.translations.TranslationsScreen

@Composable
fun WaqfahNavDisplay(startDestination: WaqfahDestination) {
    // rememberWaqfahNavBackStack (rather than remember { mutableStateListOf(...) })
    // saves and restores the whole stack across rotation and process death —
    // without it, either would silently drop the user back to startDestination
    // no matter how deep into Settings they'd navigated. See Destinations.kt.
    val backStack = rememberWaqfahNavBackStack(startDestination)
    // Home-only shared ReadingViewModel: hoisted to Activity scope so Home +
    // GoTo screens (both inside MainActivity) see the same currentVerse.
    // TriggerActivity keeps its own separate instance via its own Activity.
    val sharedReadingViewModel: ReadingViewModel = hiltViewModel()

    // Guards against rapid double-taps pushing the same destination twice —
    // the second tap would otherwise stack an identical screen that only
    // reveals itself as an extra back press. Data objects/classes compare by
    // value, so Main(HOME) and Main(SETTINGS) stay distinct.
    fun push(destination: WaqfahDestination) {
        if (backStack.lastOrNull() != destination) backStack.add(destination)
    }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        transitionSpec = {
            slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
        },
        popTransitionSpec = {
            slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
        },
        predictivePopTransitionSpec = {
            slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
        },
        entryProvider = entryProvider {
            entry<Welcome> {
                OnboardWelcomeScreen(onGetStarted = { push(OnboardReadingPrefs) })
            }
            entry<OnboardReadingPrefs> {
                OnboardReadingPrefsScreen(
                    onBack = { backStack.removeLastOrNull() },
                    onContinue = { push(OnboardChooseApps) },
                )
            }
            entry<OnboardChooseApps> {
                OnboardChooseAppsScreen(
                    onBack = { backStack.removeLastOrNull() },
                    onContinue = { push(OnboardPermissions) },
                )
            }
            entry<OnboardPermissions> {
                OnboardPermissionsScreen(
                    onOpenRationale = { push(PermissionsRationale) },
                    onBack = { backStack.removeLastOrNull() },
                    onComplete = {
                        // First-run only: land on the Settings tab after onboarding.
                        backStack.clear()
                        backStack.add(Main(initialTab = WaqfahTab.SETTINGS))
                    },
                )
            }
            entry<Main> { key ->
                MainScreen(
                    initialTab = key.initialTab,
                    onOpenReadingDisplay = { push(ReadingDisplaySettings()) },
                    onOpenTranslationSection = {
                        push(ReadingDisplaySettings(scrollToSection = ReadingDisplaySettings.SECTION_TRANSLATION))
                    },
                    onOpenApps = { push(AppsSettings) },
                    onOpenPermissions = { push(PermissionsSettings) },
                    onOpenAbout = { push(About) },
                    onOpenFaq = { push(Faq) },
                    onOpenDonate = { push(Donate) },
                    onGoToSurah = { push(GoToSurahList) },
                    readingViewModel = sharedReadingViewModel,
                )
            }
            entry<GoToSurahList> {
                GoToSurahScreen(
                    readingViewModel = sharedReadingViewModel,
                    onBack = { backStack.removeLastOrNull() },
                    onJumped = { backStack.removeLastOrNull() },
                )
            }
            entry<ReadingDisplaySettings> { key ->
                ReadingDisplayScreen(
                    scrollToSection = key.scrollToSection,
                    onOpenTranslations = { language -> push(TranslationsSettings(language.code)) },
                    onBack = { backStack.removeLastOrNull() },
                )
            }
            entry<TranslationsSettings> { key ->
                TranslationsScreen(languageCode = key.languageCode, onBack = { backStack.removeLastOrNull() })
            }
            entry<PermissionsSettings> {
                PermissionsScreen(
                    onOpenRationale = { push(PermissionsRationale) },
                    onBack = { backStack.removeLastOrNull() },
                )
            }
            entry<PermissionsRationale> { PermissionsRationaleScreen(onBack = { backStack.removeLastOrNull() }) }
            entry<AppsSettings> { AppsScreen(onBack = { backStack.removeLastOrNull() }) }
            entry<About> {
                AboutScreen(
                    onOpenPrivacyPolicy = { push(PrivacyPolicy) },
                    onOpenGratitude = { push(Gratitude) },
                    onOpenDonate = { push(Donate) },
                    onBack = { backStack.removeLastOrNull() },
                )
            }
            entry<PrivacyPolicy> { PrivacyPolicyScreen(onBack = { backStack.removeLastOrNull() }) }
            entry<Faq> { FaqScreen(onBack = { backStack.removeLastOrNull() }) }
            entry<Gratitude> { GratitudeScreen(onBack = { backStack.removeLastOrNull() }) }
            entry<Donate> { DonateScreen(onBack = { backStack.removeLastOrNull() }) }
        },
    )
}
