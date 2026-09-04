package com.shrekbytes.waqfah

import com.shrekbytes.waqfah.detection.MonitorSession
import com.shrekbytes.waqfah.detection.Reason
import com.shrekbytes.waqfah.detection.ResumedActivity
import com.shrekbytes.waqfah.detection.TriggerDecision
import com.shrekbytes.waqfah.detection.TriggerPrefs
import com.shrekbytes.waqfah.detection.Verdict
import com.shrekbytes.waqfah.data.monitoredapp.MonitoredAppMembership
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
// module pair. Virtual time drives the loop's delays; the elapsed clock is the
// test scheduler's clock (delays advance it), the wall clock is manual.
@OptIn(ExperimentalCoroutinesApi::class)
class MonitorSessionTest {

    private companion object {
        const val MONITORED_APP = "com.target.app"
        // The session's poll interval, restated here as the spec: one window
        // per 1 000 ms of loop time.
        const val POLL_MS = 1_000L
    }

    private val appActive = MutableStateFlow(true)
    private val monitoredApps = MutableStateFlow(setOf(MONITORED_APP))
    private val screenOn = MutableStateFlow(true)

    private var wallNow = 1_000_000L
    private var queuedEvents: List<ResumedActivity> = emptyList()
    private val queriedWindows = mutableListOf<Pair<Long, Long>>()

    private val verdicts = mutableListOf<Pair<Verdict, ResumedActivity>>()
    private var stopRequests = 0
    private var permissionProbeCalls = 0
    private var permissionsGranted = true

    // TriggerDecision's own fake probes. decisionElapsed drives its internal
    // windows (switch-back, call grace); nothing advances it unless a test does.
    private val stamped = mutableListOf<String>()
    private val monitored = mutableSetOf(MONITORED_APP)
    private var callAudio = false
    private var decisionElapsed = 0L
    private val decision = TriggerDecision(
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
        nowElapsed = { decisionElapsed },
        nowWall = { wallNow },
    )

    private fun TestScope.session() = MonitorSession(
        appActive = appActive,
        monitoredApps = monitoredApps,
        screenOn = screenOn,
        resumedActivities = { from, to ->
            queriedWindows += from to to
            queuedEvents
        },
        hasPermissions = {
            permissionProbeCalls++
            permissionsGranted
        },
        decision = decision,
        onVerdict = { verdict, activity -> verdicts += verdict to activity },
        onStopRequested = { stopRequests++ },
        nowElapsed = { testScheduler.currentTime },
        nowWall = { wallNow },
        scope = backgroundScope,
    )

    @Test
    fun `polls one usage window per interval`() = runTest {
        val job = launch { session().run() }

        advanceTimeBy(POLL_MS)
        runCurrent()
        assertEquals(listOf(1_000_000L to 1_000_000L), queriedWindows)

        wallNow = 1_000_500L
        advanceTimeBy(POLL_MS)
        runCurrent()
        // The wall clock jumped while the poll slept: the window still spans
        // the whole interval — start where the last window ended, end at now.
        assertEquals(
            listOf(1_000_000L to 1_000_000L, 1_000_000L to 1_000_500L),
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

        // Wake: everything the wall clock ran past while closed is stale —
        // the fresh window starts at the wake, not at the last polled window.
        wallNow = 1_500_000L
        appActive.value = true
        advanceTimeBy(POLL_MS)
        runCurrent()

        assertEquals(listOf(1_500_000L to 1_500_000L), queriedWindows)

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
