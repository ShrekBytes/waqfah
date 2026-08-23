package com.shrekbytes.waqfah.ui.navigation

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.shrekbytes.waqfah.ui.components.WaqfahTab
import com.shrekbytes.waqfah.ui.main.MainScreen
import com.shrekbytes.waqfah.ui.onboarding.OnboardChooseAppsScreen
import com.shrekbytes.waqfah.ui.onboarding.OnboardPermissionsScreen
import com.shrekbytes.waqfah.ui.onboarding.OnboardReadingPrefsScreen
import com.shrekbytes.waqfah.ui.onboarding.OnboardWelcomeScreen
import com.shrekbytes.waqfah.ui.settings.apps.AppsScreen
import com.shrekbytes.waqfah.ui.settings.display.ReadingDisplayScreen
import com.shrekbytes.waqfah.ui.settings.permissions.PermissionsScreen
import com.shrekbytes.waqfah.ui.settings.translations.TranslationsScreen

@Composable
fun WaqfahNavDisplay(startDestination: WaqfahDestination) {
    val backStack = remember { mutableStateListOf<WaqfahDestination>(startDestination) }

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
                OnboardWelcomeScreen(onGetStarted = { backStack.add(OnboardReadingPrefs) })
            }
            entry<OnboardReadingPrefs> {
                OnboardReadingPrefsScreen(
                    onBack = { backStack.removeLastOrNull() },
                    onContinue = { backStack.add(OnboardChooseApps) },
                )
            }
            entry<OnboardChooseApps> {
                OnboardChooseAppsScreen(
                    onBack = { backStack.removeLastOrNull() },
                    onContinue = { backStack.add(OnboardPermissions) },
                )
            }
            entry<OnboardPermissions> {
                OnboardPermissionsScreen(
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
                    onOpenReadingDisplay = { backStack.add(ReadingDisplaySettings) },
                    onOpenApps = { backStack.add(AppsSettings) },
                    onOpenPermissions = { backStack.add(PermissionsSettings) },
                )
            }
            entry<ReadingDisplaySettings> {
                ReadingDisplayScreen(
                    onOpenTranslations = { language -> backStack.add(TranslationsSettings(language.code)) },
                    onBack = { backStack.removeLastOrNull() },
                )
            }
            entry<TranslationsSettings> { key ->
                TranslationsScreen(languageCode = key.languageCode, onBack = { backStack.removeLastOrNull() })
            }
            entry<PermissionsSettings> { PermissionsScreen(onBack = { backStack.removeLastOrNull() }) }
            entry<AppsSettings> { AppsScreen(onBack = { backStack.removeLastOrNull() }) }
        },
    )
}
