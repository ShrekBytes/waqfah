package com.shrekbytes.waqfah.detection

import com.shrekbytes.waqfah.BuildConfig
import com.shrekbytes.waqfah.TriggerActivity
import com.shrekbytes.waqfah.data.monitoredapp.MonitoredAppMembership

// One foreground observation from UsageStatsManager. The activity class is
// what tells a fresh open apart from an indirect entry (a picker, a share
// sheet, a file viewer, a link grabber) — see TriggerDecision.isIndirectEntry.
data class ResumedActivity(val packageName: String, val className: String?)

// The only preference facts the trigger decision reads. A snapshot instead of
// the full UserPreferences keeps the decision's interface minimal; the
// adapter wires it from SettingsRepository at read time — the read itself is
// deliberately late, after the cheap rules have run.
data class TriggerPrefs(val appActive: Boolean, val cooldownMinutes: Int)

// Why a resumed activity did not earn a trigger. The service logs from this;
// tests assert on it.
enum class Reason {
    SAME_FOREGROUND,
    CALL,
    CALL_GRACE,
    INTERSTITIAL_RETURN,
    INDIRECT_ENTRY,
    TRIGGER_CLAIM_REJECTED,
    NOT_MONITORED,
    INACTIVE,
    SWITCH_BACK,
    COOLDOWN,
}

// The outcome for one resumed activity: trigger, or ignore with a reason.
sealed interface Verdict {
    data class Trigger(val packageName: String) : Verdict
    data class Ignore(val reason: Reason) : Verdict
}

