package com.shrekbytes.waqfah.detection

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.shrekbytes.waqfah.R
import com.shrekbytes.waqfah.TriggerActivity
import com.shrekbytes.waqfah.data.local.appstate.MonitoredAppEntity
import com.shrekbytes.waqfah.data.repository.MonitoredAppsRepository
import com.shrekbytes.waqfah.data.repository.PermissionsRepository
import com.shrekbytes.waqfah.data.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

// Watches foreground app changes by polling UsageStatsManager and launches
// TriggerActivity when a monitored app comes to the front. Runs as a specialUse
// foreground service; polling keeps battery cost proportional to how long
// Waqfah runs, not to how often the device switches apps.
//
// The trigger rules themselves — what counts as a fresh open, and what never
// earns a pause — live in TriggerDecision, which owns all rule state. This
// service is its Android adapter: a poller that feeds it resumed activities
// and an actor that launches the interstitial on a Trigger verdict.
@AndroidEntryPoint
class AppMonitorService : Service() {

    @Inject lateinit var monitoredAppsRepository: MonitoredAppsRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var permissionsRepository: PermissionsRepository

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(
        serviceJob + Dispatchers.Default + CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "Unhandled error in app monitor coroutine", throwable)
        },
    )

    // Latest monitored-apps snapshot, kept fresh by monitorGate's combine.
    // The trigger decision reads it through the isMonitored wiring below
    // instead of re-querying Room once per foreground change.
    private var monitoredSnapshot: List<MonitoredAppEntity> = emptyList()

    // Lazily-built cache of each package's alternate entry-point activities
    // (share targets, file/link viewers). Queried once per package on first
    // encounter; the monitored set is small so no eviction is needed.
    private val indirectEntryClassCache = HashMap<String, Set<String>>()

    // No permission needed to read; MODE_IN_CALL/MODE_IN_COMMUNICATION/
    // MODE_RINGTONE is the general-purpose signal that catches calls from
    // apps the call-UI package/class heuristic doesn't know about.
    private val audioManager: AudioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }

    // The trigger decision and all of its rule state. The impure edges are
    // wired here: audio routing, the cached PackageManager probe, the
    // preference snapshot (read late, after the cheap rules have run), the
    // persisted trigger stamp, and the two clocks.
    private val triggerDecision by lazy {
        TriggerDecision(
            isMonitored = { pkg -> monitoredSnapshot.any { it.packageName == pkg } },
            callAudioActive = { audioManager.mode in CALL_AUDIO_MODES },
            indirectEntryClasses = { indirectEntryClasses(it) },
            prefs = {
                settingsRepository.preferences.first().let { TriggerPrefs(it.appActive, it.cooldownMinutes) }
            },
            triggerStamp = { monitoredAppsRepository.getTriggerStamp(it) },
            stampShown = { monitoredAppsRepository.recordShown(it) },
            nowElapsed = SystemClock::elapsedRealtime,
            nowWall = System::currentTimeMillis,
        )
    }

    // Polling pauses whenever detection is impossible or pointless — screen
    // off, app paused via Settings, or no monitored apps selected. The loop
    // suspends on this combined gate instead of waking up each interval, so
    // idle time costs nothing; re-activating resumes polling within one flow
    // emission with no added trigger latency.
    private val screenOn = MutableStateFlow(true)

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> screenOn.value = true
                Intent.ACTION_SCREEN_OFF -> screenOn.value = false
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        startInForeground()
        serviceScope.launch { monitorLoop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        unregisterReceiver(screenReceiver)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startInForeground() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.monitor_notification_channel), NotificationManager.IMPORTANCE_MIN),
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            // Flat white glyph — the adaptive mipmap renders as a grey blob in
            // the status bar.
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.monitor_notification_title))
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Constant is compile-time inlined, so it's safe on API 28 despite
            // being added in Q; only the three-arg overload needs the guard.
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private suspend fun monitorLoop() {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        // The receiver keeps this fresh from here on; read the real state once
        // so a service restart mid-screen-off doesn't poll for nothing.
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        screenOn.value = powerManager.isInteractive

        var windowStart = System.currentTimeMillis()
        var lastPermissionCheckAt = SystemClock.elapsedRealtime()

        // True only when a foreground change could actually produce a trigger.
        // Shared as one long-lived StateFlow instead of left as a plain
        // combine(): the while loop below checks this roughly once a second
        // for as long as the service runs, and a plain cold Flow would
        // re-subscribe — re-querying Room and DataStore from scratch — on
        // every single check instead of once for the service's lifetime.
        val monitorGate = combine(
            settingsRepository.preferences.map { it.appActive }.distinctUntilChanged(),
            monitoredAppsRepository.monitoredApps,
            screenOn,
        ) { active, monitored, screenOnNow ->
            monitoredSnapshot = monitored
            active && monitored.isNotEmpty() && screenOnNow
        }
            // A detection pause must also sever the picker-pairing and
            // call-grace context: a chooser flash or a call from before a
            // pause (screen off, monitoring toggled off) must never be paired
            // against, or extend a grace window into, a post-wake resume. The
            // loop's window reset already drops old events; this drops their
            // remembered counterpart.
            .onEach { open ->
                if (!open) triggerDecision.reset()
            }
            .stateIn(serviceScope, SharingStarted.Eagerly, initialValue = false)

        while (serviceScope.isActive) {
            monitorGate.first { it }
            // Fresh window after every wake-up: never replay events accumulated
            // while suspended — stale resumes could false-trigger.
            windowStart = System.currentTimeMillis()
            delay(POLL_INTERVAL_MS)

            // AppOps is a binder IPC — throttled instead of paid every second.
            // Revocation is still caught promptly enough: the next check, the
            // next foreground change, or MainActivity's resume all re-verify.
            val nowElapsed = SystemClock.elapsedRealtime()
            if (nowElapsed - lastPermissionCheckAt >= PERMISSION_CHECK_INTERVAL_MS) {
                lastPermissionCheckAt = nowElapsed
                if (!permissionsRepository.hasRequiredPermissions()) {
                    Log.d(TAG, "Usage access or overlay permission revoked — stopping")
                    stopSelf()
                    break
                }
            }

            val windowEnd = System.currentTimeMillis()
            // Walk EVERY resume in the window instead of only the latest one:
            // a chooser flash shorter than one poll can sit between the
            // previous app and the target, and pairing consecutive events is
            // what spots picker-mediated entries.
            processResumedEvents(usageStatsManager, windowStart, windowEnd)
        }
    }

    private suspend fun processResumedEvents(manager: UsageStatsManager, from: Long, to: Long) {
        val events = manager.queryEvents(from, to)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType != UsageEvents.Event.ACTIVITY_RESUMED) continue
            when (val verdict = triggerDecision.onResumedActivity(ResumedActivity(event.packageName, event.className))) {
                is Verdict.Trigger -> {
                    Log.d(TAG, "Triggering reading screen for ${verdict.packageName} (${event.className})")
                    launchReadingScreen(verdict.packageName)
                }
                // Only the reasons that logged before the extraction are
                // logged now — same-foreground, not-monitored, inactive,
                // switch-back and cooldown were always silent.
                is Verdict.Ignore -> when (verdict.reason) {
                    Reason.CALL -> Log.d(TAG, "Call detected (${event.packageName}/${event.className}) — suppressing detection")
                    Reason.INTERSTITIAL_RETURN -> Log.d(TAG, "Skipping ${event.packageName} — resumed from Waqfah's interstitial")
                    Reason.CALL_GRACE -> Log.d(TAG, "Skipping ${event.packageName} — within post-call grace window")
                    Reason.INDIRECT_ENTRY -> Log.d(TAG, "Skipping ${event.className} for ${event.packageName} — indirect entry")
                    Reason.SAME_FOREGROUND, Reason.NOT_MONITORED, Reason.INACTIVE, Reason.SWITCH_BACK, Reason.COOLDOWN -> {}
                }
            }
        }
    }

    private fun indirectEntryClasses(packageName: String): Set<String> =
        indirectEntryClassCache.getOrPut(packageName) {
            val packageManager = applicationContext.packageManager
            val classes = mutableSetOf<String>()
            val probes = listOf(
                Intent(Intent.ACTION_SEND).setType("*/*"),
                Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*"),
                // File opens ("Open with …") resolve by MIME type.
                Intent(Intent.ACTION_VIEW).setTypeAndNormalize("*/*"),
                // Link grabs (1DM/ADM-style) resolve by scheme; content://
                // covers viewers registered for in-app file URIs.
                Intent(Intent.ACTION_VIEW, "https://probe.waqfah.local/link".toUri()),
                Intent(Intent.ACTION_VIEW, "content://probe.waqfah.local/file".toUri()),
            )
            for (probe in probes) {
                for (info in packageManager.queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY)) {
                    if (info.activityInfo.packageName == packageName) {
                        info.activityInfo.name?.let(classes::add)
                    }
                }
            }
            // The launcher activity IS how a normal open happens — never
            // suppress it even if it also declares SEND/VIEW filters.
            packageManager.getLaunchIntentForPackage(packageName)?.component?.className?.let(classes::remove)
            classes
        }

    private fun launchReadingScreen(packageName: String) {
        val intent = Intent(this, TriggerActivity::class.java).apply {
            putExtra(TriggerActivity.EXTRA_TRIGGERED_PACKAGE, packageName)
            // Required from a Service context; allowed in the background because
            // Waqfah holds SYSTEM_ALERT_WINDOW (see AndroidManifest).
            // EXCLUDE_FROM_RECENTS mirrors the manifest attribute — some OEM
            // recents screens only honor one or the other.
            // NO_ANIMATION drops the ROM's default activity slide; the
            // interstitial defines its own calm fade instead (see TriggerActivity).
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION,
            )
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch TriggerActivity for $packageName", e)
        }
    }

    companion object {
        private const val TAG = "AppMonitorService"
        private const val CHANNEL_ID = "app_monitor"
        private const val NOTIFICATION_ID = 1
        private const val POLL_INTERVAL_MS = 1_000L

        // How often the loop re-verifies usage access + overlay permission.
        private const val PERMISSION_CHECK_INTERVAL_MS = 30_000L

        // Audio-routing modes that count as "a call owns the audio". Fed to
        // TriggerDecision through the callAudioActive probe.
        private val CALL_AUDIO_MODES = setOf(
            AudioManager.MODE_IN_CALL,
            AudioManager.MODE_IN_COMMUNICATION,
            AudioManager.MODE_RINGTONE,
        )

        // Lookback for isLatestForeground().
        private const val RECENT_EVENT_WINDOW_MS = 3_000L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, AppMonitorService::class.java))
        }

        // True when the most recent ACTIVITY_RESUMED event belongs to
        // [packageName]. Used by TriggerActivity to tell "the user left" apart
        // from "the triggered app raised itself back over the interstitial".
        fun isLatestForeground(context: Context, packageName: String): Boolean {
            val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            return latestResumedPackage(manager, now - RECENT_EVENT_WINDOW_MS, now)?.packageName == packageName
        }

        private fun latestResumedPackage(manager: UsageStatsManager, from: Long, to: Long): ResumedActivity? {
            val events = manager.queryEvents(from, to)
            val event = UsageEvents.Event()
            var latest: ResumedActivity? = null
            var latestTimestamp = Long.MIN_VALUE
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED && event.timeStamp >= latestTimestamp) {
                    latestTimestamp = event.timeStamp
                    latest = ResumedActivity(event.packageName, event.className)
                }
            }
            return latest
        }
    }
}
