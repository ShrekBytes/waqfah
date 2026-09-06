# Correctness audit — whole-codebase pass 1

- **Date:** 2026-09-06 · **Audited commit:** `0420241` · **Spec:** [issue #9](https://github.com/ShrekBytes/waqfah/issues/9)
- **Method:** exhaustive read of all 88 production source files through the correctness lens, against the contracts in `CONTEXT.md`, `docs/ARCHITECTURE.md`, and the ADRs. Read-and-report only — the working tree is untouched by this pass (one throwaway probe test was run and deleted; its output is quoted below).
- **Baseline:** `./gradlew :app:testDebugUnitTest` green (18 test classes) at the audited commit.
- **Disposition (2026-09-07):** all findings fixed — P2-1 in `914b18e`, P3-1 in `8cd364f`, P3-2 in `565c722`, P3-3 in `cf3dc64`, P3-4 in `89389ae` (its gesture-scope half noted by pass 2 landed in `d3d21b1`). After the fixes the suite is green (155 tests) and `assembleDebug` is clean.

Severity: **P0** core promise broken or user data loss · **P1** user-visible wrong behavior in common paths · **P2** contract violated under plausible conditions · **P3** narrow-timing or robustness defect.

---

## Findings

### P2-1 — Poll windows don't tile real time: resumed activities between two windows are dropped forever

**Where:** `app/src/main/java/com/shrekbytes/waqfah/detection/MonitorSession.kt:76-104`

The loop captures `windowStart = nowWall()` at the top of each iteration — *after* the previous window's events were queried and processed — and `windowEnd = nowWall()` after the 1 s poll delay. In production (`System.currentTimeMillis()`), the time spent querying UsageStats and processing the previous window's events (the decision's DataStore preference read, the trigger claim, `startActivity` on a Trigger verdict) elapses between `windowEndₙ` and `windowStartₙ₊₁`. That slice belongs to **no window**: an `ACTIVITY_RESUMED` event recorded there is never returned by any `queryEvents` call and never reaches the decision.

**Contract violated:** `MonitorSession`'s own doc — "EVERY resumed activity inside it is fed to the decision in order — not just the latest event, because a chooser flash shorter than one poll must still pair" — and `MonitorSessionTest`'s stated spec, "the window still spans the whole interval — start where the last window ended." The existing test passes only because its manual wall clock freezes while events are processed, which is exactly what a real clock does not do.

**Evidence (executed probe, then deleted):** a JVM probe modeled the production clock with the test scheduler and simulated the decision's real preference-read latency with a 50 ms delay (a fraction of what a UsageStats walk + DataStore read + trigger launch costs on device):

```
PROBE queriedWindows=[(0, 1000), (1050, 2050), (2050, 3050)]
PROBE verdicts=[Trigger(packageName=com.target.app)]
```

Window 1 ends at 1000; window 2 starts at 1050. The slice [1000, 1050) is covered by no window, and an event timestamped inside it can never be served.

**Impact:** during app-switching bursts — precisely when trigger decisions matter — each 1 s cycle drops a slice on the order of the processing cost (tens of ms, more on a Trigger verdict). A chooser flash, a splash-chain hop, or the fresh-open event itself can land there: picker-pairing then misses (spurious trigger on indirect entries via the previous-event check) or a fresh open is simply never seen (missed trigger). The class-probe fallback still catches most indirect entries, so the most likely user-visible symptom is a *missed* trigger whose event landed in a gap.

**Fix sketch:** carry the window across iterations — keep `windowEnd` in a `var`, and on the next iteration query `[lastWindowEnd, newEnd)`; keep the "fresh window at the wake" behavior (start from `nowWall()` only when the gate transitioned from closed to open) as `MonitorSessionTest` already pins. `TriggerDecision.reset()` on gate close keeps the fresh-window semantics safe.

---

### P3-1 — `TriggerDecision.reset()` races the verdict loop on unsynchronized rule state

**Where:** `app/src/main/java/com/shrekbytes/waqfah/detection/TriggerDecision.kt:103-140,142-239` and `app/src/main/java/com/shrekbytes/waqfah/detection/MonitorSession.kt:69-72`

`TriggerDecision` holds all its rule state in plain mutable fields (`lastResumedActivity`, `currentForeground`, `lastLeftForegroundAt`, `inCall`, `callGracePackages`, `callGraceEndsAt`) with no synchronization. Two coroutines in `AppMonitorService`'s `serviceScope` (a multithreaded `Dispatchers.Default` scope) touch it concurrently:

1. the session loop, calling `onResumedActivity()` → `decide()` (mutating the fields), and
2. the gate's `onEach` collector, calling `reset()` whenever the gate emits `false` (screen off, toggle off, monitored set emptied).

`reset()` exists precisely to guarantee "nothing from before a pause may pair against, or extend a grace window into, a post-wake resume." An interleaving defeats it: the gate flips closed *while* `decide()` is mid-execution on an event (e.g. the user presses power while an app is launching and the poll lands simultaneously). Depending on the interleaving point, `reset()` can clear `callGracePackages`/`callGraceEndsAt` *before* `decide()` re-arms them (grace state survives the pause into post-wake), or `decide()` can read `lastResumedActivity`/`inCall` mid-reset (a torn pairing across the pause). Consequence: one wrongly-paired verdict at wake — a spurious trigger or a missed suppression — after which state self-corrects. Narrow window (gate flip must coincide with event processing), one-shot, self-healing.

**Verified by:** analysis of the interleaving; the memory model violation (unsynchronized cross-thread access to non-`volatile` fields from two coroutines on `Dispatchers.Default`) is unconditional, only its user-visible outcome is timing-dependent. A deterministic unit repro is not practical for a data race.

**Fix sketch:** guard the whole `decide()`/`reset()` body with a single `Mutex` (both entry points are already suspend or trivially made so), or confine all `TriggerDecision` access to one dispatcher/actor.

---

### P3-2 — The interstitial's once-only re-assert resets across `CLEAR_TOP` re-launches, so a stubborn app can re-arm it

**Where:** `app/src/main/java/com/shrekbytes/waqfah/detection/InterstitialSession.kt:34-58`, `app/src/main/java/com/shrekbytes/waqfah/TriggerActivity.kt:159-187`, manifest `TriggerActivity` declaration (`noHistory="true"`, `taskAffinity=""`)

The re-assert guard is per-instance: `reassertUsed` lives in the activity instance (persisted across *recreation* via `savedInstanceState`). But the re-assert itself launches with `FLAG_ACTIVITY_CLEAR_TOP` and without `FLAG_ACTIVITY_SINGLE_TOP`, in a task that contains only the interstitial (`taskAffinity=""`). `CLEAR_TOP` therefore **finishes** the buried instance and creates a **fresh** instance — with `savedInstanceState == null`, so `reassertUsed` starts at `false` again. An app that buries the interstitial repeatedly without user input (splash-chain loops, self-raising tasks) gets a fresh once-only budget each round: `i₁` buried → re-assert `i₂` → `i₂` buried → re-assert `i₃` → …

**Contract violated:** `InterstitialSession`'s doc — "a stubborn app must not be able to trap the user in a loop of interstitials; the flag survives recreation." The flag survives *recreation* but not the `CLEAR_TOP` re-launch that the re-assert itself performs, so the loop guard is defeatable in exactly the scenario it was written for. Practically narrow: each re-assert requires the target app to bury the interstitial *again* on its own, which ordinary splash chains do not do after settling.

**Verified by:** analysis of launch-flag semantics (`CLEAR_TOP` without `SINGLE_TOP` = finish-and-recreate) against the manifest attributes; not demonstrable on the JVM because the behavior lives in the platform's activity dispatch, and not exercised on-device because it requires a hostile app. Confidence in the mechanism is high; confidence that any real app triggers it is low — hence P3.

**Fix sketch:** carry the once-only state outside the instance (e.g. a process-lifetime "re-asserted for trigger N" stamp keyed by the trigger claim's revision, or drop the per-instance Bundle path in favor of a companion/`MonitorSession`-owned flag), so a re-assert budget belongs to the *trigger*, not the instance.

---

### P3-3 — Translation file-handle lifecycle races: eviction, reopen, and delete are not mutually excluded

**Where:** `app/src/main/java/com/shrekbytes/waqfah/data/repository/TranslationRepository.kt:84-125,160-171,322-327`

`getText()`'s failure path closes and optionally deletes a translation file (`openDatabases.remove(id)?.close()` + `fileFor().delete()`) outside the `openLock` that serializes *creation*, and `delete()` runs under the per-id *download* lock but not the open lock. Interleavings:

- Thread A fails a read and starts evicting while thread B (render fires up to three concurrent `getText` calls) is inside `synchronized(openLock)` building a fresh handle for the same id. B's `TranslationDatabase.build()` then opens (and SQLite silently **recreates**) a file A just deleted — every subsequent `getText` fails with "no such table", read as non-corruption, returning null until the availability refresh reroutes the card to the bundled translation.
- A `delete()` can land between B's `isDownloaded()` check and its `open()`, same outcome: an empty recreated file at a path the UI now believes is deleted.

Consequence is transient blank ayahs / a stale-looking row after a delete or a corrupted read, self-healing on the next availability-driven re-render. The same seam already ate one bug of this shape (the comment cites "switching en↔bn sometimes shows an empty translation"); the remaining hole is evict/delete vs. reopen.

**Verified by:** analysis of the lock coverage (`downloadLocks` ≠ `openLock`; eviction and deletion take neither consistently with `open()`). A deterministic repro would require interleaving two render threads against a failing file — not practical as a unit test without injecting failure points the production class doesn't have.

**Fix sketch:** perform close+delete under `openLock` (single lock for the handle map's full lifecycle), and have `delete()` reuse the same critical section.

---

### P3-4 — An unexpected Room/DataStore exception in a reading-session coroutine crashes the process

**Where:** `app/src/main/java/com/shrekbytes/waqfah/ui/reading/ReadingSession.kt:107-159` (collectors and all `scope.launch` mutations), `detection/BootReceiver.kt:35-52`, `MainActivity.kt:81-84`

Every reading-machine mutation runs in `scope.launch { … }` (viewModelScope) with no exception handling. The translation probe is internally hardened (`TranslationRepository.getText` catches everything and classifies), but the Quran-side probes (`verseById`, `surah`, `getAllVerseIds`, `isRead`, `markRead`/`unmarkRead` on `waqfah_app.db`) and the DataStore reads behind `preferences` are not. A transient SQLite failure (disk pressure, `SQLITE_BUSY`) or a DataStore `IOException` inside any render, step, or mark-read propagates out of the `launch` to the default uncaught handler: **process death** on the reading screen (or, for `BootReceiver`'s one-shot scope, a boot-time crash).

**Verified by:** code-path analysis (no handler anywhere on these launches; SupervisorJob only prevents sibling cancellation, not the crash handler). Not reproduced at runtime — triggering it requires a failing disk, which the audit won't inflict on the working tree. Framed as robustness: the probability is low, the blast radius is total.

**Fix sketch:** one `CoroutineExceptionHandler` on the reading scope (log + keep the last good UI state, mirroring `AppMonitorService`'s handler), and the same for `BootReceiver`'s one-shot scope.

---

## Explicitly checked and found sound

The following high-risk contracts were verified line-by-line and are correctly enforced; listed so future audits know they were covered, not skipped:

- **Trigger rules** (`TriggerDecision`): call checks first and never count as leaving the foreground; one pause per continuous stay; interstitial-return matched by class (an open from Waqfah's own main screen still counts as fresh); call grace armed at call end and covering multi-hop returns; indirect entries via both picker-pairing and the class probe; launcher activity never suppressed as indirect; switch-back checked regardless of cooldown; cooldown `0` = Off; negative elapsed (clock rollback) counts as expired.
- **Trigger-stamp ownership**: stamped exactly once, inside `TriggerDecision`, at trigger time, never on dismissal; the claim is a true compare-and-set on membership id + stamp + revision (`AppStateDaos.claimTrigger`), failing closed on remove/re-add and concurrent decisions; `toggle` keeps membership changes in a Room transaction; `insertIfAbsent` preserves an existing membership's stamp.
- **Migration 1→2** preserves all user data (rebuild-and-copy with fresh membership ids, revision 0), and `WaqfahAppDatabase` has no destructive fallback.
- **Verse selection**: sequential first-unread with first-ayah fallback; random unread with any-ayah fallback; in-surah continue with first-ayah fallback; wrap-around stepping; null only means "no data". Mark-read decisions are made under `mutationMutex` against DB truth.
- **Translation intake**: atomic tmp-rename for both asset copies and network downloads; magic-header + schema probe + `user_version` gate + streaming SHA-256 before the rename; per-id download mutexes; corruption-vs-transient eviction classification; the published disk-truth set refreshes under a lock with the listing inside it.
- **Active-translation resolution** can never blank the card (stored → bundled fallback, pinned by tests), and Settings' active label and the reading card share the one resolver.
- **Monitor supervisor matrix**: only the toggle may stop; resume and boot may only start; stopping ignores permission probes by design.
- **The toggle governs detection only**: the reading path never reads `appActive`.
- **Preferences**: unknown enum values degrade to defaults; null-until-loaded is part of the `loadedPreferences` type; the splash holds until first load; the reading render signature filters exactly the card-relevant fields.
- **Manifest surface**: the interstitial is `exported="false"`; the service is a `specialUse` FGS with its subtype property; the boot receiver is `exported="false"` against a protected broadcast; `<queries>` declares the launcher-intent visibility the app catalog needs on Android 11+.

## Out-of-scope notes handed to the later passes

- Pre-stable toolchain exposure (Navigation 3, AGP 9.x) and doc-vs-code rule-order trivia (e.g. the `INACTIVE` rule's position relative to switch-back, behavior-neutral) — pass 2 (health).
- `allowBackup`/`dataExtractionRules` posture, egress inventory, logging review — pass 3 (security).
- R8 keep-rules (the all-comments `rules.keep`), `assembleRelease` verification — pass 4 (release-readiness).