// Decides whether a resumed activity earns a trigger — a fresh open of a
// monitored app shows the interstitial; everything else (calls, indirect
// entries, switch-backs, cooldowns, returns from the interstitial itself) is
// ignored with a reason.
//
// Trigger rules:
//  - At most ONE pause per continuous stay in the foreground of an app, no
//    matter how many ACTIVITY_RESUMED events that stay emits (splash screens,
//    notification trampolines and multi-activity apps like Messenger emit the
//    event several times per launch). Leaving for ANY other app re-arms it;
//    the persisted trigger stamp and cooldown then decide whether the next
//    open is allowed through.
//  - Returning from Waqfah's own interstitial never re-triggers: finishing
//    TriggerActivity resumes the app underneath, an event identical to a
//    fresh open. Matched by the TriggerActivity class name so an open that
//    comes straight from Waqfah's own main screen still counts as fresh.
//  - Calls are never paused, and never even count as leaving the foreground:
//    a messaging app's own call screen, a system dialer/incall-UI package, or
//    audio routed into a call (see isCallForeground) all count. Call screens
//    aren't launched through a public intent-filter the way indirect entries
//    are, so this can't be discovered by probing like indirectEntryClasses —
//    it's a maintained heuristic instead (see isKnownCallUi). currentForeground
//    is left untouched for the whole call so whatever it interrupted is still
//    "current" when it hands the foreground back. For CALL_GRACE_MS after a
//    call ends, returning to either that interrupted app or the calling app's
//    own screen also stays quiet, however many hops the return takes (a
//    launcher flash between hanging up and landing is common on some OEMs).
//  - Quick switch-backs never trigger: returning to an app within
//    SWITCH_BACK_GAP_MS of it leaving the foreground counts as the same
//    session no matter what the cooldown says, so a glance at another
//    app and back doesn't re-show — including right as a short cooldown
//    happens to elapse mid-glance.
//
// The module owns ALL trigger-rule state: the foreground tracker, the
// switch-back map, call detection and the post-call grace window, and the
// pairing of consecutive resumed activities. The caller (AppMonitorService)
// feeds it one event at a time and acts on the verdict; it holds no rule
// state of its own. reset() severs remembered context when detection pauses
// (the monitor gate closed): nothing from before a pause may pair against,
// or extend a grace window into, a post-wake resume.
//
// Everything impure arrives through the constructor as a function: the audio
// probe, the indirect-entry class probe (PackageManager, cached by the
// caller), the preference snapshot, the monitored-app membership snapshot and
// trigger claim, and two clocks. Monotonic elapsed time drives the switch-back
// and call-grace windows; wall time drives the persisted cooldown anchor, which
// must survive reboots.
class TriggerDecision(
    private val isMonitored: (String) -> Boolean,
    private val callAudioActive: () -> Boolean,
    private val indirectEntryClasses: (String) -> Set<String>,
    private val prefs: suspend () -> TriggerPrefs,
    private val monitoredMembership: suspend (String) -> MonitoredAppMembership?,
    private val claimTrigger: suspend (MonitoredAppMembership, Long) -> Boolean,
    private val nowElapsed: () -> Long,
    private val nowWall: () -> Long,
) {

    // Most recent resumed activity, kept across calls so consecutive events
    // can be paired — that pairing is how picker-mediated entries are
    // recognized even when the chooser flash is shorter than one poll.
    private var lastResumedActivity: ResumedActivity? = null

    // The package we currently believe owns the foreground, plus when each
    // package last left it — the switch-back rule compares against this to
    // keep quick switch-backs from re-triggering. Deliberately NOT updated
    // while a call owns the foreground (see isCallForeground) — the call
    // isn't "leaving" whatever it interrupted.
    private var currentForeground: String? = null
    private val lastLeftForegroundAt = mutableMapOf<String, Long>()

    // True while a call currently owns the foreground. Used to (a) tell the
    // very next non-call resume that it's a return from a call, and (b) know
    // when to arm the post-call grace window below.
    private var inCall = false

    // Packages exempt from triggering for CALL_GRACE_MS after a call ends:
    // whatever was in the foreground when the call took over, every call-UI
    // package seen while it was ongoing, and wherever it lands afterward (in
    // case the return trip itself has more than one hop). Cleared whenever a
    // fresh call starts, the grace window lapses, or detection resets.
    private val callGracePackages = mutableSetOf<String>()
    private var callGraceEndsAt: Long? = null

    suspend fun onResumedActivity(current: ResumedActivity): Verdict {
        val verdict = decide(lastResumedActivity, current)
        lastResumedActivity = current
        return verdict
    }

    // Detection is pausing (screen off, monitoring off): sever the
    // picker-pairing and call-grace context so nothing from before the pause
    // influences a post-wake resume.
    fun reset() {
        lastResumedActivity = null
        inCall = false
        callGracePackages.clear()
        callGraceEndsAt = null
    }

    private suspend fun decide(previous: ResumedActivity?, current: ResumedActivity): Verdict {
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
            }
            callGracePackages.add(candidate)
            return Verdict.Ignore(Reason.CALL)
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
            callGraceEndsAt = nowElapsed() + CALL_GRACE_MS
        }

        if (candidate == currentForeground) return Verdict.Ignore(Reason.SAME_FOREGROUND)

        // Note when the outgoing package left the foreground — used by the
        // switch-back rule below to tell a fresh open apart from a quick
        // switch-back.
        currentForeground?.let { left ->
            val now = nowElapsed()
            lastLeftForegroundAt[left] = now
            // Bound memory: only exits within SWITCH_BACK_GAP_MS matter, so
            // expired entries are swept BY AGE. A size-cap eviction here would
            // be wrong — re-exiting a package updates its value without
            // refreshing its LinkedHashMap position, so "oldest inserted"
            // isn't necessarily the oldest exit.
            lastLeftForegroundAt.entries.removeAll { (_, exitedAt) -> now - exitedAt >= SWITCH_BACK_GAP_MS }
        }
        currentForeground = candidate

        if (!isMonitored(candidate)) return Verdict.Ignore(Reason.NOT_MONITORED)

        // The dismissal flow: TriggerActivity finishes, the paused app
        // underneath resumes. Without this guard that resume re-triggers
        // whenever the reading session outlasted SWITCH_BACK_GAP_MS —
        // trapping the user in a loop of interstitials.
        if (isReturnFromInterstitial(previous?.packageName, previous?.className)) {
            return Verdict.Ignore(Reason.INTERSTITIAL_RETURN)
        }

        // Landing here right after a call — on either the app it interrupted
        // or the calling app's own screen, however many hops it took — is
        // never a fresh, user-initiated open.
        if (isWithinCallGrace(candidate)) return Verdict.Ignore(Reason.CALL_GRACE)

        // Share sheets, "Open with" dialogs and download-grabbers surface a
        // worker activity of the target app, not a user-initiated open —
        // pausing there makes sharing/forwarding painful without adding value.
        if (isIndirectEntry(previous, current)) return Verdict.Ignore(Reason.INDIRECT_ENTRY)

        val triggerPrefs = prefs()
        if (!triggerPrefs.appActive) return Verdict.Ignore(Reason.INACTIVE)

        // A quick switch-back — glancing at another app and returning within
        // SWITCH_BACK_GAP_MS — is the same session and never triggers, no
        // matter what the cooldown says. This used to only apply
        // when the cooldown was Off; checking it here too stops a switch
        // timed right as a short cooldown elapses from triggering again
        // immediately.
        val leftAt = lastLeftForegroundAt[candidate]
        if (leftAt != null && nowElapsed() - leftAt < SWITCH_BACK_GAP_MS) return Verdict.Ignore(Reason.SWITCH_BACK)

        val membership = monitoredMembership(candidate)
            ?: return Verdict.Ignore(Reason.TRIGGER_CLAIM_REJECTED)
        val triggerTime = nowWall()
        if (triggerPrefs.cooldownMinutes > 0 &&
            isWithinCooldown(membership.triggerStamp, triggerTime, triggerPrefs.cooldownMinutes)
        ) {
            return Verdict.Ignore(Reason.COOLDOWN)
        }

        // The anchor is stamped here, at trigger time, exactly once — never
        // on dismissal — so the cooldown always runs from the moment the
        // interstitial was shown.
        if (!claimTrigger(membership, triggerTime)) {
            return Verdict.Ignore(Reason.TRIGGER_CLAIM_REJECTED)
        }
        return Verdict.Trigger(candidate)
    }

    // True when [event] is a call's own UI rather than a normal app screen —
    // matched against the maintained package/class heuristic first (cheap,
    // and catches an incoming call while it's still only ringing, before any
    // audio is routed), then against the device's audio routing probe
    // (catches any other calling app once its call actually connects).
    //
    // The audio probe is deliberately a plain level check, not "did the mode
    // just change": while it's active, EVERY foreground change reads as a
    // call — including switching between other apps with the call minimized
    // to picture-in-picture — so nothing triggers anywhere until the call's
    // audio actually ends. That is the intended behavior, not a gap: someone
    // mid-call doesn't want a reading pause competing for their attention
    // just because they alt-tabbed while still on the line.
    private fun isCallForeground(event: ResumedActivity): Boolean {
        if (isKnownCallUi(event.packageName, event.className)) return true
        return callAudioActive()
    }

    // True when [packageName] is still within CALL_GRACE_MS of a call ending
    // and was one of the packages involved in that call. Expires itself
    // lazily on the first check after the window lapses — consistent with
    // lastLeftForegroundAt's age-based sweep elsewhere in this class.
    private fun isWithinCallGrace(packageName: String): Boolean {
        val endsAt = callGraceEndsAt ?: return false
        if (nowElapsed() >= endsAt) {
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

    companion object {
        // Quick switch-back window: returning to an app within this long of
        // it leaving the foreground is treated as the same session (no
        // re-trigger), regardless of the cooldown. Public usage APIs
        // can't tell a launcher tap from a recents-resume, so this timing
        // rule approximates "fresh open" everywhere, not just when the
        // cooldown is Off.
        internal const val SWITCH_BACK_GAP_MS = 45_000L

        // Post-call grace: for this long after a call ends, the app it
        // interrupted and the calling app's own screen are both treated as
        // "same session, not a fresh open" — shorter than SWITCH_BACK_GAP_MS
        // on purpose, since a call is a much stronger, more legible signal
        // that nothing was deliberately closed than an ordinary app switch.
        internal const val CALL_GRACE_MS = 15_000L

        // Stock + common OEM in-call UI / dialer packages. A call routed
        // through any of these briefly owns the foreground on top of, or
        // instead of, the app the user was actually in — never a fresh open
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

        // Pure core of the call-UI rule — the package/class half only; the
        // audio-routing half arrives through the callAudioActive probe.
        internal fun isKnownCallUi(packageName: String, className: String?): Boolean {
            if (packageName in SYSTEM_CALL_PACKAGES) return true
            return className != null && CALL_CLASS_KEYWORDS.any { className.contains(it, ignoreCase = true) }
        }

        // Pure core of the interstitial-return rule: did
        // [previousPackage]/[previousClass] resume Waqfah's TriggerActivity?
        // Class match is what separates "returned from the interstitial"
        // from "opened after using Waqfah itself".
        internal fun isReturnFromInterstitial(previousPackage: String?, previousClass: String?): Boolean =
            previousPackage == BuildConfig.APPLICATION_ID &&
                previousClass == TriggerActivity::class.java.name

        // Pure core of the cooldown rule. Negative elapsed (clock rolled
        // back / NTP resync) counts as expired, never as permanently cooling
        // down. A 0-minute cooldown (Off) never cools down.
        internal fun isWithinCooldown(triggerStamp: Long?, now: Long, cooldownMinutes: Int): Boolean {
            triggerStamp ?: return false
            val elapsedMs = now - triggerStamp
            return elapsedMs >= 0 && elapsedMs < cooldownMinutes * 60_000L
        }
    }
}
