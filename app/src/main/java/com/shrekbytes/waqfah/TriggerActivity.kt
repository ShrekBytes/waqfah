package com.shrekbytes.waqfah

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shrekbytes.waqfah.data.repository.SettingsRepository
import com.shrekbytes.waqfah.detection.AppMonitorService
import com.shrekbytes.waqfah.ui.reading.ReadingScreen
import com.shrekbytes.waqfah.ui.theme.AccentColor
import com.shrekbytes.waqfah.ui.theme.AppTheme
import com.shrekbytes.waqfah.ui.theme.WaqfahTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

// Launched by AppMonitorService as a brief interstitial before a monitored app
// opens. Background activity starts are normally blocked on Android 10+ but are
// exempted for apps holding SYSTEM_ALERT_WINDOW. excludeFromRecents + empty
// taskAffinity + noHistory (manifest, mirrored by intent flags) keep each
// trigger a fresh, short-lived instance that never clutters the system
// app-switcher.
//
// The window is translucent (see Theme.Waqfah.Trigger) with every window-level
// transition zeroed out — the interstitial fades itself in and out in Compose,
// crossfading over the target app beneath. Doing it in Compose keeps the effect
// identical on every OEM and immune to predictive-back/system animations.
// Dismissing it — via back or the open-app button — fades out, then finishes,
// revealing whatever screen of the target app is really paused directly
// beneath (main UI, share sheet, file viewer), like a normal back press.
// See ReadingScreen.
// AppCompatActivity so AppCompatDelegate's per-app locales apply here.
@AndroidEntryPoint
class TriggerActivity : AppCompatActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    // Set once dismissal starts, so repeated dismiss requests can't restart the
    // exit fade or schedule multiple finishes.
    private var closing = false

    // Set after the single re-assertion below, so a stubborn app can't trap
    // the user in a loop of interstitials.
    private var buriedRetryUsed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Zero every window-level transition on all supported APIs: the visual
        // entrance/exit lives entirely in Compose below.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        val triggeredPackage = intent?.getStringExtra(EXTRA_TRIGGERED_PACKAGE)
        if (triggeredPackage == null) {
            finish()
            return
        }

        setContent {
            var shown by remember { mutableStateOf(false) }
            var closingState by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()

            // Entrance: crossfade over the app while settling down from a slight
            // overscale — a calm "lands on the screen" feel instead of a pop.
            // Exit: quicker fade that lifts away again (the same scale term
            // reverses, so the card visibly recedes as it dissolves).
            val fade = animateFloatAsState(
                targetValue = if (closingState) 0f else if (shown) 1f else 0f,
                animationSpec = tween(
                    durationMillis = if (closingState) EXIT_FADE_MS else ENTER_FADE_MS,
                    easing = if (closingState) FastOutLinearInEasing else LinearOutSlowInEasing,
                ),
                label = "trigger_fade",
                finishedListener = { if (closingState && it == 0f) finish() },
            )
            LaunchedEffect(Unit) { shown = true }

            fun beginClose() {
                if (!closing) {
                    closing = true
                    closingState = true
                    // Safety net: the finishedListener above drives the real
                    // finish; this guarantees it even if animation is disabled
                    // (accessibility "remove animations", animator scale 0).
                    scope.launch {
                        delay(EXIT_FADE_MS + 100L)
                        finish()
                    }
                }
            }

            val prefs by settingsRepository.preferences.collectAsStateWithLifecycle(initialValue = null)
            WaqfahTheme(
                theme = prefs?.theme ?: AppTheme.SYSTEM,
                accentColor = prefs?.accentColor ?: AccentColor.SAGE,
            ) {
                // Reading fade/scale inside the graphicsLayer lambda keeps every
                // frame a pure redraw+re-layer — no recomposition per frame.
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val progress = fade.value
                            alpha = progress
                            val scale = 1f + SETTLE_OVERSCALE * (1f - progress)
                            scaleX = scale
                            scaleY = scale
                        },
                ) {
                    ReadingScreen(
                        triggeredPackage = triggeredPackage,
                        onDismissRequest = ::beginClose,
                    )
                }
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
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION,
                )
            },
        )
    }

    // Fade-out is drawn by Compose; strip any residual window transition here
    // so nothing else animates on top of it (pre-34 path; 34+ was zeroed in
    // onCreate via the CLOSE override).
    override fun finish() {
        super.finish()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    companion object {
        private const val TAG = "TriggerActivity"
        const val EXTRA_TRIGGERED_PACKAGE = "com.shrekbytes.waqfah.EXTRA_TRIGGERED_PACKAGE"
        private const val ENTER_FADE_MS = 360
        private const val EXIT_FADE_MS = 140

        // Entrance settles from this much larger down to rest (4% ≈ subtle
        // "lands on the screen"; anything past ~6% starts reading as a zoom).
        private const val SETTLE_OVERSCALE = 0.04f
    }
}
