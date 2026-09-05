package com.shrekbytes.waqfah.detection

import android.content.Intent

// The interstitial's machine (see CONTEXT.md): everything about how the
// interstitial presents itself over the monitored app and recovers when the
// monitored app covers it. AppMonitorService (launches on a Trigger verdict) and
// TriggerActivity (hosts the interstitial) are its Android adapters; the
// decision logic — which flags to launch with, whether a buried interstitial
// may re-assert itself — lives here so it can be pinned by JVM tests.
//
// The dismissal path is deliberately NOT part of the machine: it is a fade,
// then finish(), and the activity owns it.
object InterstitialSession {

    // Flags for presenting the interstitial, on both launch paths — the
    // service's launch on a Trigger verdict, and the activity's once-only
    // re-assert when the monitored app covers it.
    //
    // NEW_TASK is required when starting from a Service context.
    // EXCLUDE_FROM_RECENTS mirrors the manifest attribute — some OEM recents
    // screens only honor one or the other.
    // NO_ANIMATION drops the ROM's default activity slide; the interstitial
    // defines its own calm fade instead (see TriggerActivity).
    // CLEAR_TOP (re-assert only) finishes the buried instance(s) of the
    // activity in the task instead of stacking another one on top, so
    // repeated self-raising apps can't pile up stale overlays.
    fun launchFlags(reassert: Boolean): Int =
        Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
            Intent.FLAG_ACTIVITY_NO_ANIMATION or
            if (reassert) Intent.FLAG_ACTIVITY_CLEAR_TOP else 0

    // The re-assert verdict. While the interstitial shows, the monitored app
    // can raise its own task back over it mid-launch — splash chains, VPN
    // consent dialogs (1.1.1.1 does this) — which looks to the user like
    // Waqfah opened the app and then nothing happened. When that happens the
    // activity loses visibility without the foreground changing, so the
    // interstitial re-asserts itself, at most ONCE, and only when:
    //  - this instance hasn't re-asserted already (a stubborn app must not
    //    be able to trap the user in a loop of interstitials; the flag
    //    survives recreation — Bundle persistence is the activity's job),
    //  - the interstitial isn't already dismissing,
    //  - the screen is on — screen-off also stops the activity without
    //    changing the foreground, and we never relaunch into a dark screen,
    //  - the triggered app is STILL the latest foreground — i.e. the burial
    //    is the app covering us, not the user having gone elsewhere.
    //
    // [triggeredAppIsLatestForeground] is a probe rather than a value: it
    // costs a UsageStatsManager query, so it must only run once the cheap
    // guards have passed — the same late-read discipline TriggerDecision
    // applies to its prefs probe.
    fun shouldReassert(
        reassertUsed: Boolean,
        finishing: Boolean,
        screenOn: Boolean,
        triggeredAppIsLatestForeground: () -> Boolean,
    ): Boolean = !reassertUsed && !finishing && screenOn && triggeredAppIsLatestForeground()
}
