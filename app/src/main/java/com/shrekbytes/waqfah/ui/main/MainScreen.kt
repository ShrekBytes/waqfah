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

// Tab switch is a soft fade+scale pop in place — not a slide, which would read
// as drill-down navigation. The tab bar composes once, outside AnimatedContent.
@Composable
fun MainScreen(
    initialTab: WaqfahTab,
    onOpenReadingDisplay: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenPermissions: () -> Unit,
) {
    // rememberSaveable survives Navigation3 disposing/recomposing this screen
    // when a settings sub-screen is pushed over it; plain remember resets.
    var selectedTab by rememberSaveable { mutableStateOf(initialTab) }

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
