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
    // In-memory only for now — surviving process death needs a Saver built on
    // kotlinx.serialization; add that once the data layer has something worth
    // restoring beyond what's already persisted in Room/DataStore.
    val backStack = remember { mutableStateListOf<WaqfahDestination>(startDestination) }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        // Forward: new screen slides in from the right, old one slides out left.
        // Back: reverse of that. The default is a crossfade, which read as
        // "screens randomly swapping" rather than a clear forward/back sense
        // of place. This only applies to genuine drill-in/back navigation
        // (Settings -> Apps/Permissions/etc, onboarding steps) — switching
        // between the Home and Settings tabs no longer goes through this at
        // all, see MainScreen.
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
                        // First-run only: land on the Settings tab right after
                        // onboarding. Home and Settings are tabs of the same
                        // screen now, not separate backstack entries, so
                        // there's nothing to keep "underneath" — pressing back
                        // from here returns to the Home tab first (see
                        // MainScreen's BackHandler) before exiting.
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
