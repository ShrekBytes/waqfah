package com.shrekbytes.waqfah.detection

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
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
import kotlinx.coroutines.flow.first
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
//  - The interval stepper's 0 ("Off") keeps detection on: every fresh open
//    triggers, but returning to an app that left the foreground less than
//    OFF_SESSION_GAP_MS ago counts as the same session and stays quiet — so
//    minimize/switch-back doesn't re-show.
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

    // The package we currently believe owns the foreground, plus when each
    // package last left it — the Off-interval rule compares against this to
    // keep quick switch-backs from re-triggering.
    private var currentForeground: String? = null
    private val lastLeftForegroundAt = mutableMapOf<String, Long>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        serviceScope.launch { monitorLoop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startInForeground() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Background monitoring", NotificationManager.IMPORTANCE_MIN),
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
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
        var windowStart = System.currentTimeMillis()

        while (serviceScope.isActive) {
            delay(POLL_INTERVAL_MS)

            if (!permissionsRepository.hasRequiredPermissions()) {
                Log.d(TAG, "Usage access or overlay permission revoked — stopping")
                stopSelf()
                break
            }

            val windowEnd = System.currentTimeMillis()
            val candidate = latestForegroundPackage(usageStatsManager, windowStart, windowEnd)
            windowStart = windowEnd

            if (candidate == null || candidate == currentForeground) continue

            // Note when the outgoing package left the foreground — used by the
            // interval-Off rule below to tell a fresh open apart from a quick
            // switch-back.
            currentForeground?.let { lastLeftForegroundAt[it] = SystemClock.elapsedRealtime() }
            currentForeground = candidate

            val monitored = monitoredAppsRepository.monitoredApps.first()
            if (monitored.none { it.packageName == candidate }) continue

            val prefs = settingsRepository.preferences.first()
            if (!prefs.appActive) continue
            if (prefs.cooldownMinutes <= 0) {
                // Interval Off: still detect every open, but treat returning to
                // an app that left the foreground moments ago (minimize,
                // glance-away, straight switch-back) as the SAME session — no
                // trigger. Coming back after the session gap counts as a fresh
                // open again.
                val leftAt = lastLeftForegroundAt[candidate]
                if (leftAt != null && SystemClock.elapsedRealtime() - leftAt < OFF_SESSION_GAP_MS) continue
            } else if (monitoredAppsRepository.isInCooldown(candidate, prefs.cooldownMinutes)) {
                continue
            }

            Log.d(TAG, "Triggering reading screen for $candidate")
            monitoredAppsRepository.recordShown(candidate)
            launchReadingScreen(packageName = candidate)
        }
    }

    private fun launchReadingScreen(packageName: String) {
        val intent = Intent(this, TriggerActivity::class.java).apply {
            putExtra(TriggerActivity.EXTRA_TRIGGERED_PACKAGE, packageName)
            // Required from a Service context; allowed in the background because
            // Waqfah holds SYSTEM_ALERT_WINDOW (see AndroidManifest).
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch TriggerActivity for $packageName", e)
        }
    }

    // Same scan as latestResumedPackage, minus Waqfah itself — the monitor must
    // never react to its own interstitial coming to the front.
    private fun latestForegroundPackage(manager: UsageStatsManager, from: Long, to: Long): String? =
        latestResumedPackage(manager, from, to)?.takeUnless { it == packageName }

    companion object {
        private const val TAG = "AppMonitorService"
        private const val CHANNEL_ID = "app_monitor"
        private const val NOTIFICATION_ID = 1
        private const val POLL_INTERVAL_MS = 1_000L

        // Interval-Off session gap: returning to an app within this window of
        // it leaving the foreground is treated as the same session (no
        // re-trigger). Public usage APIs can't tell a launcher tap from a
        // recents-resume, so this timing rule approximates "fresh open".
        private const val OFF_SESSION_GAP_MS = 45_000L

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
            return latestResumedPackage(manager, now - RECENT_EVENT_WINDOW_MS, now) == packageName
        }

        private fun latestResumedPackage(manager: UsageStatsManager, from: Long, to: Long): String? {
            val events = manager.queryEvents(from, to)
            val event = UsageEvents.Event()
            var latest: String? = null
            var latestTimestamp = Long.MIN_VALUE
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED && event.timeStamp >= latestTimestamp) {
                    latestTimestamp = event.timeStamp
                    latest = event.packageName
                }
            }
            return latest
        }
    }
}
