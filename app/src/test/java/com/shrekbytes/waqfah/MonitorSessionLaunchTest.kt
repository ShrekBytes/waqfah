package com.shrekbytes.waqfah

import com.shrekbytes.waqfah.detection.launchMonitorSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

// The session loop's failure owner, extracted from AppMonitorService's launch
// site (2026-09-06 health audit, P2-1) so the JVM can pin it without
// constructing the service. A session that dies must reach its owner — the
// service logs and stops itself, so a live notification can never outlive
// detection — while cancellation is the host's own teardown, not a death, and
// stays out of the owner's way.
@OptIn(ExperimentalCoroutinesApi::class)
class MonitorSessionLaunchTest {

    @Test
    fun `a failed session reaches its failure owner with the failure`() = runTest {
        val failure = IllegalStateException("the session died")
        var ownerNotifiedWith: Throwable? = null

        val job = launchMonitorSession(
            session = { throw failure },
            onFailure = { ownerNotifiedWith = it },
        )
        job.join()

        assertSame(failure, ownerNotifiedWith)
    }

    // onDestroy cancels the service scope; that teardown reaches the loop as a
    // CancellationException. It must not be mistaken for a dead session, and
    // must propagate instead of being swallowed by the launch wrapper. The
    // session is suspended inside run() before the cancel — the live-service
    // shape — so the catch actually sees the cancellation.
    @Test
    fun `a cancelled session does not reach its failure owner`() = runTest {
        var ownerNotified = false

        val job = launchMonitorSession(
            session = { awaitCancellation() },
            onFailure = { ownerNotified = true },
        )
        runCurrent()
        job.cancel()
        job.join()

        assertTrue(job.isCancelled)
        assertFalse(ownerNotified)
    }

    // A session that returns on its own ended deliberately (the permission
    // revocation path already asks the service to stop itself); the failure
    // owner is for deaths, not for every ending.
    @Test
    fun `a session that ends on its own does not reach its failure owner`() = runTest {
        var ownerNotified = false

        val job = launchMonitorSession(
            session = {},
            onFailure = { ownerNotified = true },
        )
        job.join()

        assertTrue(job.isCompleted)
        assertFalse(ownerNotified)
    }
}
