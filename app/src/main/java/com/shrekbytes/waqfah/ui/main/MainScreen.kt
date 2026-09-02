package com.shrekbytes.waqfah.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.Surface
import com.shrekbytes.waqfah.ui.components.WaqfahTab
import com.shrekbytes.waqfah.ui.components.WaqfahTabBar
import com.shrekbytes.waqfah.ui.home.HomeScreen
import com.shrekbytes.waqfah.ui.settings.SettingsScreen
import com.shrekbytes.waqfah.ui.theme.WaqfahTheme
import com.shrekbytes.waqfah.ui.tour.FeatureTourOverlay
import com.shrekbytes.waqfah.ui.tour.FeatureTourViewModel

// Tab switch is a soft fade+scale pop in place — not a slide, which would read
// as drill-down navigation. The tab bar composes once, outside AnimatedContent.
@Composable
fun MainScreen(
    initialTab: WaqfahTab,
    onOpenReadingDisplay: () -> Unit,
    onOpenTranslationSection: () -> Unit = onOpenReadingDisplay,
    onOpenApps: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenFaq: () -> Unit,
    onOpenDonate: () -> Unit,
    onGoToSurah: () -> Unit = {},
    readingViewModel: com.shrekbytes.waqfah.ui.reading.ReadingViewModel = hiltViewModel(),
) {
    val tourViewModel: FeatureTourViewModel = hiltViewModel()
    // null until DataStore resolves — the auto-show waits instead of flashing.
    val hasCompletedTour by tourViewModel.hasCompletedTour.collectAsStateWithLifecycle()

    // rememberSaveable survives Navigation3 disposing/recomposing this screen
    // when a settings sub-screen is pushed over it — and also restores the tab
    // across activity recreation (e.g. a per-app locale switch).
    var selectedTab by rememberSaveable { mutableStateOf(initialTab) }

    // Manual launch from Home's "Start tour" button, and the session-local
    // dismissal flag: a skipped tour must stay quiet while switching tabs in
    // this session, yet re-offer on every future launch until finished —
    // which is exactly what NOT persisting anything on skip achieves.
    var tourOpenedManually by rememberSaveable { mutableStateOf(false) }
    var tourDismissedThisSession by rememberSaveable { mutableStateOf(false) }

    val colors = WaqfahTheme.colors

    BackHandler(enabled = selectedTab != WaqfahTab.HOME) { selectedTab = WaqfahTab.HOME }

    Surface(modifier = Modifier.fillMaxSize(), color = colors.background, contentColor = colors.ink) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = selectedTab,
                    modifier = Modifier.weight(1f),
                    transitionSpec = {
                        (fadeIn(tween(220)) + scaleIn(initialScale = 0.97f, animationSpec = tween(220)))
                            .togetherWith(fadeOut(tween(140)) + scaleOut(targetScale = 1.03f, animationSpec = tween(140)))
                    },
                    label = "main_tab_content",
                ) { tab ->
                    when (tab) {
                        WaqfahTab.HOME -> HomeScreen(
                            onStartTour = { tourOpenedManually = true },
                            onGoToAyah = onGoToSurah,
                            viewModel = readingViewModel,
                        )
                        WaqfahTab.SETTINGS -> SettingsScreen(
                            onOpenReadingDisplay = onOpenReadingDisplay,
                            onOpenApps = onOpenApps,
                            onOpenPermissions = onOpenPermissions,
                            onOpenAbout = onOpenAbout,
                            onOpenFaq = onOpenFaq,
                            onOpenDonate = onOpenDonate,
                        )
                    }
                }
                WaqfahTabBar(
                    selected = selectedTab,
                    onHomeClick = { selectedTab = WaqfahTab.HOME },
                    onSettingsClick = { selectedTab = WaqfahTab.SETTINGS },
                )
            }

            // The feature tour lives ONLY over the Home tab of MainActivity. It can
            // never appear over TriggerActivity's over-other-apps interstitial,
            // which hosts ReadingScreen directly and never composes MainScreen.
            val tourVisible = selectedTab == WaqfahTab.HOME &&
                (tourOpenedManually || (hasCompletedTour == false && !tourDismissedThisSession))
            if (tourVisible) {
                FeatureTourOverlay(
                    viewModel = readingViewModel,
                    onFinish = {
                        // Persisted: never auto-shows again after finishing once.
                        tourViewModel.completeTour()
                        tourOpenedManually = false
                        tourDismissedThisSession = true
                    },
                    onSkip = {
                        // Nothing persisted: re-offered next launch.
                        tourOpenedManually = false
                        tourDismissedThisSession = true
                    },
                    onBrowseTranslations = {
                        // Deep-link to Reading & display#translation (like a web hash fragment):
                        // scrolls straight to the Translation section with a soft highlight.
                        // Keeps the tour active so returning brings the user back to step 4.
                        onOpenTranslationSection()
                    },
                )
            }
        }
    }
}
