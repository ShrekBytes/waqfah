package com.shrekbytes.waqfah

import com.shrekbytes.waqfah.detection.MonitorSupervisor
import com.shrekbytes.waqfah.detection.MonitorSupervisor.Reason
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

// Tests the MonitorSupervisor at its interface: one sync(reason) per external
// event, an outcome out, and the start/stop gestures recorded by the fakes.
// The matrix pins the may-the-monitor-run rule — persisted appActive AND the
// required permissions — including the asymmetry that only the toggle may
// stop, so resuming the app can never resurrect a monitor the user turned off.
class MonitorSupervisorTest {

    private var appActive = true
    private var permissionsGranted = true
    private var starts = 0
    private var stops = 0

    private fun supervisor() = MonitorSupervisor(
        appActive = { appActive },
        hasPermissions = { permissionsGranted },
        startMonitor = { starts++ },
        stopMonitor = { stops++ },
    )

    // Stopping ignores permissions on purpose: the user turned the monitor
    // off, so it comes down whether or not the probes would allow running.
    @Test
    fun `toggling off stops the monitor unconditionally`() = runTest {
        appActive = false

        val outcome = supervisor().sync(Reason.TOGGLE)

        assertEquals(1, stops)
        assertEquals(0, starts)
        assertEquals(MonitorSupervisor.Outcome.Stopped, outcome)
    }

    @Test
    fun `toggling on with permissions starts the monitor`() = runTest {
        val outcome = supervisor().sync(Reason.TOGGLE)

        assertEquals(1, starts)
        assertEquals(0, stops)
        assertEquals(MonitorSupervisor.Outcome.Started, outcome)
    }

    @Test
    fun `toggling on without permissions keeps the monitor down`() = runTest {
        permissionsGranted = false

        val outcome = supervisor().sync(Reason.TOGGLE)

        assertEquals(0, starts)
        assertEquals(
            MonitorSupervisor.Outcome.KeptDown(MonitorSupervisor.Outcome.BlockReason.MISSING_PERMISSIONS),
            outcome,
        )
    }

    // The invariant the old MainActivity.onResume comment could only state:
    // resuming the app must not resurrect a monitor the user explicitly
    // stopped — not start it, and not stop it either (resume owns nothing).
    @Test
    fun `resuming a toggled off monitor leaves it exactly as it was`() = runTest {
        appActive = false

        val outcome = supervisor().sync(Reason.APP_RESUME)

        assertEquals(0, starts)
        assertEquals(0, stops)
        assertEquals(
            MonitorSupervisor.Outcome.KeptDown(MonitorSupervisor.Outcome.BlockReason.TOGGLED_OFF),
            outcome,
        )
    }

    @Test
    fun `resuming without permissions keeps the monitor down`() = runTest {
        permissionsGranted = false

        val outcome = supervisor().sync(Reason.APP_RESUME)

        assertEquals(0, starts)
        assertEquals(
            MonitorSupervisor.Outcome.KeptDown(MonitorSupervisor.Outcome.BlockReason.MISSING_PERMISSIONS),
            outcome,
        )
    }

    @Test
    fun `resuming an active monitor with permissions starts it`() = runTest {
        val outcome = supervisor().sync(Reason.APP_RESUME)

        assertEquals(1, starts)
        assertEquals(0, stops)
        assertEquals(MonitorSupervisor.Outcome.Started, outcome)
    }

    // When both blockers apply, the outcome names permissions — boot's
    // diagnostics have always reported the missing probe first.
    @Test
    fun `boot without permissions keeps the monitor down even when toggled off`() = runTest {
        appActive = false
        permissionsGranted = false

        val outcome = supervisor().sync(Reason.BOOT)

        assertEquals(0, starts)
        assertEquals(
            MonitorSupervisor.Outcome.KeptDown(MonitorSupervisor.Outcome.BlockReason.MISSING_PERMISSIONS),
            outcome,
        )
    }

    @Test
    fun `boot with a toggled off monitor keeps the monitor down`() = runTest {
        appActive = false

        val outcome = supervisor().sync(Reason.BOOT)

        assertEquals(0, starts)
        assertEquals(0, stops)
        assertEquals(
            MonitorSupervisor.Outcome.KeptDown(MonitorSupervisor.Outcome.BlockReason.TOGGLED_OFF),
            outcome,
        )
    }

    @Test
    fun `boot with everything in place starts the monitor`() = runTest {
        val outcome = supervisor().sync(Reason.BOOT)

        assertEquals(1, starts)
        assertEquals(0, stops)
        assertEquals(MonitorSupervisor.Outcome.Started, outcome)
    }
}
