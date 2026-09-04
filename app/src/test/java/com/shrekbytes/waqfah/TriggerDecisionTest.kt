package com.shrekbytes.waqfah

import com.shrekbytes.waqfah.detection.Reason
import com.shrekbytes.waqfah.detection.ResumedActivity
import com.shrekbytes.waqfah.detection.TriggerDecision
import com.shrekbytes.waqfah.detection.TriggerPrefs
import com.shrekbytes.waqfah.detection.Verdict
import com.shrekbytes.waqfah.data.monitoredapp.MonitoredAppMembership
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// The TriggerDecision module decides, for every resumed activity, whether it
// earns a trigger: a fresh open of a monitored app shows the interstitial;
// everything else — calls, picker-mediated entries, switch-backs, cooldowns,
// returns from the interstitial itself — is ignored with a reason.
//
// Tests go through the module's interface: the pure rule cores below, and
// scripted resumed-activity sequences (in the scenario sections) driven
// through onResumedActivity with controllable clocks.
class TriggerDecisionTest {

    // ---- Call-UI rule (package/class half; the audio half is a probe) ----

    // Getting this wrong one way shows the reading screen over an incoming or
    // ongoing call; getting it wrong the other way silently swallows a real
    // app open.
    @Test
    fun systemDialerPackages_areCallUi_regardlessOfClassName() {
        assertTrue(TriggerDecision.isKnownCallUi("com.android.dialer", null))
        assertTrue(TriggerDecision.isKnownCallUi("com.google.android.dialer", "com.google.android.dialer.SomeMainActivity"))
        assertTrue(TriggerDecision.isKnownCallUi("com.samsung.android.incallui", "AnythingAtAll"))
    }

    @Test
    fun messagingApps_ownCallScreen_isCallUi() {
        // Real-world examples of what these apps' call activities look like.
        assertTrue(TriggerDecision.isKnownCallUi("com.whatsapp", "com.whatsapp.voipcalling.VoipActivityV2"))
        assertTrue(TriggerDecision.isKnownCallUi("com.facebook.orca", "com.facebook.rtc.fbwebrtc.RtcCallActivity"))
        assertTrue(TriggerDecision.isKnownCallUi("org.telegram.messenger", "org.telegram.messenger.voip.VoIPActivity"))
        assertTrue(TriggerDecision.isKnownCallUi("org.thoughtcrime.securesms", "org.thoughtcrime.securesms.calls.WebRtcCallActivity"))
        assertTrue(TriggerDecision.isKnownCallUi("com.viber.voip", "com.viber.voip.phone.ViberOutgoingCallActivity"))
    }

    @Test
    fun matchIsCaseInsensitive() {
        assertTrue(TriggerDecision.isKnownCallUi("com.example.app", "com.example.app.INCOMINGCALLACTIVITY"))
    }

    @Test
    fun ordinaryAppScreens_areNotCallUi() {
        assertFalse(TriggerDecision.isKnownCallUi("com.whatsapp", "com.whatsapp.HomeActivity"))
        assertFalse(TriggerDecision.isKnownCallUi("com.instagram.android", "com.instagram.android.activity.MainTabActivity"))
        assertFalse(TriggerDecision.isKnownCallUi("com.android.chrome", "com.google.android.apps.chrome.Main"))
    }

    @Test
    fun nullClassName_onAnUnknownPackage_isNotCallUi() {
        assertFalse(TriggerDecision.isKnownCallUi("com.instagram.android", null))
    }

    // ---- Interstitial-return rule ----

    // Finishing TriggerActivity resumes the paused app underneath — an event
    // indistinguishable from a fresh open. Getting this wrong in one direction
    // traps users in interstitial loops; getting it wrong the other way
    // swallows legitimate opens that come right after using Waqfah itself.
    // That's why the match is on the TriggerActivity CLASS, not just the
    // package.
    @Test
    fun resumingFromTheInterstitial_isAnInterstitialReturn() {
        val ownPackage = "com.shrekbytes.waqfah"
        val triggerClass = "com.shrekbytes.waqfah.TriggerActivity"

        assertTrue(TriggerDecision.isReturnFromInterstitial(ownPackage, triggerClass))
    }

