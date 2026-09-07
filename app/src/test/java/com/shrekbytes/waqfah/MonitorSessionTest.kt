package com.shrekbytes.waqfah

import com.shrekbytes.waqfah.detection.MonitorSession
import com.shrekbytes.waqfah.detection.Reason
import com.shrekbytes.waqfah.detection.ResumedActivity
import com.shrekbytes.waqfah.detection.TriggerDecision
import com.shrekbytes.waqfah.detection.TriggerPrefs
import com.shrekbytes.waqfah.detection.Verdict
import com.shrekbytes.waqfah.data.monitoredapp.MonitoredAppMembership
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Tests the MonitorSession at its interface: flows in, one run() loop, verdicts
// and stop requests out. The decision is the real TriggerDecision with fake
// probes behind its own ports, so the session is exercised against the genuine
// module pair. Virtual time drives the loop's delays; the session's elapsed
// clock AND wall clock are both the test scheduler's clock — the wall one
// plus a constant epoch offset — so the wall clock runs while a window's
// events are queried, decided and dispatched. The manual wall clock this
// harness replaced froze during that processing slice, the blind spot both
// audits flagged: a poll-window gap could hide behind a green suite.
@OptIn(ExperimentalCoroutinesApi::class)
class MonitorSessionTest {

    private companion object {
        const val MONITORED_APP = "com.target.app"
        // The session's poll interval, restated here as the spec: one window
        // per 1 000 ms of loop time.
        const val POLL_MS = 1_000L

        // The wall clock's value at virtual time zero. Scheduler time starts
        // at 0; a real wall clock doesn't — the offset keeps window bounds
        // reading like timestamps instead of loop arithmetic.
        const val WALL_EPOCH_MS = 1_000_000L
    }

    private val appActive = MutableStateFlow(true)
    private val monitoredApps = MutableStateFlow(setOf(MONITORED_APP))
    private val screenOn = MutableStateFlow(true)

    // A mid-session wall-clock jump (an NTP resync): added on top of virtual
    // time, so a test can move the wall clock without moving the loop — the
    // wall jumps while the poll sleeps.
    private var wallJumpMs = 0L

    // What one window's processing costs in virtual time: the UsageStats
    // query — and, when the window carries events, the decision and verdict
    // dispatch behind it. A real clock runs through that slice between one
    // window's end and the next one's start; zero everywhere except the
    // tiling pin, which needs the slice to exist.
    private var processingCostMs = 0L

    private var queuedEvents: List<ResumedActivity> = emptyList()
    private val queriedWindows = mutableListOf<Pair<Long, Long>>()

    private val verdicts = mutableListOf<Pair<Verdict, ResumedActivity>>()
    private var stopRequests = 0
    private var permissionProbeCalls = 0
    private var permissionsGranted = true

    // TriggerDecision's own fake probes, wired inside session() so its wall
    // clock can share the session's scheduler-driven one. decisionElapsed
    // drives its internal windows (switch-back, call grace); nothing advances
    // it unless a test does.
    private val stamped = mutableListOf<String>()
    private val monitored = mutableSetOf(MONITORED_APP)
    private var callAudio = false
    private var decisionElapsed = 0L

    // The wall clock both halves read: virtual time plus the epoch plus any
    // jump. Unlike the manual var it replaces, it advances while a window's
    // events are processed, because virtual time does.
    private fun TestScope.wallNow() = WALL_EPOCH_MS + wallJumpMs + testScheduler.currentTime

    private fun TestScope.session() = MonitorSession(
        appActive = appActive,
        monitoredApps = monitoredApps,
        screenOn = screenOn,
        resumedActivities = { from, to ->
            queriedWindows += from to to
            delay(processingCostMs)
            queuedEvents
        },
        hasPermissions = {
            permissionProbeCalls++
            permissionsGranted
        },
        decision = TriggerDecision(
            isMonitored = { it in monitored },
            callAudioActive = { callAudio },
            indirectEntryClasses = { emptySet() },
            prefs = { TriggerPrefs(true, 0) },
            monitoredMembership = { pkg ->
                if (pkg in monitored) MonitoredAppMembership(pkg, "membership-$pkg", null, 0L) else null
            },
            claimTrigger = { membership, _ ->
                stamped += membership.packageName
                true
            },
            interstitialClassName = "com.shrekbytes.waqfah.TriggerActivity",
            nowElapsed = { decisionElapsed },
            nowWall = { wallNow() },
        ),
        onVerdict = { verdict, activity -> verdicts += verdict to activity },
        onStopRequested = { stopRequests++ },
        nowElapsed = { testScheduler.currentTime },
        nowWall = { wallNow() },
        scope = backgroundScope,
    )

    @Test
    fun `polls one usage window per interval`() = runTest {
        val job = launch { session().run() }

        advanceTimeBy(POLL_MS)
        runCurrent()
        val firstEnd = WALL_EPOCH_MS + POLL_MS
        // The wall clock runs while the poll sleeps, so the window spans the
        // whole interval.
        assertEquals(listOf(WALL_EPOCH_MS to firstEnd), queriedWindows)

        // The wall clock jumps while the poll sleeps: the next window still
        // starts where the last one ended and ends at now, jump included.
        wallJumpMs = 500L
        advanceTimeBy(POLL_MS)
        runCurrent()
        assertEquals(
            listOf(WALL_EPOCH_MS to firstEnd, firstEnd to firstEnd + POLL_MS + 500L),
            queriedWindows,
        )

        job.cancel()
    }

