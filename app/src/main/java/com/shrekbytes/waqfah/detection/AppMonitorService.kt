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
import com.shrekbytes.waqfah.data.repository.MonitoredAppsRepository
import com.shrekbytes.waqfah.data.repository.PermissionsRepository
import com.shrekbytes.waqfah.data.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// Watches which app is in the foreground and launches TriggerActivity when a
// monitored app earns a reading pause. Runs as a specialUse foreground
// service. All the watching logic — the monitor gate, the poll windows, the
// permission heartbeat — lives in MonitorSession, which owns the rhythm of
// detection the same way TriggerDecision owns its rules. This service is the
// Android adapter for both: it wires the impure probes (resumed-activity
// queries, screen state, audio routing, PackageManager, preferences, the
// persisted trigger stamp), feeds the session, and acts on outcomes by
// launching the interstitial on a Trigger verdict and stopping itself when
// the session asks.
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

    // Latest monitored-apps snapshot, shared between the session's gate input
    // and TriggerDecision's isMonitored probe — one Room subscription for
    // both, so the trigger decision reads a warm snapshot instead of
    // re-querying Room once per foreground change.
    private val monitoredPackages by lazy {
        monitoredAppsRepository.monitoredApps
            .map { apps -> apps.map { it.packageName }.toSet() }
            .stateIn(serviceScope, SharingStarted.Eagerly, emptySet())
    }

    // Lazily-built cache of each package's alternate entry-point activities
    // (share targets, file/link viewers). Queried once per package on first
    // encounter; the monitored set is small so no eviction is needed.
    private val indirectEntryClassCache = HashMap<String, Set<String>>()

    // No permission needed to read; MODE_IN_CALL/MODE_IN_COMMUNICATION/
    // MODE_RINGTONE is the general-purpose signal that catches calls from
    // apps the call-UI package/class heuristic doesn't know about.
    private val audioManager: AudioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }

    private val usageStatsManager: UsageStatsManager by lazy {
        getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    }

    // The trigger decision and all of its rule state. The impure edges are
    // wired here: audio routing, the cached PackageManager probe, the
    // preference snapshot (read late, after the cheap rules have run), the
    // persisted trigger stamp, and the two clocks.
    private val triggerDecision by lazy {
        TriggerDecision(
            isMonitored = { pkg -> pkg in monitoredPackages.value },
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

    // The watching session. Its gate inputs come from the adapters below:
    // the appActive preference mapped to a plain boolean flow, the shared
    // monitored-package snapshot, and the screen-on state the receiver keeps
    // fresh.
    private val monitorSession by lazy {
        MonitorSession(
            appActive = settingsRepository.preferences.map { it.appActive }.distinctUntilChanged(),
            monitoredApps = monitoredPackages,
            screenOn = screenOn,
            resumedActivities = ::queryResumedActivities,
            hasPermissions = { permissionsRepository.hasRequiredPermissions() },
            decision = triggerDecision,
            onVerdict = ::handleVerdict,
            onStopRequested = {
                Log.d(TAG, "Usage access or overlay permission revoked — stopping")
                stopSelf()
            },
            nowElapsed = SystemClock::elapsedRealtime,
            nowWall = System::currentTimeMillis,
            scope = serviceScope,
        )
    }

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
        // The receiver keeps this fresh from here on; read the real state once
        // so a service restart mid-screen-off doesn't poll for nothing.
        screenOn.value = (getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
        startInForeground()
        serviceScope.launch { monitorSession.run() }
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

    private fun queryResumedActivities(from: Long, to: Long): List<ResumedActivity> {
        val events = usageStatsManager.queryEvents(from, to)
        val event = UsageEvents.Event()
        val resumed = mutableListOf<ResumedActivity>()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                resumed += ResumedActivity(event.packageName, event.className)
            }
        }
        return resumed
    }

    // Adapter for the session's verdicts: launch the interstitial on a
    // Trigger, log the reasons that logged before the TriggerDecision
    // extraction — same-foreground, not-monitored, inactive, switch-back and
    // cooldown were always silent.
    private fun handleVerdict(verdict: Verdict, activity: ResumedActivity) {
        when (verdict) {
            is Verdict.Trigger -> {
                Log.d(TAG, "Triggering reading screen for ${verdict.packageName} (${activity.className})")
                launchReadingScreen(verdict.packageName)
            }
            is Verdict.Ignore -> when (verdict.reason) {
                Reason.CALL -> Log.d(TAG, "Call detected (${activity.packageName}/${activity.className}) — suppressing detection")
                Reason.INTERSTITIAL_RETURN -> Log.d(TAG, "Skipping ${activity.packageName} — resumed from Waqfah's interstitial")
                Reason.CALL_GRACE -> Log.d(TAG, "Skipping ${activity.packageName} — within post-call grace window")
                Reason.INDIRECT_ENTRY -> Log.d(TAG, "Skipping ${activity.className} for ${activity.packageName} — indirect entry")
                Reason.SAME_FOREGROUND, Reason.NOT_MONITORED, Reason.INACTIVE, Reason.SWITCH_BACK, Reason.COOLDOWN -> {}
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
