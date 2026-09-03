package com.shrekbytes.waqfah.detection

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive

// One watching session of foreground detection: the loop that holds the
// monitor gate open, walks each poll window's resumed activities into the
// trigger decision, and re-verifies permissions while it runs. TriggerDecision
// owns the rules for what earns a pause; this module owns the rhythm of
// watching:
//
//  - The monitor gate — Waqfah active, at least one monitored app, screen on
//    — suspends the loop entirely while closed, so idle time costs nothing;
//    re-activating resumes polling within one flow emission. Closing the gate
//    also severs the decision's remembered context (see onEach below).
//  - Every wake-up opens a FRESH window starting at the wake: events that
//    accumulated while suspended are never replayed, since stale resumes
//    could false-trigger.
//  - Each window is polled once per interval and EVERY resumed activity
//    inside it is fed to the decision in order — not just the latest event,
//    because a chooser flash shorter than one poll must still pair.
//  - A permission heartbeat re-verifies usage access + overlay every
//    PERMISSION_CHECK_INTERVAL_MS; a revocation asks the adapter to end the
//    service (onStopRequested) and returns from run().
//
// Everything impure arrives through the constructor as a function or flow:
// the three gate inputs, the resumed-activities query, the permission probe, the
// two clocks, and the scope hosting the gate's shared state. The adapter
// (AppMonitorService) wires them and receives outcomes through callbacks, so
// the whole rhythm is unit-testable with virtual time (see MonitorSessionTest).
class MonitorSession(
    private val appActive: Flow<Boolean>,
    private val monitoredApps: Flow<Set<String>>,
    private val screenOn: Flow<Boolean>,
    private val resumedActivities: suspend (from: Long, to: Long) -> List<ResumedActivity>,
    private val hasPermissions: () -> Boolean,
    private val decision: TriggerDecision,
    private val onVerdict: (Verdict, ResumedActivity) -> Unit,
    private val onStopRequested: () -> Unit,
    private val nowElapsed: () -> Long,
    private val nowWall: () -> Long,
    private val scope: CoroutineScope,
) {
    suspend fun run() {
        // Polling pauses whenever detection is impossible or pointless —
        // screen off, app paused via Settings, or no monitored apps selected.
        // The loop suspends on this combined gate instead of waking up each
        // interval, so idle time costs nothing.
        val monitorGate = combine(
            appActive,
            monitoredApps,
            screenOn,
        ) { active, monitored, screenOnNow ->
            active && monitored.isNotEmpty() && screenOnNow
        }
            // A detection pause must also sever the picker-pairing and
            // call-grace context: a chooser flash or a call from before a
            // pause (screen off, monitoring toggled off) must never be paired
            // against, or extend a grace window into, a post-wake resume. The
            // loop's window reset already drops old events; this drops their
            // remembered counterpart.
            .onEach { open ->
                if (!open) decision.reset()
            }
            .stateIn(scope, SharingStarted.Eagerly, false)

        var lastPermissionCheckAt = nowElapsed()

        while (scope.isActive) {
            monitorGate.first { it }
            // Fresh window after every wake-up: never replay events
            // accumulated while suspended — stale resumes could false-trigger.
            val windowStart = nowWall()
            delay(POLL_INTERVAL_MS)

            // AppOps is a binder IPC — throttled instead of paid every second.
            // Revocation is still caught promptly enough: the next check, the
            // next foreground change, or MainActivity's resume all re-verify.
            val now = nowElapsed()
            if (now - lastPermissionCheckAt >= PERMISSION_CHECK_INTERVAL_MS) {
                lastPermissionCheckAt = now
                if (!hasPermissions()) {
                    onStopRequested()
                    return
                }
            }

            val windowEnd = nowWall()
            // Walk EVERY resume in the window instead of only the latest one:
            // a chooser flash shorter than one poll can sit between the
            // previous app and the target, and pairing consecutive events is
            // what spots picker-mediated entries.
            for (activity in resumedActivities(windowStart, windowEnd)) {
                val verdict = decision.onResumedActivity(activity)
                onVerdict(verdict, activity)
            }
        }
    }

    private companion object {
        private const val POLL_INTERVAL_MS = 1_000L

        // How often the loop re-verifies usage access + overlay permission.
        private const val PERMISSION_CHECK_INTERVAL_MS = 30_000L
    }
}
