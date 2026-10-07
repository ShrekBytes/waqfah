package dev.shrekbytes.waqfah.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import dev.shrekbytes.waqfah.ui.bookmarks.BookmarksScreen
import dev.shrekbytes.waqfah.ui.components.WaqfahTab
import dev.shrekbytes.waqfah.ui.components.WaqfahTabBar
import dev.shrekbytes.waqfah.ui.home.HomeScreen
import dev.shrekbytes.waqfah.ui.reading.BookmarksViewModel
import dev.shrekbytes.waqfah.ui.settings.SettingsScreen
import dev.shrekbytes.waqfah.ui.theme.WaqfahTheme
import dev.shrekbytes.waqfah.ui.tour.FeatureTourOverlay
import dev.shrekbytes.waqfah.ui.tour.FeatureTourViewModel
import dev.shrekbytes.waqfah.ui.tour.TourHost
import dev.shrekbytes.waqfah.ui.tour.tourVisible

// Tabs that render the shared reading card — the pair that gets the parallax
// slide below. WaqfahTab's order is meaningful: Home=0, Bookmarks=1.
private val READING_TABS = setOf(WaqfahTab.HOME, WaqfahTab.BOOKMARKS)

// Travel distance for the Home <-> Bookmarks parallax slide, as a fraction of
// the content width. A third reads clearly as "the page moved" without turning
// the tab switch into a full pager slide.
private const val PARALLAX_FRACTION = 0.3f

// Tab switch motion is per-pair, not global, and it splits the job in two. The
// tab bar's travelling pill (WaqfahTabBar) carries "which tab you are on"; this
// carries "the page changed". Home <-> Bookmarks render the same card over a
// different source, so a crossfade of the two frames has nothing to hide — that
// pair gets a directional parallax slide, keyed to tab order (Home=0,
// Bookmarks=1) so the motion says which way the reader moved. The pill is what
// keeps it from reading as the ayah swipe: the slide is anchored by a chrome
// change happening at the same moment. Anything touching Settings keeps the
// soft fade+scale pop — Settings looks nothing like a reading card, so the
// subtle spec already reads there. The tab bar composes once, outside
// AnimatedContent.
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
    onGoToBookmarksList: () -> Unit = {},
    readingViewModel: dev.shrekbytes.waqfah.ui.reading.ReadingViewModel = hiltViewModel(),
    // The Bookmarks tab's own session (ADR-0005), separate from the shared
    // ReadingViewModel above so each tab keeps its own position. Hoisted by
    // WaqfahNavDisplay rather than built here, because the Bookmarks list screen
    // has to be handed this exact instance — a second one would retarget a card
    // the reader cannot see.
    bookmarksViewModel: BookmarksViewModel = hiltViewModel(),
    // The tour machine is the FeatureTourViewModel's session: it owns the
    // gate's flags (manual open, this-session dismissal) alongside the steps,
    // so the whole tour survives the navigation pushes that dispose this
    // screen mid-tour. Hoisted by WaqfahNavDisplay so FAQ's tour shares this
    // same session.
    tourViewModel: FeatureTourViewModel = hiltViewModel(),
) {
    val tourSession = tourViewModel.session
    val tourUi by tourSession.uiState.collectAsStateWithLifecycle()

    // rememberSaveable survives Navigation3 disposing/recomposing this screen
    // when a settings sub-screen is pushed over it — and also restores the tab
    // across activity recreation (e.g. a per-app locale switch).
    var selectedTab by rememberSaveable { mutableStateOf(initialTab) }

    val colors = WaqfahTheme.colors

    BackHandler(enabled = selectedTab != WaqfahTab.HOME) { selectedTab = WaqfahTab.HOME }

    Surface(modifier = Modifier.fillMaxSize(), color = colors.background, contentColor = colors.ink) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = selectedTab,
                    modifier = Modifier.weight(1f),
                    transitionSpec = {
                        if (initialState in READING_TABS && targetState in READING_TABS) {
                            // Home <-> Bookmarks: a directional parallax slide. The
                            // incoming card enters from the side of the destination
                            // tab and the outgoing card drifts the other way, so the
                            // motion — not the near-identical content — carries the
                            // change. Direction follows tab order: moving to the
                            // higher index (Home -> Bookmarks) travels leftward,
                            // matching the pill travelling right.
                            val forward = targetState.ordinal > initialState.ordinal
                            val enterFrom = if (forward) 1f else -1f
                            val exitTo = if (forward) -1f else 1f
                            (
                                slideInHorizontally(
                                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                                ) { (it * PARALLAX_FRACTION * enterFrom).toInt() } + fadeIn(tween(300))
                                ).togetherWith(
                                slideOutHorizontally(
                                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                                ) { (it * PARALLAX_FRACTION * exitTo).toInt() } + fadeOut(tween(300)),
                            )
                        } else {
                            (fadeIn(tween(220)) + scaleIn(initialScale = 0.97f, animationSpec = tween(220)))
                                .togetherWith(fadeOut(tween(140)) + scaleOut(targetScale = 1.03f, animationSpec = tween(140)))
                        }
                    },
                    label = "main_tab_content",
                ) { tab ->
                    when (tab) {
                        WaqfahTab.HOME -> HomeScreen(
                            onGoToAyah = onGoToSurah,
                            viewModel = readingViewModel,
                        )
                        WaqfahTab.BOOKMARKS -> BookmarksScreen(
                            viewModel = bookmarksViewModel,
                            onOpenList = onGoToBookmarksList,
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
                    onBookmarksClick = { selectedTab = WaqfahTab.BOOKMARKS },
                    onSettingsClick = { selectedTab = WaqfahTab.SETTINGS },
                )
            }

            // The auto-shown tour lives ONLY over the Home tab of
            // MainActivity; a manual open shows on FAQ instead (see
            // WaqfahNavDisplay). It can never appear over TriggerActivity's
            // over-other-apps interstitial, which hosts ReadingScreen directly
            // and never composes MainScreen. The rest of the rule (finished-once,
            // this-session dismissal) is TourSession's, composed by tourVisible.
            if (selectedTab == WaqfahTab.HOME && tourVisible(TourHost.HOME, tourUi)) {
                FeatureTourOverlay(
                    tourSession = tourSession,
                    viewModel = readingViewModel,
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
