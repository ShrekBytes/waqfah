package com.shrekbytes.waqfah

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.shrekbytes.waqfah.data.repository.SettingsRepository
import com.shrekbytes.waqfah.detection.MonitorSupervisor
import com.shrekbytes.waqfah.ui.navigation.Main
import com.shrekbytes.waqfah.ui.navigation.WaqfahNavDisplay
import com.shrekbytes.waqfah.ui.navigation.Welcome
import com.shrekbytes.waqfah.ui.theme.AccentColor
import com.shrekbytes.waqfah.ui.theme.AppTheme
import com.shrekbytes.waqfah.ui.theme.WaqfahTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

// Handles normal app usage (onboarding, Home, Settings). The triggered reading
// screen is TriggerActivity — a separate Activity on purpose, see its doc.
// AppCompatActivity so AppCompatDelegate's per-app locales apply here.
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var supervisor: MonitorSupervisor

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate(): on Android 12+ it hands control to
        // the system SplashScreen (started from Theme.Waqfah.Starting), and on
        // older versions it draws the compat splash and swaps in
        // postSplashScreenTheme. No separate SplashActivity involved.
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Hold the splash until DataStore's first value resolves, so the first
        // visible frame is already painted in the user's chosen theme instead
        // of a blank unthemed flash between the splash and real content.
        var contentReady = false
        splashScreen.setKeepOnScreenCondition { !contentReady }

        setContent {
            val prefs by settingsRepository.preferences.collectAsStateWithLifecycle(initialValue = null)
            val hasCompletedOnboarding = prefs?.hasCompletedOnboarding

            SideEffect { contentReady = hasCompletedOnboarding != null }

            WaqfahTheme(
                theme = prefs?.theme ?: AppTheme.SYSTEM,
                accentColor = prefs?.accentColor ?: AccentColor.SAGE,
            ) {
                if (hasCompletedOnboarding == null) {
                    Unit // blank frame while DataStore's first value loads
                } else {
                    // Locked in once on the first real composition so the back
                    // stack built here survives onboarding flipping the flag —
                    // branching live on it would tear down and remount the nav
                    // display mid-flow, discarding the post-onboarding push.
                    val startDestination = remember {
                        if (hasCompletedOnboarding) Main() else Welcome
                    }
                    WaqfahNavDisplay(startDestination = startDestination)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-checked on every resume: the permissions may have just been
        // granted in system settings, and starting an already-running service
        // is a no-op. The supervisor owns the may-run rule; resume may only
        // start, so it can never resurrect a monitor the user toggled off.
        lifecycleScope.launch {
            supervisor.sync(MonitorSupervisor.Reason.APP_RESUME)
        }
    }
}
