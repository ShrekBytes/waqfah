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
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.shrekbytes.waqfah.R
import com.shrekbytes.waqfah.TriggerActivity
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

// Watches foreground app changes by polling UsageStatsManager and launches
// TriggerActivity when a monitored app comes to the front. Runs as a specialUse
// foreground service; polling keeps battery cost proportional to how long
// Waqfah runs, not to how often the device switches apps.
//
// Trigger rules:
//  - At most ONE pause per continuous stay in the foreground of an app, no
//    matter how many ACTIVITY_RESUMED events that stay emits (splash screens,
//    notification trampolines and multi-activity apps like Messenger fire the
//    event several times per launch). Leaving for ANY other app re-arms it;
//    the persisted per-app interval then decides whether the next open is
//    allowed through.
//  - Indirect entries are never paused: share-sheet targets, "Open with" file
//    viewers and download-grabbers like 1DM/ADM surface a worker activity of
//    the target app rather than a user-initiated open. These are spotted by
//    pairing consecutive ACTIVITY_RESUMED events (a system chooser/resolver
//    immediately before) or by matching the resumed activity against the
//    package's non-launcher SEND/VIEW intent-filter handlers. Pausing there
//    would make sharing/forwarding painful without adding value; notification
//    taps and fully internal launches remain indistinguishable from real opens
//    via public APIs and still trigger.
//  - The interval stepper's 0 ("Off") keeps detection on: every fresh open
//    triggers, but returning to an app that left the foreground less than
//    OFF_SESSION_GAP_MS ago counts as the same session and stays quiet — so
//    minimize/switch-back doesn't re-show.
@AndroidEntryPoint
class AppMonitorService : Service() {

    // One foreground observation from UsageStatsManager. The activity class is
    // what tells a real open apart from an indirect entry (share target, file
    // viewer, link grabber) — see isIndirectEntry.
    private data class ResumedActivity(val packageName: String, val className: String?)

    @Inject lateinit var monitoredAppsRepository: MonitoredAppsRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var permissionsRepository: PermissionsRepository

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(
        serviceJob + Dispatchers.Default + CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "Unhandled error in app monitor coroutine", throwable)
        },
    )

    // The package we currently believe owns the foreground, plus when each
    // package last left it — the Off-interval rule compares against this to
    // keep quick switch-backs from re-triggering.
    private var currentForeground: String? = null
    private val lastLeftForegroundAt = mutableMapOf<String, Long>()

    // Most recent ACTIVITY_RESUMED component, kept across polls so consecutive
    // events can be paired — that pairing is how picker-mediated entries are
    // recognized even when the chooser flash is shorter than one poll.
    private var lastResumedActivity: ResumedActivity? = null

    // Lazily-built cache of each package's alternate entry-point activities
    // (share targets, file/link viewers). Queried once per package on first
    // encounter; the monitored set is small so no eviction is needed.
    private val indirectEntryClassCache = HashMap<String, Set<String>>()

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
        val monitorGate = combine(
            settingsRepository.preferences.map { it.appActive }.distinctUntilChanged(),
            monitoredAppsRepository.monitoredApps.map { it.isNotEmpty() }.distinctUntilChanged(),
            screenOn,
        ) { active, hasMonitoredApps, screenOnNow -> active && hasMonitoredApps && screenOnNow }

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
            windowStart = windowEnd
        }
    }

    private suspend fun processResumedEvents(manager: UsageStatsManager, from: Long, to: Long) {
        val events = manager.queryEvents(from, to)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType != UsageEvents.Event.ACTIVITY_RESUMED) continue
            val current = ResumedActivity(event.packageName, event.className)
            evaluateForegroundChange(lastResumedActivity, current)
            lastResumedActivity = current
        }
    }

    private suspend fun evaluateForegroundChange(previous: ResumedActivity?, current: ResumedActivity) {
        val candidate = current.packageName
        if (candidate == currentForeground) return

        // Note when the outgoing package left the foreground — used by the
        // interval-Off rule below to tell a fresh open apart from a quick
        // switch-back.
        currentForeground?.let { left ->
            lastLeftForegroundAt[left] = SystemClock.elapsedRealtime()
            // Bound memory: only exits within OFF_SESSION_GAP_MS matter, so
            // stale entries are dropped instead of accumulating forever.
            while (lastLeftForegroundAt.size > MAX_TRACKED_EXITS) {
                lastLeftForegroundAt.remove(lastLeftForegroundAt.keys.first())
            }
        }
        currentForeground = candidate

        val monitored = monitoredAppsRepository.monitoredApps.first()
        if (monitored.none { it.packageName == candidate }) return

        // Share sheets, "Open with" dialogs and download-grabbers surface a
        // worker activity of the target app, not a user-initiated open —
        // pausing there makes sharing/forwarding painful without adding value.
        if (isIndirectEntry(previous, current)) {
            Log.d(TAG, "Skipping ${current.className} for $candidate — indirect entry")
            return
        }

        val prefs = settingsRepository.preferences.first()
        if (!prefs.appActive) return
        if (prefs.cooldownMinutes <= 0) {
            // Interval Off: still detect every open, but treat returning to
            // an app that left the foreground moments ago (minimize,
            // glance-away, straight switch-back) as the SAME session — no
            // trigger. Coming back after the session gap counts as a fresh
            // open again.
            val leftAt = lastLeftForegroundAt[candidate]
            if (leftAt != null && SystemClock.elapsedRealtime() - leftAt < OFF_SESSION_GAP_MS) return
        } else if (monitoredAppsRepository.isInCooldown(candidate, prefs.cooldownMinutes)) {
            return
        }

        Log.d(TAG, "Triggering reading screen for $candidate (${current.className})")
        monitoredAppsRepository.recordShown(candidate)
        launchReadingScreen(packageName = candidate)
    }

    // True when [current] looks like an entry through a picker or an alternate
    // intent-filter activity rather than a fresh user-initiated open.
    private fun isIndirectEntry(previous: ResumedActivity?, current: ResumedActivity): Boolean {
        if (isSystemPicker(previous)) return true
        val className = current.className ?: return false
        return className in indirectEntryClasses(current.packageName)
    }

    private fun isSystemPicker(event: ResumedActivity?): Boolean {
        val className = event?.className ?: return false
        // The framework chooser/resolver runs under the "android" package;
        // OEM skins subclass them elsewhere, hence the suffix heuristic.
        return event.packageName == "android" ||
            className.endsWith("ResolverActivity") ||
            className.endsWith("ChooserActivity")
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
                Intent(Intent.ACTION_VIEW, Uri.parse("https://probe.waqfah.local/link")),
                Intent(Intent.ACTION_VIEW, Uri.parse("content://probe.waqfah.local/file")),
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

        // Interval-Off session gap: returning to an app within this window of
        // it leaving the foreground is treated as the same session (no
        // re-trigger). Public usage APIs can't tell a launcher tap from a
        // recents-resume, so this timing rule approximates "fresh open".
        private const val OFF_SESSION_GAP_MS = 45_000L

        // Cap for lastLeftForegroundAt — only recent exits matter.
        private const val MAX_TRACKED_EXITS = 32

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
