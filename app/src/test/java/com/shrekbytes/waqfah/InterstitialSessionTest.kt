package com.shrekbytes.waqfah

import android.content.Intent
import com.shrekbytes.waqfah.detection.InterstitialSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// The InterstitialSession module owns how the interstitial presents itself
// and recovers when the monitored app covers it: the flag set for both launch
// paths, and the once-only re-assert verdict. Tests go through the
// module's interface with the foreground probe as a fake.
class InterstitialSessionTest {

    // ---- Launch flags ----

    // The service's launch: no CLEAR_TOP — there is nothing to clear on a
    // first presentation.
    @Test
    fun serviceLaunch_flags() {
        assertEquals(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                Intent.FLAG_ACTIVITY_NO_ANIMATION,
            InterstitialSession.launchFlags(reassert = false),
        )
    }

    @Test
    fun reassert_flags_addClearTop() {
        assertEquals(
            InterstitialSession.launchFlags(reassert = false) or Intent.FLAG_ACTIVITY_CLEAR_TOP,
            InterstitialSession.launchFlags(reassert = true),
        )
    }

    // ---- Re-assert verdict ----

    // The buried case: the target app raised itself back over the
    // interstitial without the foreground really changing.
    @Test
    fun buriedByTheTargetApp_reasserts() {
        val verdict = InterstitialSession.shouldReassert(
            reassertUsed = false,
            finishing = false,
            screenOn = true,
        ) { true }

        assertTrue(verdict)
    }

    // Exactly once per trigger — the budget survives recreation because it
    // lives in InterstitialSession (per trigger, not per activity instance),
    // so neither a recreated nor a CLEAR_TOP-replaced instance can fire the
    // re-assert a second time.
    @Test
    fun reassertUsed_neverReasserts() {
        val verdict = InterstitialSession.shouldReassert(
            reassertUsed = true,
            finishing = false,
            screenOn = true,
        ) { true }

        assertFalse(verdict)
    }

    @Test
    fun dismissing_neverReasserts() {
        val verdict = InterstitialSession.shouldReassert(
            reassertUsed = false,
            finishing = true,
            screenOn = true,
        ) { true }

        assertFalse(verdict)
    }

    // Screen-off also stops the activity without changing the foreground —
    // never relaunch into a dark screen.
    @Test
    fun screenOff_neverReasserts() {
        val verdict = InterstitialSession.shouldReassert(
            reassertUsed = false,
            finishing = false,
            screenOn = false,
        ) { true }

        assertFalse(verdict)
    }

    // The user went elsewhere — that's a real leave, not a burial.
    @Test
    fun triggeredAppNoLongerForeground_neverReasserts() {
        val verdict = InterstitialSession.shouldReassert(
            reassertUsed = false,
            finishing = false,
            screenOn = true,
        ) { false }

        assertFalse(verdict)
    }

    // The foreground probe costs a UsageStatsManager query — it must stay
    // silent whenever a cheaper guard already refuses.
    @Test
    fun foregroundProbe_runsOnlyAfterTheCheapGuardsPass() {
        var probeCalls = 0

        InterstitialSession.shouldReassert(reassertUsed = true, finishing = false, screenOn = true) { probeCalls++; true }
        InterstitialSession.shouldReassert(reassertUsed = false, finishing = true, screenOn = true) { probeCalls++; true }
        InterstitialSession.shouldReassert(reassertUsed = false, finishing = false, screenOn = false) { probeCalls++; true }

        assertEquals(0, probeCalls)
    }

    // ---- Re-assert budget (process-lifetime, per trigger) ----

    // The budget must survive the re-assert's own CLEAR_TOP launch: that
    // launch finishes the buried instance and creates a fresh one whose
    // savedInstanceState is null, so a per-instance flag would re-arm on
    // every round and a self-raising app could chain re-asserts forever.
    @Test
    fun reassertBudget_onePerTrigger_resetOnlyByANewTrigger() {
        InterstitialSession.onTriggerLaunched()

        assertTrue(
            InterstitialSession.shouldReassert(
                reassertUsed = InterstitialSession.reassertUsed,
                finishing = false,
                screenOn = true,
            ) { true },
        )
        InterstitialSession.markReassertUsed()

        // A fresh activity instance ("recreated" or CLEAR_TOP-replaced) reads
        // the same process-lifetime budget — no second round for this trigger.
        assertFalse(
            InterstitialSession.shouldReassert(
                reassertUsed = InterstitialSession.reassertUsed,
                finishing = false,
                screenOn = true,
            ) { true },
        )

        // Only the service's next trigger launch re-arms it.
        InterstitialSession.onTriggerLaunched()
        assertTrue(
            InterstitialSession.shouldReassert(
                reassertUsed = InterstitialSession.reassertUsed,
                finishing = false,
                screenOn = true,
            ) { true },
        )
    }
}
