package com.shrekbytes.waqfah

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shrekbytes.waqfah.data.repository.SettingsRepository
import com.shrekbytes.waqfah.ui.reading.ReadingScreen
import com.shrekbytes.waqfah.ui.theme.AccentColor
import com.shrekbytes.waqfah.ui.theme.AppTheme
import com.shrekbytes.waqfah.ui.theme.WaqfahTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

// Deliberately separate from MainActivity. This one is only ever launched by
// WaqfahAccessibilityService as a brief interstitial before a monitored app
// opens — android:excludeFromRecents="true" (manifest) keeps it out of the
// system app-switcher, which a shared MainActivity couldn't do without also
// hiding the app when the user opens it normally to browse or change
// settings. See ReadingScreen's BackHandler for why pressing back here goes
// to the home screen instead of just finishing this Activity: the target
// app's task is still alive, paused, right underneath — finishing without
// redirecting would reveal it, which looks like Waqfah "opened the app" on
// its own.
@AndroidEntryPoint
class TriggerActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val triggeredPackage = intent?.getStringExtra(EXTRA_TRIGGERED_PACKAGE)
        if (triggeredPackage == null) {
            finish()
            return
        }

        setContent {
            val prefs by settingsRepository.preferences.collectAsStateWithLifecycle(initialValue = null)
            WaqfahTheme(
                theme = prefs?.theme ?: AppTheme.SYSTEM,
                accentColor = prefs?.accentColor ?: AccentColor.SAGE,
            ) {
                ReadingScreen(triggeredPackage = triggeredPackage)
            }
        }
    }

    companion object {
        const val EXTRA_TRIGGERED_PACKAGE = "com.shrekbytes.waqfah.EXTRA_TRIGGERED_PACKAGE"
    }
}