    @Test
    fun openingAfterUsingWaqfahItself_isAFreshOpen_notAnInterstitialReturn() {
        val ownPackage = "com.shrekbytes.waqfah"

        // Waqfah's main screen shares the package name — only the trigger
        // activity's class marks an interstitial return.
        assertFalse(TriggerDecision.isReturnFromInterstitial(ownPackage, "com.shrekbytes.waqfah.MainActivity"))
        assertFalse(TriggerDecision.isReturnFromInterstitial(ownPackage, null))
    }

    @Test
    fun comingFromAnyOtherApp_isAFreshOpen() {
        assertFalse(TriggerDecision.isReturnFromInterstitial("com.android.systemui", "com.shrekbytes.waqfah.TriggerActivity"))
        assertFalse(TriggerDecision.isReturnFromInterstitial("com.android.launcher3", null))
    }

    @Test
    fun noPreviousEvent_isNeverAnInterstitialReturn() {
        assertFalse(TriggerDecision.isReturnFromInterstitial(null, null))
    }

    // ---- Cooldown arithmetic ----

    private val now = 1_000_000_000L

    private fun within(triggerStamp: Long?, cooldownMinutes: Int = 30) =
        TriggerDecision.isWithinCooldown(triggerStamp, now, cooldownMinutes)

    @Test
    fun neverShown_neverCoolsDown() {
        assertFalse(within(null))
    }

    @Test
    fun insideWindow_coolsDown() {
        assertTrue(within(now - 1))
        assertTrue(within(now - 29 * 60_000L))
    }

    @Test
    fun windowBoundary_isExpired_exactlyAtLimit() {
        // elapsed == window means the cooldown has fully passed.
        assertFalse(within(now - 30 * 60_000L))
    }

    @Test
    fun pastWindow_isExpired() {
        assertFalse(within(now - 31 * 60_000L))
        assertFalse(within(now - 10 * 24 * 60 * 60_000L))
    }

    @Test
    fun clockRollback_countsAsExpired_neverPermanentlyCooling() {
        assertFalse(within(now + 5 * 60_000L))
    }

    @Test
    fun zeroCooldown_off_coolsNothingDown() {
        assertFalse(within(now - 1, cooldownMinutes = 0))
    }

    // ---- Scripted scenarios ----
    //
    // Fake clocks (monotonic elapsed for switch-back/call-grace, wall for the
    // persisted cooldown anchor) and fakes for the four impure dependencies.
    // `stamps` doubles as the last_shown_at store, so cooldown reads see
    // exactly what the engine stamped.

    private val A = "com.target.a"
    private val B = "com.target.b"
    private val L = "com.android.launcher3" // not monitored
    private val TRIGGER = ResumedActivity("com.shrekbytes.waqfah", "com.shrekbytes.waqfah.TriggerActivity")

    private var elapsedMs = 0L
    private var wallMs = 0L
    private var appActive = true
    private var audioInCall = false
    private val monitored = mutableSetOf(A, B)
    private val indirectClasses = mutableMapOf<String, Set<String>>()
    private val stamps = mutableListOf<Pair<String, Long>>()
    private val revisions = mutableMapOf<String, Long>()

