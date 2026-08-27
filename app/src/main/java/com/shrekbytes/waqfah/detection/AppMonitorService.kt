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
import com.shrekbytes.waqfah.BuildConfig
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
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
//  - Returning from Waqfah's own interstitial never re-triggers: finishing
//    TriggerActivity resumes the app underneath, an event identical to a
//    fresh open. Matched by the TriggerActivity class name so an open that
//    comes straight from Waqfah's own main screen still counts as fresh.
//  - Indirect entries are never paused: share-sheet targets, "Open with" file
//    viewers and download-grabbers like 1DM/ADM surface a worker activity of
//    the target app rather than a user-initiated open. These are spotted by
//    pairing consecutive ACTIVITY_RESUMED events (a system chooser/resolver
//    immediately before) or by matching the resumed activity against the
//    package's non-launcher SEND/VIEW intent-filter handlers. Pausing there
//    would make sharing/forwarding painful without adding value; notification
//    taps and fully internal launches remain indistinguishable from real opens
//    via public APIs and still trigger.
//  - Calls are never paused, and never even count as leaving the foreground:
//    a messaging app's own call screen, a system dialer/incall-UI package, or
//    audio routed into a call (see isCallForeground) all count. Call screens
//    aren't launched through a public intent-filter the way share targets
//    are, so this can't be discovered by probing like indirectEntryClasses —
//    it's a maintained heuristic instead (see isKnownCallUi). currentForeground
//    is left untouched for the whole call so whatever it interrupted is still
//    "current" when it hands the foreground back. For CALL_GRACE_MS after a
//    call ends, returning to either that interrupted app or the calling app's
//    own screen also stays quiet, however many hops the return takes (a
//    launcher flash between hanging up and landing is common on some OEMs).
//  - Quick switch-backs never trigger: returning to an app within
//    SWITCH_BACK_GAP_MS of it leaving the foreground counts as the same
//    session no matter what the per-app interval says, so a glance at another
//    app and back doesn't re-show — including right as a short interval
//    happens to elapse mid-glance.
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
    // package last left it — the switch-back rule compares against this to
    // keep quick switch-backs from re-triggering. Deliberately NOT updated
    // while a call owns the foreground (see isCallForeground) — the call
    // isn't "leaving" whatever it interrupted.
    private var currentForeground: String? = null
    private val lastLeftForegroundAt = mutableMapOf<String, Long>()

    // Most recent ACTIVITY_RESUMED component, kept across polls so consecutive
    // events can be paired — that pairing is how picker-mediated entries are
    // recognized even when the chooser flash is shorter than one poll.
    private var lastResumedActivity: ResumedActivity? = null

    // Latest monitored-apps snapshot, kept fresh by monitorGate's combine.
    // evaluateForegroundChange reads this instead of re-querying Room once per
    // foreground change.
    private var monitoredSnapshot: List<MonitoredAppEntity> = emptyList()

    // Lazily-built cache of each package's alternate entry-point activities
    // (share targets, file/link viewers). Queried once per package on first
    // encounter; the monitored set is small so no eviction is needed.
    private val indirectEntryClassCache = HashMap<String, Set<String>>()

    // True while a call currently owns the foreground. Used to (a) tell the
    // very next non-call resume that it's a return from a call, and (b) know
    // when to arm the post-call grace window below.
    private var inCall = false

    // Packages exempt from triggering for CALL_GRACE_MS after a call ends:
    // whatever was in the foreground when the call took over, every call-UI
    // package seen while it was ongoing, and wherever it lands afterward (in
    // case the return trip itself has more than one hop). Cleared whenever a
    // fresh call starts or the grace window lapses.
    private val callGracePackages = mutableSetOf<String>()
    private var callGraceEndsAt: Long? = null

    // No permission needed to read; MODE_IN_CALL/MODE_IN_COMMUNICATION/
    // MODE_RINGTONE is the general-purpose signal that catches calls from
    // apps isKnownCallUi's package/class list doesn't know about.
    private val audioManager: AudioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }

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
                if (!open) {
                    lastResumedActivity = null
                    inCall = false
                    callGracePackages.clear()
                    callGraceEndsAt = null
                }
            }

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

        // A call — a messaging app's own call screen, a system dialer/incall
        // package, or just audio routed into a call — never counts as a
        // foreground change: currentForeground is left exactly as it was so
        // whatever the call interrupted is still "current" once it hands the
        // foreground back. See isCallForeground.
        if (isCallForeground(current)) {
            if (!inCall) {
                inCall = true
                callGracePackages.clear()
                callGraceEndsAt = null
                currentForeground?.let(callGracePackages::add)
                Log.d(TAG, "Call detected ($candidate/${current.className}) — suppressing detection")
            }
            callGracePackages.add(candidate)
            return
        }

        val resumingFromCall = inCall
        inCall = false
        if (resumingFromCall) {
            // The call just ended. Arm the grace window now, from THIS
            // landing spot — whether that's back where the call interrupted,
            // or the calling app's own screen — rather than waiting for a
            // trigger attempt, so a further hop within CALL_GRACE_MS (a
            // launcher flash settling, or the user glancing between the two)
            // is covered too.
            callGracePackages.add(candidate)
            callGraceEndsAt = SystemClock.elapsedRealtime() + CALL_GRACE_MS
        }

        if (candidate == currentForeground) return

        // Note when the outgoing package left the foreground — used by the
        // switch-back rule below to tell a fresh open apart from a quick
        // switch-back.
        currentForeground?.let { left ->
            val nowElapsed = SystemClock.elapsedRealtime()
            lastLeftForegroundAt[left] = nowElapsed
            // Bound memory: only exits within SWITCH_BACK_GAP_MS matter, so
            // expired entries are swept BY AGE. A size-cap eviction here would
            // be wrong — re-exiting a package updates its value without
            // refreshing its LinkedHashMap position, so "oldest inserted"
            // isn't necessarily the oldest exit.
            lastLeftForegroundAt.entries.removeAll { (_, exitedAt) -> nowElapsed - exitedAt >= SWITCH_BACK_GAP_MS }
        }
        currentForeground = candidate

        if (monitoredSnapshot.none { it.packageName == candidate }) return

        // The dismissal flow: TriggerActivity finishes, the paused app
        // underneath resumes. Without this guard that resume re-triggers
        // whenever the reading session outlasted SWITCH_BACK_GAP_MS —
        // trapping the user in a loop of interstitials.
        if (isReturnFromInterstitial(previous?.packageName, previous?.className)) {
            Log.d(TAG, "Skipping $candidate — resumed from Waqfah's interstitial")
            return
        }

        // Landing here right after a call — on either the app it interrupted
        // or the calling app's own screen, however many hops it took — is
        // never a fresh, user-initiated open.
        if (isWithinCallGrace(candidate)) {
            Log.d(TAG, "Skipping $candidate — within post-call grace window")
            return
        }

        // Share sheets, "Open with" dialogs and download-grabbers surface a
        // worker activity of the target app, not a user-initiated open —
        // pausing there makes sharing/forwarding painful without adding value.
        if (isIndirectEntry(previous, current)) {
            Log.d(TAG, "Skipping ${current.className} for $candidate — indirect entry")
            return
        }

        val prefs = settingsRepository.preferences.first()
        if (!prefs.appActive) return

        // A quick switch-back — glancing at another app and returning within
        // SWITCH_BACK_GAP_MS — is the same session and never triggers, no
        // matter what the per-app interval says. This used to only apply
        // when the interval was Off; checking it here too stops a switch
        // timed right as a short interval elapses from firing again
        // immediately.
        val leftAt = lastLeftForegroundAt[candidate]
        if (leftAt != null && SystemClock.elapsedRealtime() - leftAt < SWITCH_BACK_GAP_MS) return

        if (prefs.cooldownMinutes > 0 && monitoredAppsRepository.isInCooldown(candidate, prefs.cooldownMinutes)) {
            return
        }

        Log.d(TAG, "Triggering reading screen for $candidate (${current.className})")
        monitoredAppsRepository.recordShown(candidate)
        launchReadingScreen(packageName = candidate)
    }

    // True when [event] is a call's own UI rather than a normal app screen —
    // matched against the maintained package/class heuristic first (cheap,
    // and catches an incoming call while it's still only ringing, before any
    // audio is routed), then against the device's audio routing (catches any
    // other calling app once its call actually connects).
    //
    // The audio check is deliberately a plain level check, not "did the mode
    // just change": while it's active, EVERY foreground change reads as a
    // call — including switching between other apps with the call minimized
    // to picture-in-picture — so nothing triggers anywhere until the call's
    // audio actually ends. That is the intended behavior, not a gap: someone
    // mid-call doesn't want a reading pause competing for their attention
    // just because they alt-tabbed while still on the line.
    private fun isCallForeground(event: ResumedActivity): Boolean {
        if (isKnownCallUi(event.packageName, event.className)) return true
        return when (audioManager.mode) {
            AudioManager.MODE_IN_CALL, AudioManager.MODE_IN_COMMUNICATION, AudioManager.MODE_RINGTONE -> true
            else -> false
        }
    }

    // True when [packageName] is still within CALL_GRACE_MS of a call ending
    // and was one of the packages involved in that call. Expires itself
    // lazily on the first check after the window lapses — consistent with
    // lastLeftForegroundAt's age-based sweep elsewhere in this class.
    private fun isWithinCallGrace(packageName: String): Boolean {
        val endsAt = callGraceEndsAt ?: return false
        if (SystemClock.elapsedRealtime() >= endsAt) {
            callGraceEndsAt = null
            callGracePackages.clear()
            return false
        }
        return packageName in callGracePackages
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

        // Quick switch-back window: returning to an app within this long of
        // it leaving the foreground is treated as the same session (no
        // re-trigger), regardless of the per-app interval. Public usage APIs
        // can't tell a launcher tap from a recents-resume, so this timing
        // rule approximates "fresh open" everywhere, not just when the
        // interval is Off.
        private const val SWITCH_BACK_GAP_MS = 45_000L

        // Post-call grace: for this long after a call ends, the app it
        // interrupted and the calling app's own screen are both treated as
        // "same session, not a fresh open" — shorter than SWITCH_BACK_GAP_MS
        // on purpose, since a call is a much stronger, more legible signal
        // that nothing was deliberately closed than an ordinary app switch.
        private const val CALL_GRACE_MS = 15_000L

        // Lookback for isLatestForeground().
        private const val RECENT_EVENT_WINDOW_MS = 3_000L

        // Stock + common OEM in-call UI / dialer packages. A call routed
        // through any of these briefly owns the foreground on top of, or
        // instead of, the app the user was actually in — never a real "open"
        // of anything. Add more here if a specific OEM's incall package is
        // reported to slip through.
        private val SYSTEM_CALL_PACKAGES = setOf(
            "com.android.server.telecom",
            "com.android.incallui",
            "com.android.dialer",
            "com.google.android.dialer",
            "com.google.android.ims",
            "com.samsung.android.incallui",
            "com.samsung.android.dialer",
            "com.miui.contacts", // Xiaomi's in-call UI ships inside the contacts package.
        )

        // Case-insensitive substrings matched against the resumed activity's
        // class name to catch a messaging app's OWN call screen (WhatsApp,
        // Messenger, Telegram, Signal, Viber, Duo, Meet, Discord, Skype,
        // Line, IMO, …). Unlike indirectEntryClasses, this can't be
        // discovered by probing public intent-filters — call screens aren't
        // launched through one, they're started internally by the app's own
        // call-handling code — so it's a maintained heuristic instead.
        // Deliberately broad: a stray non-call "…Call…" screen (e.g. a call
        // LOG) being skipped once is far cheaper than a pause blocking
        // someone from answering or hanging up, and it only ever matters on
        // the one activity a package first opens into (see isCallForeground
        // callers — internal navigation never re-runs this check).
        private val CALL_CLASS_KEYWORDS = listOf("call", "voip", "incall")

        // Pure core of the call-UI rule, extracted for unit testing — the
        // package/class half only; the audio-routing half needs a live
        // AudioManager and lives in the instance method isCallForeground.
        internal fun isKnownCallUi(packageName: String, className: String?): Boolean {
            if (packageName in SYSTEM_CALL_PACKAGES) return true
            return className != null && CALL_CLASS_KEYWORDS.any { className.contains(it, ignoreCase = true) }
        }

        // Pure core of the interstitial-return rule, extracted for unit
        // testing: did [previousPackage]/[previousClass] resume Waqfah's
        // TriggerActivity? Class match is what separates "returned from the
        // interstitial" from "opened after using Waqfah itself".
        internal fun isReturnFromInterstitial(previousPackage: String?, previousClass: String?): Boolean =
            previousPackage == BuildConfig.APPLICATION_ID &&
                previousClass == TriggerActivity::class.java.name

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
