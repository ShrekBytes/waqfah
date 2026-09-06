# Correctness audit — whole-codebase pass 2 (fresh-eyes re-run)

- **Date:** 2026-09-06 · **Audited code:** `0420241` (the working tree is unchanged since — later commits touch audit docs only) · **Spec:** [issue #9](https://github.com/ShrekBytes/waqfah/issues/9)
- **Method:** an independent second correctness pass over all **87** production source files (one method correction for the record: pass 1's report says 88; the count under `app/src/main` is 87). Read-and-report only — the committed tree is untouched by this pass; two throwaway probe tests were run and deleted, their output quoted below.
- **Baseline:** `./gradlew :app:testDebugUnitTest` green (18 test classes) before the probes, and re-run green after deleting them.
- **Companion report:** [pass 1](2026-09-06-correctness.md) is kept side by side with this one so the two passes can be diffed.

## Relationship to pass 1

This pass was run *after* reading pass 1's report — full independence from it is impossible, so the discipline applied instead: **every finding below was re-derived from the code and re-verified on its own merits before reporting**, and the fan-out readers were briefed to hunt without being told pass 1's conclusions.

The diff between the passes:

- **N-1 … N-6** — new findings this pass contributes (pass 1 missed them).
- **C-1 … C-5** — pass 1's five findings, all of which **survived re-verification** here.
- **No refutations.** Nothing in pass 1's findings was wrong, and none of its "explicitly checked and found sound" claims was contradicted. Pass 1's sound-list stands as written; the list at the bottom of this report is this pass's own coverage record and overlaps it heavily by design.

Severity: **P0** core promise broken or user data loss · **P1** user-visible wrong behavior in common paths · **P2** contract violated under plausible conditions · **P3** narrow-timing or robustness defect.

---

## Findings new in this pass

### P2-N1 — The tour's Finish is a no-op for the rest of the session once the tour has been dismissed, so completion can never be persisted after a skip-then-reopen

**Where:** `app/src/main/java/com/shrekbytes/waqfah/ui/tour/TourSession.kt:112-124` (the guard), with `:97-100` (`onOpenedManually`), `:135-139` (`back()` on the first step dismisses), `:142` (`skip() = dismiss()`), `:182-188` (`dismiss()` sets the flag); reopen wiring at `app/src/main/java/com/shrekbytes/waqfah/ui/main/MainScreen.kt:78` (Home's "?" → `onOpenedManually()`), session hosted app-lifetime-per-entry in `FeatureTourViewModel`.

`next()` on the last step is the only path to persistence, and it is gated:

```kotlin
if (stepIndex == tasks.lastIndex) {
    // The only path to persistence: finishing — exactly once, however
    // many times the button fires before the gate tears the overlay
    // down (dismissal closes it).
    if (!dismissedThisSession) {
        onFinished()
        dismiss()
    }
}
```

The comment scopes the flag to "one showing", but `dismissedThisSession` is shared with skip and with backing out of step 1, and `onOpenedManually()` — the documented way to reopen the tour within a session — never clears it. The same `TourSession` instance survives the skip → reopen cycle (it lives in the Home entry's ViewModel), so: launch → auto-tour → Skip → tap "?" → walk all seven steps → tap Finish → **nothing happens**. `onFinished` never fires, nothing is persisted (ADR-0003's "finishing persists completion" is unreachable), and the session is stuck on the last step — Back and Skip still work, Finish never does again, for the whole session. On the next launch the tour re-offers, per the very rule finishing was supposed to end.

**Evidence (executed probe, then deleted):** a JVM probe at the `TourSessionTest` seam built a real `TourSession` over `TOUR_STEP_TASKS`, called `skip()`, `onOpenedManually()`, walked `next()` to the last step (asserting `stepIndex == tasks.lastIndex`, which passed), then called `next()` and asserted completion:

```
AuditProbeTest > probe_finishAfterSkipAndReopen_persistsCompletion FAILED
    java.lang.AssertionError: Finish must persist completion
```

**Fix sketch:** separate "one finish per showing" from "dismissed this session" — either clear the finish-guard when the tour is (re)opened manually, or replace the guard with a one-shot latch armed inside `next()` itself.

---

### P2-N2 — A fresh session renders the compare-mode peek: `beginFreshSessionLocked()` never clears `translationOverrideId`

**Where:** `app/src/main/java/com/shrekbytes/waqfah/ui/reading/ReadingSession.kt:228-233`; contract stated by the field's own comment at `:88-90`; contrast `step()`'s clear at `:296-299` and `jumpToVerse()`'s clear at `:241-248`.

The compare-translation override is defined to be per-ayah: "Cleared on every step() so it never outlives the ayah it was opened on." Two of the three ways the card lands on a different ayah honor that — `step()` (next/previous) and `jumpToVerse()` both reset the override before rendering. The third does not:

```kotlin
// Caller must hold mutationMutex.
private suspend fun beginFreshSessionLocked() {
    completionDismissed = false
    currentVerse = loadStartingVerse(latestPrefs)
    render(latestPrefs)
}
```

`beginFreshSessionLocked()` is the landing path of all three reset verbs — the completion popup's **Start Again** (`startOver()`), **Switch Mode** (`switchModeAndRestart()`), and the external **Reset progress** echo (`:152-158`). Every one of them moves the card to a new starting ayah while `translationOverrideId` survives, so `render()`'s `shownMeta = availableTranslations.find { it.id == translationOverrideId } ?: defaultMeta` (`:352`) picks the previously peeked translation. The user marks their last ayah read, taps Start Again, and the fresh session opens on a translation they never chose for it — violating the peek's core rule ("for this ayah only").

**Evidence (executed probe, then deleted):** a JVM probe at the `ReadingSessionTest` seam (real `ReadingSession` + real `VerseSelection` over fakes; pickthall in `downloadedIds`) stepped to ayah 1:3, cycled the compare switcher once (asserting the peek was active — that assertion passed), then called `startOver()` and asserted the fresh starting ayah 1:1 renders the default:

```
AuditProbeTest > probe_freshSessionAfterStartOver_rendersDefaultTranslation FAILED
    org.junit.ComparisonFailure: fresh ayah must render the default translation, not the peeked one
    expected:<[Sahih]> but was:<[Pickthall]>
```

**Fix sketch:** clear `translationOverrideId = null` at the top of `beginFreshSessionLocked()`, matching `step()` and `jumpToVerse()`.

---

### P2-N3 — The compare-mode peek also survives the switcher's composition disposal, and then renders styled as the default

**Where:** `app/src/main/java/com/shrekbytes/waqfah/ui/reading/ReadingCard.kt:279-280` (switcher state), `:342-343` (the only reset trigger); disposal scenarios via `app/src/main/java/com/shrekbytes/waqfah/ui/main/MainScreen.kt:67-91` (tab `AnimatedContent`) and `app/src/main/java/com/shrekbytes/waqfah/ui/navigation/WaqfahNavDisplay.kt:88-103` (push disposes `MainScreen`).

The UI side of the switcher is `var translationSwitcherOpen by remember { mutableStateOf(false) }` inside `key(state.ayahLabel)`, and `onResetTranslation()` — the only thing that clears the session's peek short of a verse change — fires **only on the tap that closes the switcher**. There is no `DisposableEffect` clearing it when the keyed subtree leaves composition. So: open compare mode on Home (peek set) → switch to the Settings tab (the `AnimatedContent` disposes the Home subtree; the flag is discarded) → return to Home. The flag rebuilds as `false` — switcher closed, arrows and source-name pill hidden — but the session's override is intact and nothing re-renders it away (even a re-render keeps it, see N-2's `:352`). The card now renders the peeked translation indistinguishably from the user's default. Same leak via compare-mode → "Surahs & ayahs" → back without jumping. Companion to N-2 (same violated contract, different site and mechanism — one is fixed in the session, one needs the composition side).

**Verified by:** analysis of Compose composition semantics (remember scoping under `key` + subtree disposal on tab switch/push). Deterministic at the composition layer but not demonstrable at the JVM seam; confidence in the mechanism is high.

**Fix sketch:** a `DisposableEffect` inside the keyed subtree that calls `onResetTranslation()` on dispose while the switcher is open — or hold the open/closed state in the session next to `translationOverrideId` so it cannot diverge.

---

### P2-N4 — The monitored-apps search doesn't trim its query; a stray space blanks the list, contradicting the go-to search's pinned semantics

**Where:** `app/src/main/java/com/shrekbytes/waqfah/ui/settings/apps/AppsViewModel.kt:85`

```kotlin
.filter { it.app.label.contains(query, ignoreCase = true) }
```

The raw query is matched untrimmed. A trailing space — trivially produced by a soft keyboard or a paste — can never occur inside a label, so `apps` goes empty and the screen renders "no apps found" while matching apps are installed. The codebase has already decided what a search query means: the go-to screen trims before matching, with the comment "so stray keyboard whitespace can't blank the list", pinned by `GoToSurahFilterTest.whitespaceAroundQuery_stillMatchesNames` (`GoToViewModels.kt:113-121`), and commit `fe8fd5a` fixed exactly this failure mode there. The sibling search fields now disagree about the same input.

**Verified by:** code reading; the string semantics are deterministic (`"label".contains("query ")` is false whenever the label lacks the space). Compile-level trivially; not probe-worthy beyond the quoted lines.

**Fix sketch:** `query.trim()` before matching, mirroring `filterSurahRows` (a small pure filter function would let a test pin it the same way).

---

### P3-N5 — Onboarding completion is fire-and-forget, racing the navigation teardown that cancels its scope

**Where:** `app/src/main/java/com/shrekbytes/waqfah/ui/onboarding/OnboardPermissionsScreen.kt:57-60`, `app/src/main/java/com/shrekbytes/waqfah/ui/settings/permissions/PermissionsViewModel.kt:86-88`, `app/src/main/java/com/shrekbytes/waqfah/ui/navigation/WaqfahNavDisplay.kt:81-86`.

The Continue button calls `viewModel.completeOnboarding()` — `viewModelScope.launch { settingsRepository.setOnboardingComplete(true) }`, not awaited — and then `onComplete()` **synchronously** runs `backStack.clear(); backStack.add(Main(...))`. Clearing the stack disposes the onboarding entry and its ViewModelStore, cancelling the very scope the write was launched on. The write must survive at least one suspension inside DataStore's `edit` before it is enqueued; if the teardown wins that race, the write never lands and `MainActivity` re-runs onboarding on next launch (it branches on `hasCompletedOnboarding`).

**Verified by:** analysis only, and honestly low-confidence on reproducibility — in practice the enqueue usually wins the scheduling race against the next frame. The defect is the missing happens-before between the persist and the scope teardown; a lost write is silent (the user just sees onboarding again).

**Fix sketch:** make the button's handler `suspend`-friendly — await the write (or expose a suspending `completeOnboarding()` and navigate from the same coroutine after it returns).

---

### P3-N6 — `notificationsPermanentlyDenied` is sticky for the ViewModel's lifetime and can route taps away from a working runtime dialog

**Where:** `app/src/main/java/com/shrekbytes/waqfah/ui/settings/permissions/PermissionsViewModel.kt:57` (refresh preserves the old value), `:64-71` (the only setter), `app/src/main/java/com/shrekbytes/waqfah/ui/settings/permissions/PermissionsScreen.kt:79` (the routing guard).

The flag means "the system launcher silently no-ops; deep-link to the notification settings page instead". It is set only in `onNotificationRequestResult` and `refresh()` — which runs on every ON_RESUME — copies it forward unconditionally, with no way to recompute it. After a "Don't ask again" denial, the user who grants notifications from the settings page and later revokes them resets the OS's don't-ask state — the runtime dialog would show again — but the flag is still true, so every tap on the row deep-links to settings for the rest of the screen visit. The state now contradicts the flag's own contract: the launcher works, yet taps never reach it. A fresh ViewModel instance resets it, hence P3.

**Verified by:** analysis of the state machine (high confidence — the clearing path is structurally unreachable once set) plus one OS-behavior dependency (`shouldShowRequestPermissionRationale` resetting after a settings re-grant/revoke cycle): **unverified on device**, medium confidence on that half.

**Fix sketch:** recompute the flag in `refresh()` from the launcher's perspective where the API allows, or clear it when `refresh()` observes the permission granted (a grant implies the no-again state was lifted).

---

## Pass-1 findings, re-verified by this pass

Each of pass 1's five findings was re-derived from the code here (full write-ups live in the [pass-1 report](2026-09-06-correctness.md); this section records the independent verification, not a repeat).

- **C-1 (pass-1 P2-1) — poll windows don't tile real time.** Re-traced `MonitorSession.run()`: `windowStart` (`MonitorSession.kt:80`) is captured after the previous iteration's query + decision + launch have already run, while `windowEnd` (`:95`) closed before them — the processing slice belongs to no `queryEvents` call, so a resumed activity in it is never fed to the decision. Pass 1's executed probe output stands as the demonstration; this pass's trace reaches the same conclusion independently.
- **C-2 (pass-1 P3-1) — `TriggerDecision.reset()` races the verdict loop.** Re-confirmed: all rule state is plain mutable fields (`TriggerDecision.kt:103-124`), `reset()` (`:135-140`) is unsynchronized and runs from the gate collector on the multithreaded `Dispatchers.Default` `serviceScope` (`AppMonitorService.kt:60-61`, `:129`) concurrently with the session loop's `decide()` (`:161`). The memory-model violation is unconditional; the user-visible outcome is the narrow, self-healing one pass 1 described.
- **C-3 (pass-1 P3-2) — the once-only re-assert re-arms across `CLEAR_TOP` re-launches.** Re-confirmed on both ends: `launchFlags(reassert = true)` emits `CLEAR_TOP` without `SINGLE_TOP` (`InterstitialSession.kt:28-32`), and the budget lives in the instance Bundle (`TriggerActivity.kt:70`, `:74`, `:154-157`) — which a finish-and-recreate relaunch does not carry over (`reassertUsed = true` set at `:179` before the relaunch at `:181-186` dies with the instance). Platform behavior, unverified on device, as in pass 1.
- **C-4 (pass-1 P3-3) — translation file-handle lifecycle races eviction/reopen/delete.** Re-confirmed the lock topology first-hand: creation is double-checked under `openLock` (`TranslationRepository.kt:322-327`), while `getText()`'s failure path closes-and-deletes (`:112-119`) and `delete()` (`:162-171`) take neither `openLock` nor a shared critical section — the recreate-after-delete interleaving ("no such table", read as non-corruption) is open. (Pass 1's numbers `:84-125,160-171,322-327` match.)
- **C-5 (pass-1 P3-4) — an unexpected Room/DataStore exception in a reading-session coroutine crashes the process.** Re-confirmed: every reading-machine mutation is a bare `scope.launch` (`ReadingSession.kt:107-159` and the verbs at `:167-219`), `viewModelScope` carries no handler, and the same shape exists in `BootReceiver.kt:35-52` and `MainActivity.kt:81-84`. This pass adds the observation that the reading card's **swipe handlers** await `session.next()/previous()` from Compose coroutine scopes, so the same exception family can surface there mid-gesture (same fix: one `CoroutineExceptionHandler` at the hosting scopes).

---

## Explicitly checked and found sound

This pass's coverage record — contracts verified line-by-line (by the audit driver on the core machines, by the reader passes on the UI surface, with every candidate finding re-verified by the driver before reporting):

- **Trigger rules core** (`TriggerDecision`): the one-pause-per-stay arming, call handling (calls never count as leaving; grace armed at call end from the landing spot; lazy grace expiry; `currentForeground` untouched during calls), switch-back recorded only on real foreground changes with age-based sweeping, cooldown arithmetic (`0` = Off, negative elapsed = expired, Int·60_000L overflow unreachable), interstitial-return class match per ADR-0004, indirect-entry probes with the launcher activity exempted, late prefs/membership reads after the cheap rules.
- **Monitored-app state**: the claim's SQL compare-and-set (membership id + stamp + revision, rows-affected=1) fails closed on remove/re-add and concurrent decisions; `toggle` is transactional; `add` is INSERT IGNORE (preserves stamps); migration 1→2 preserves data with fresh membership ids; no destructive fallback on `waqfah_app.db`.
- **Verse selection**: sequential first-unread / random unread with their everything-read fallbacks, in-surah continue, wrap-around stepping, null only when there is no data; determinism under the injected `Random`.
- **Reading machine**: mark-read decided under the mutex against DB truth; the monotonic `lastSelfInitiatedReset` echo filter (conflation-safe in both orders); render's single `downloadedIds` snapshot shared with previews; the render-signature filter excluding `appActive` per ADR-0002; `cycleTranslationSource`'s modulo arithmetic safe at both ends (peek-lifetime defects excepted, N-2/N-3); previews never blank.
- **Translation intake**: atomic tmp-rename for asset copies and downloads (with copy fallback and tmp cleanup); magic-header + openDatabase + `user_version ∈ {0, expected}` + schema probe before the rename; streaming SHA-256 with catalog-pinned checksums; per-id download mutexes; `refreshDownloadedIds` serialized with the listing inside; corruption-vs-transient classification over the cause chain.
- **Active translation**: `resolveActive` can never blank the card (stored → bundled fallback); Settings and the reading card share the one resolver; `available()` counts bundled translations unconditionally.
- **Detection rhythm and lifetime**: the monitor gate suspends the loop when closed; fresh window on every wake (stale events never replayed); permission heartbeat cadence; screen state re-read once at `onCreate` before the receiver takes over; supervisor matrix (only the toggle stops; resume/boot start-only; stopping ignores permission probes); boot receiver routes only `BOOT_COMPLETED`, `goAsync` finished in `finally`; START_STICKY restart re-reads real screen state.
- **`ResumedActivityReader`**: events walked in order; `latestIn` tie keeps the last-seen; `isLatestForeground` fails conservative when the window misses.
- **Preferences**: every enum read degrades via `enumOrDefault`; null-until-loaded is the `loadedPreferences` type; splash holds until first load; cooldown writes coerced to `PreferenceLimits`.
- **Navigation & shell**: the double-tap push guard (value-equality, `Main(HOME)` ≠ `Main(SETTINGS)`); the whole back stack saved/restored across process death; the start destination locked on first composition so onboarding's flag flip can't remount mid-flow; tour visible only over Home.
- **Go-to**: trimmed, test-pinned search semantics; digit-filtered ayah input with range validation; the jump → continue-in-surah fallback chain; Go guarded against blank input.
- **Tour machine** apart from N-1: anchor captured only on a resolved card and re-taken when loading resolves; GO_TO_AYAH completable only by an in-picker jump; picker/jump state reset only on real step changes; back priority (picker → step → dismiss); `tourVisible` keeps auto-show shut while preferences are unresolved.
- **Translations screen**: retry loop rethrows `CancellationException` at both levels and always clears downloading/progress in `finally`; per-id repository mutex makes double-tap downloads safe; delete guarded for bundled entries; row-state `when` ordering matches the VM's flags.
- **Settings ViewModels**: read-then-write toggle convergence; per-script font filtering (the stale-prefs font write traced benign); steppers coerce against `PreferenceLimits` on both ends.
- **Reading card UI**: swipe commit thresholds and drag serialization against the session mutex; mark-read double-tap vs translation-tap gesture split; dismiss paths all funnel into TriggerActivity's `closing` guard; the peek pages' wrap-around previews.
- **Formatting & theme**: Bengali/Arabic-Indic digit maps character-correct; exhaustive `when`s over the display enums; per-script font families; accent application matching `hasAccentPicker`.

## Out-of-scope notes

Unchanged from pass 1: maintainability/health, security/privacy, and release-readiness belong to passes 2–4 of the series; nothing here was fixed; no tracker issues were filed from findings; the checked-in SQLite binaries were not audited.