    private fun engine(
        cooldownMinutes: Int = 0,
        claim: suspend (MonitoredAppMembership, Long) -> Boolean = { membership, triggeredAt ->
            val current = if (membership.packageName in monitored) {
                MonitoredAppMembership(
                    packageName = membership.packageName,
                    membershipId = membership.membershipId,
                    triggerStamp = stamps.lastOrNull { it.first == membership.packageName }?.second,
                    triggerRevision = revisions[membership.packageName] ?: 0L,
                )
            } else {
                null
            }
            if (current != membership) {
                false
            } else {
                stamps.add(membership.packageName to triggeredAt)
                revisions[membership.packageName] = membership.triggerRevision + 1
                true
            }
        },
    ) = TriggerDecision(
        isMonitored = { it in monitored },
        callAudioActive = { audioInCall },
        indirectEntryClasses = { indirectClasses[it] ?: emptySet() },
        prefs = { TriggerPrefs(appActive, cooldownMinutes) },
        monitoredMembership = { pkg ->
            if (pkg !in monitored) {
                null
            } else {
                MonitoredAppMembership(
                    packageName = pkg,
                    membershipId = "membership-$pkg",
                    triggerStamp = stamps.lastOrNull { it.first == pkg }?.second,
                    triggerRevision = revisions[pkg] ?: 0L,
                )
            }
        },
        claimTrigger = claim,
        nowElapsed = { elapsedMs },
        nowWall = { wallMs },
    )

    private fun TriggerDecision.resume(vararg events: ResumedActivity): List<Verdict> = runBlocking {
        events.map { onResumedActivity(it) }
    }

    private fun resumed(pkg: String, className: String? = "$pkg.MainActivity") = ResumedActivity(pkg, className)

    @Test
    fun freshOpenOfMonitoredApp_triggers_andStampsOnceAtTriggerTime() {
        wallMs = 1_000_000
        val verdicts = engine().resume(resumed(A))

        assertEquals(listOf<Verdict>(Verdict.Trigger(A)), verdicts)
        assertEquals(listOf(A to 1_000_000L), stamps)
    }

    // Regression (dd93b52): finishing the interstitial resumes the paused app
    // underneath — an event identical to a fresh open. If the reading session
    // outlasted the switch-back gap, that resume used to re-trigger, trapping
    // the user in a loop of interstitials. The interstitial-return rule must
    // win regardless of how long the session took, and the anchor must not be
    // re-stamped on dismissal.
    @Test
    fun dismissingTheInterstitial_afterTheSwitchBackGap_doesNotRetrigger() {
        wallMs = 1_000_000
        val e = engine()
        e.resume(resumed(A))
        e.resume(TRIGGER)
        elapsedMs += 60_000

        val verdict = e.resume(resumed(A)).single()

        assertEquals(Verdict.Ignore(Reason.INTERSTITIAL_RETURN), verdict)
        assertEquals(1, stamps.size)
    }

    // The interstitial-return rule comes before the cooldown rule: a
    // dismissal resuming the app inside its cooldown window is still an
    // interstitial return, not a cooldown hit.
    @Test
    fun dismissingTheInterstitial_insideTheCooldownWindow_isAnInterstitialReturn_notCooldown() {
        val e = engine(cooldownMinutes = 30)
        e.resume(resumed(A))
        e.resume(TRIGGER)

        val verdict = e.resume(resumed(A)).single()

        assertEquals(Verdict.Ignore(Reason.INTERSTITIAL_RETURN), verdict)
    }

    // Regression (0d4b8f7): last_shown_at is stamped once, at trigger time,
    // regardless of how the interstitial is later dismissed — and a fresh
    // open after the cooldown lapses triggers and stamps again at ITS trigger
    // time.
    @Test
    fun cooldownAnchor_isStampedAtTriggerTime_andExpiryLetsThroughTheNextFreshOpen() {
        val e = engine(cooldownMinutes = 30)
        e.resume(resumed(A))
        e.resume(TRIGGER)
        elapsedMs += 60_000
        e.resume(resumed(L))
        wallMs += 5 * 60_000

        assertEquals(Verdict.Ignore(Reason.COOLDOWN), e.resume(resumed(A)).single())

        elapsedMs += 60_000
        e.resume(resumed(L))
        wallMs += 31 * 60_000
        elapsedMs += 31 * 60_000

        assertEquals(Verdict.Trigger(A), e.resume(resumed(A)).single())
        assertEquals(listOf(A to 0L, A to 31 * 60_000L + 5 * 60_000L), stamps)
    }

