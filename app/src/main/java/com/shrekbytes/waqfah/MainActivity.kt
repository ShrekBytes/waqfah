package com.shrekbytes.waqfah

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shrekbytes.waqfah.data.repository.SettingsRepository
import com.shrekbytes.waqfah.ui.navigation.Main
import com.shrekbytes.waqfah.ui.navigation.WaqfahNavDisplay
import com.shrekbytes.waqfah.ui.navigation.Welcome
import com.shrekbytes.waqfah.ui.theme.AccentColor
import com.shrekbytes.waqfah.ui.theme.AppTheme
import com.shrekbytes.waqfah.ui.theme.WaqfahTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

// Handles normal app usage only (onboarding, Home, Settings) — the
// accessibility-triggered reading screen is TriggerActivity, a separate
// Activity on purpose. See its doc comment for why.
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val prefs by settingsRepository.preferences.collectAsStateWithLifecycle(initialValue = null)
            val hasCompletedOnboarding = prefs?.hasCompletedOnboarding

            WaqfahTheme(
                theme = prefs?.theme ?: AppTheme.SYSTEM,
                accentColor = prefs?.accentColor ?: AccentColor.SAGE,
            ) {
                if (hasCompletedOnboarding == null) {
                    Unit // one blank frame while DataStore's first value loads
                } else {
                    // Decided exactly once, from whichever value the flag had
                    // on this first real composition, then never
                    // recalculated. WaqfahNavDisplay owns its own back stack
                    // from here on — onboarding finishing pushes
                    // Main(SETTINGS) onto it directly (see
                    // WaqfahNavDisplay's OnboardPermissions.onComplete).
                    //
                    // This used to branch live on hasCompletedOnboarding
                    // instead (a `when` with a separate WaqfahNavDisplay call
                    // per branch), which was the actual bug behind "onboarding
                    // still takes you to Home": the moment onboarding set the
                    // flag to true, Compose tore down the false-branch's
                    // WaqfahNavDisplay(Welcome) — back stack, freshly-pushed
                    // Main(SETTINGS), and all — and mounted a brand new
                    // true-branch WaqfahNavDisplay(Main()) with a fresh,
                    // default-Home back stack, discarding the push before it
                    // was ever seen. Locking the destination in with
                    // `remember` keeps this the same composable instance for
                    // the rest of the session, so that push actually sticks.
                    val startDestination = remember { if (hasCompletedOnboarding) Main() else Welcome }
                    WaqfahNavDisplay(startDestination = startDestination)
                }
            }
        }
    }
}