    // Real clocks keep running while a window's events are queried, decided
    // and dispatched; the next window must start where the previous one
    // ended, or every event landing in that processing slice belongs to no
    // window and never reaches the decision. The scheduler-driven wall clock
    // runs through the slice — the manual clock it replaced froze there — so
    // this pin actually sees the gap: against a window that restarts at the
    // current clock reading instead of the previous window's end, it fails.
    @Test
    fun `windows tile the time the gate stays open`() = runTest {
        processingCostMs = 50L
        val job = launch { session().run() }

        repeat(3) {
            advanceTimeBy(POLL_MS + processingCostMs)
            runCurrent()
        }

        // Each consecutive pair shares an endpoint — the processing slice
        // between one window's end and the next poll's wake included — so
        // no slice of open-gate time is left uncovered.
        val firstEnd = WALL_EPOCH_MS + POLL_MS
        val secondEnd = WALL_EPOCH_MS + 2 * POLL_MS + processingCostMs
        val thirdEnd = WALL_EPOCH_MS + 3 * POLL_MS + 2 * processingCostMs
        assertEquals(
            listOf(
                WALL_EPOCH_MS to firstEnd,
                firstEnd to secondEnd,
                secondEnd to thirdEnd,
            ),
            queriedWindows,
        )

        job.cancel()
    }

    @Test
    fun `stays quiet while the gate is closed and wakes into a fresh window`() = runTest {
        appActive.value = false
        val job = launch { session().run() }

        advanceTimeBy(10_000)
        runCurrent()
        assertTrue(queriedWindows.isEmpty())

        // Wake: everything the wall clock ran past while closed — the same
        // 10 s, since both clocks are the scheduler's — is stale. The fresh
        // window starts at the wake, not at the last polled window.
        appActive.value = true
        advanceTimeBy(POLL_MS)
        runCurrent()

        assertEquals(
            listOf(WALL_EPOCH_MS + 10_000L to WALL_EPOCH_MS + 10_000L + POLL_MS),
            queriedWindows,
        )

        job.cancel()
    }

    @Test
    fun `feeds every resumed activity in a window to the decision and dispatches verdicts`() = runTest {
        // Two events, two verdicts — the window is walked in full, not just
        // its latest event, and in event order.
        val targetOpen = ResumedActivity(MONITORED_APP, "com.target.app.MainActivity")
        val otherOpen = ResumedActivity("com.other.app", "com.other.app.OtherActivity")
        queuedEvents = listOf(targetOpen, otherOpen)

        val job = launch { session().run() }
        advanceTimeBy(POLL_MS)
        runCurrent()

        assertEquals(
            listOf(
                Verdict.Trigger(MONITORED_APP) to targetOpen,
                Verdict.Ignore(Reason.NOT_MONITORED) to otherOpen,
            ),
            verdicts,
        )

        job.cancel()
    }

    @Test
    fun `a gate close severs the decision's remembered context`() = runTest {
        // Window 1: an unmonitored open, then Waqfah's own interstitial
        // activity. If that remembered event survives the pause, the
        // interstitial-return rule pairs it against the next resume and
        // swallows the fresh open.
        val interstitialEvent = ResumedActivity("com.shrekbytes.waqfah", "com.shrekbytes.waqfah.TriggerActivity")
        queuedEvents = listOf(
            ResumedActivity("com.other.app", "com.other.app.OtherActivity"),
            interstitialEvent,
        )
        val job = launch { session().run() }
        advanceTimeBy(POLL_MS)
        runCurrent()

        // Detection pauses…
        appActive.value = false
        runCurrent()

        // …and resumes. The fresh open must be treated as fresh.
        queuedEvents = listOf(ResumedActivity(MONITORED_APP, "com.target.app.MainActivity"))
        appActive.value = true
        advanceTimeBy(POLL_MS)
        runCurrent()

        assertEquals(Verdict.Trigger(MONITORED_APP), verdicts.last().first)

        job.cancel()
    }

    @Test
    fun `stops the session when permissions are revoked`() = runTest {
        permissionsGranted = false
        val job = launch { session().run() }

        advanceTimeBy(30_000)
        runCurrent()

        assertEquals(1, stopRequests)
        assertTrue(job.isCompleted)
    }

    @Test
    fun `re-verifies permissions no more often than the heartbeat interval`() = runTest {
        permissionsGranted = true
        val job = launch { session().run() }

        // AppOps is a binder IPC — the first 30 seconds of polling pay for
        // none of it.
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(0, permissionProbeCalls)

        advanceTimeBy(20_000)
        runCurrent()
        assertEquals(1, permissionProbeCalls)
        assertFalse(job.isCompleted)

        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(1, permissionProbeCalls)

        advanceTimeBy(20_000)
        runCurrent()
        assertEquals(2, permissionProbeCalls)

        job.cancel()
    }
}