    @Test
    fun failedTriggerClaim_isIgnored_withoutEmittingATrigger() {
        val e = engine(claim = { _, _ -> false })

        assertEquals(Verdict.Ignore(Reason.TRIGGER_CLAIM_REJECTED), e.resume(resumed(A)).single())
        assertTrue(stamps.isEmpty())
    }

    @Test
    fun missingMonitoredMembership_isIgnored_withoutAttemptingAClaim() {
        var claimAttempts = 0
        val e = TriggerDecision(
            isMonitored = { true },
            callAudioActive = { false },
            indirectEntryClasses = { emptySet() },
            prefs = { TriggerPrefs(true, 0) },
            monitoredMembership = { null },
            claimTrigger = { _, _ -> claimAttempts++; true },
            nowElapsed = { 0L },
            nowWall = { 0L },
        )

        assertEquals(Verdict.Ignore(Reason.TRIGGER_CLAIM_REJECTED), e.resume(resumed(A)).single())
        assertEquals(0, claimAttempts)
    }

    @Test
    fun concurrentTriggerClaims_onlyOneDecisionEmitsATrigger() = runBlocking {
        val lock = Any()
        var sharedStamp: Long? = null
        var sharedRevision = 0L
        val claim: suspend (MonitoredAppMembership, Long) -> Boolean = { membership, triggeredAt ->
            synchronized(lock) {
                if (membership.triggerStamp != sharedStamp || membership.triggerRevision != sharedRevision) {
                    false
                } else {
                    sharedStamp = triggeredAt
                    sharedRevision++
                    true
                }
            }
        }
        val first = engine(claim = claim)
        val second = engine(claim = claim)

        val verdicts = coroutineScope {
            listOf(
                async { first.onResumedActivity(resumed(A)) },
                async { second.onResumedActivity(resumed(A)) },
            ).map { it.await() }
        }

        assertEquals(1, verdicts.count { it is Verdict.Trigger })
        assertEquals(1, verdicts.count { it == Verdict.Ignore(Reason.TRIGGER_CLAIM_REJECTED) })
        assertEquals(0L, sharedStamp)
        assertEquals(1L, sharedRevision)
    }

    // A call never counts as leaving the foreground: the app the call
    // interrupted is still "current" when the call hands the foreground back.
    @Test
    fun callScreen_doesNotCountAsLeavingTheForeground() {
        val e = engine()
        e.resume(resumed(A))

        val verdicts = e.resume(resumed("com.google.android.dialer"), resumed(A))

        assertEquals(Verdict.Ignore(Reason.CALL), verdicts.first())
        assertEquals(Verdict.Ignore(Reason.SAME_FOREGROUND), verdicts.last())
        assertEquals(1, stamps.size)
    }

    // Regression (574651d): for CALL_GRACE_MS after a call ends, the app the
    // call interrupted stays quiet however many hops the return takes. This
    // is the case the rule exists for: the interrupted app's exit has already
    // aged out of the switch-back map, so only the grace window keeps the
    // landing from reading as a fresh open.
    @Test
    fun callGrace_coversTheInterruptedApp_whenTheSwitchBackWindowHasAlreadyExpired() {
        val e = engine()
        e.resume(resumed(A))
        e.resume(resumed(L))
        elapsedMs += 10 * 60_000 // A's exit ages out of the switch-back map
        e.resume(resumed("com.google.android.dialer")) // call interrupts the launcher

        // Call ends, landing on the interrupted app: within the grace window,
        // and its old exit is long past the switch-back window — only the
        // grace rule keeps this from reading as a fresh open.
        assertEquals(Verdict.Ignore(Reason.CALL_GRACE), e.resume(resumed(A)).single())
        assertEquals(1, stamps.size)

        elapsedMs += 16_000 // grace window lapses
        e.resume(resumed(L))
        elapsedMs += 60_000

        assertEquals(Verdict.Trigger(A), e.resume(resumed(A)).single())
        assertEquals(2, stamps.size)
    }

