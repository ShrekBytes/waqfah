package com.shrekbytes.waqfah

import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shrekbytes.waqfah.data.repository.SettingsRepository
import com.shrekbytes.waqfah.detection.AppMonitorService
import com.shrekbytes.waqfah.ui.reading.ReadingScreen
import com.shrekbytes.waqfah.ui.theme.AccentColor
import com.shrekbytes.waqfah.ui.theme.AppTheme
import com.shrekbytes.waqfah.ui.theme.WaqfahTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

// Launched by AppMonitorService as a brief interstitial before a monitored app
// opens. Background activity starts are normally blocked on Android 10+ but are
// exempted for apps holding SYSTEM_ALERT_WINDOW. excludeFromRecents + empty
// taskAffinity + noHistory (manifest, mirrored by intent flags) keep each
// trigger a fresh, short-lived instance that never clutters the system
// app-switcher. Dismissing it — via back or the open-app button — simply
// finishes the activity, revealing whatever screen of the target app is really
// paused directly beneath (main UI, share sheet, file viewer), like a normal
// back press. See ReadingScreen.
// AppCompatActivity so AppCompatDelegate's per-app locales apply here.
@AndroidEntryPoint
class TriggerActivity : AppCompatActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    // Set after the single re-assertion below, so a stubborn app can't trap
    // the user in a loop of interstitials.
    private var buriedRetryUsed = false

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

    override fun onStop() {
        super.onStop()
        val triggeredPackage = intent?.getStringExtra(EXTRA_TRIGGERED_PACKAGE) ?: return
        if (buriedRetryUsed || isFinishing) return

        // Screen-off also stops us without changing the foreground — never
        // relaunch into a dark screen.
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        if (!powerManager.isInteractive) return

        // If we lost visibility but the triggered app is still what's in the
        // foreground, it raised its own task over this interstitial mid-launch
        // (splash chains, VPN consent dialogs — 1.1.1.1 does this), which looks
        // like Waqfah opened the app. Bring ourselves back once.
        if (!AppMonitorService.isLatestForeground(this, triggeredPackage)) return

        buriedRetryUsed = true
        Log.d(TAG, "Target app covered the interstitial; re-asserting")
        startActivity(
            Intent(this, TriggerActivity::class.java).apply {
                putExtra(EXTRA_TRIGGERED_PACKAGE, triggeredPackage)
                // CLEAR_TOP finishes the buried instance(s) of this activity in
                // the task instead of stacking another one on top, so repeated
                // self-raising apps can't pile up stale overlays.
                // EXCLUDE_FROM_RECENTS mirrors the manifest attribute — some
                // OEM recents screens only honor one or the other.
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
                )
            },
        )
    }

    companion object {
        private const val TAG = "TriggerActivity"
        const val EXTRA_TRIGGERED_PACKAGE = "com.shrekbytes.waqfah.EXTRA_TRIGGERED_PACKAGE"
    }
}
