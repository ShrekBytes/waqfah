package com.shrekbytes.waqfah.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.shrekbytes.waqfah.ui.components.WaqfahTab
import com.shrekbytes.waqfah.ui.components.WaqfahTabBar
import com.shrekbytes.waqfah.ui.home.HomeScreen
import com.shrekbytes.waqfah.ui.settings.SettingsScreen

// The prototype switches Home/Settings with a plain CSS display:none/block
// swap — no motion at all, and the tab bar itself never moves since it's
// outside the swapped element entirely. The tab bar is still composed
// exactly once, here, and only the content above it changes when the
// selected tab changes — but a plain opacity crossfade read as flat/inert
// next to the rest of the app's livelier motion, so this pairs the fade with
// a very small scale (new content settles in from 97%, old content eases out
// to 103%) for a soft "pop" rather than a dissolve. It's still an in-place
// swap, not a slide — sliding would read as real drill-down navigation,
// which tab switches deliberately aren't (see WaqfahNavDisplay).
@Composable
fun MainScreen(
    initialTab: WaqfahTab,
    onOpenReadingDisplay: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenPermissions: () -> Unit,
) {
    // rememberSaveable, not remember: pushing a settings sub-screen (Apps,
    // Permissions, ...) covers this composable, and Navigation3 disposes and
    // later recomposes it fresh when the sub-screen is popped back off.
    // Plain `remember` doesn't survive that — it was silently resetting to
    // initialTab (Home) every time, which is why back from a sub-screen was
    // landing on Home instead of the Settings tab you'd actually come from.
    var selectedTab by rememberSaveable { mutableStateOf(initialTab) }

    // Standard bottom-nav back behavior: from a non-Home tab, back returns to
    // Home first rather than immediately exiting the app. From Home, this
    // handler is disabled so back falls through to the real backstack (which,
    // with nothing else on it, exits — the expected root-screen behavior).
    BackHandler(enabled = selectedTab != WaqfahTab.HOME) { selectedTab = WaqfahTab.HOME }

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
                WaqfahTab.HOME -> HomeScreen()
                WaqfahTab.SETTINGS -> SettingsScreen(
                    onOpenReadingDisplay = onOpenReadingDisplay,
                    onOpenApps = onOpenApps,
                    onOpenPermissions = onOpenPermissions,
                )
            }
        }
        WaqfahTabBar(
            selected = selectedTab,
            onHomeClick = { selectedTab = WaqfahTab.HOME },
            onSettingsClick = { selectedTab = WaqfahTab.SETTINGS },
        )
    }
}