    // The monitor gate's reset (screen off, monitoring off) must sever the
    // picker pairing: a chooser flash from before the pause is never paired
    // against a post-wake resume.
    @Test
    fun reset_clearsThePickerPairing() {
        val chooser = ResumedActivity("android", "com.android.internal.app.ResolverActivity")
        val e = engine()
        e.resume(resumed(A), chooser)
        e.reset()
        elapsedMs += 60_000 // past the switch-back window, so only the pairing matters

        val verdict = e.resume(resumed(A)).single()

        assertEquals(Verdict.Trigger(A), verdict)
    }

    // And it must sever the call context: the first resume after a pause is
    // not treated as the tail end of a call that started before it.
    @Test
    fun reset_clearsTheCallContext_soTheNextOpenIsFresh() {
        val e = engine()
        e.resume(resumed(A))
        e.resume(resumed("com.google.android.dialer"))
        e.reset()

        val verdict = e.resume(resumed(B)).single()

        assertEquals(Verdict.Trigger(B), verdict)
    }

    // A quick switch-back is the same session: a glance at another app and
    // back inside the gap never re-shows, no matter what the cooldown says.
    @Test
    fun switchBack_withinTheGap_doesNotTrigger_evenWithCooldownOff() {
        val e = engine()
        e.resume(resumed(A))
        e.resume(resumed(L))
        elapsedMs += TriggerDecision.SWITCH_BACK_GAP_MS - 1

        assertEquals(Verdict.Ignore(Reason.SWITCH_BACK), e.resume(resumed(A)).single())
    }

    @Test
    fun switchBack_pastTheGap_isAFreshOpen() {
        val e = engine()
        e.resume(resumed(A))
        e.resume(resumed(L))
        elapsedMs += TriggerDecision.SWITCH_BACK_GAP_MS + 1

        assertEquals(Verdict.Trigger(A), e.resume(resumed(A)).single())
    }

    // Cooldown Off (0) means every fresh open triggers; the switch-back rule
    // is what keeps same-session returns quiet.
    @Test
    fun cooldownOff_everyFreshOpenTriggers() {
        val e = engine(cooldownMinutes = 0)
        e.resume(resumed(A))
        e.resume(resumed(L))
        elapsedMs += 60_000
        assertEquals(Verdict.Trigger(A), e.resume(resumed(A)).single())
        e.resume(resumed(L))
        elapsedMs += 60_000

        assertEquals(Verdict.Trigger(B), e.resume(resumed(B)).single())
        assertEquals(3, stamps.size)
    }

    // Detection inactive never triggers, whatever else the event looks like.
    @Test
    fun inactiveDetection_neverTriggers() {
        appActive = false

        assertEquals(Verdict.Ignore(Reason.INACTIVE), engine().resume(resumed(A)).single())
    }

    // Share sheets and "Open with" dialogs surface a worker activity of the
    // target app right after a system chooser — pausing there would make
    // sharing and forwarding painful without adding value.
    @Test
    fun entryThroughAChooser_isAnIndirectEntry() {
        val chooser = ResumedActivity("android", "com.android.internal.app.ResolverActivity")
        val e = engine()
        e.resume(resumed(A))
        e.resume(resumed(L))
        elapsedMs += 60_000
        e.resume(chooser)

        val verdict = e.resume(ResumedActivity(A, "com.target.a.ShareActivity")).single()

        assertEquals(Verdict.Ignore(Reason.INDIRECT_ENTRY), verdict)
    }

    // Alternate entry points discovered by probing the package's SEND/VIEW
    // intent filters are indirect even without a chooser flash in between.
    @Test
    fun entryThroughAProbedShareTarget_isAnIndirectEntry() {
        indirectClasses[A] = setOf("com.target.a.ShareViewer")
        val e = engine()
        e.resume(resumed(A))
        e.resume(resumed(L))
        elapsedMs += 60_000

        val verdict = e.resume(ResumedActivity(A, "com.target.a.ShareViewer")).single()

        assertEquals(Verdict.Ignore(Reason.INDIRECT_ENTRY), verdict)
    }

}
