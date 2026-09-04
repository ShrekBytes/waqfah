# Architecture

A quick map of how Waqfah works, for anyone (human or AI) touching the code.
The detailed, code-adjacent rationale lives in doc comments next to what it
explains — those are the source of truth; this file is just the index.

## The core loop

**MonitorSession** (`detection/MonitorSession.kt`), hosted by the **AppMonitorService** foreground
service (`specialUse` FGS), polls UsageStatsManager every second while the monitor gate is open —
screen on, Waqfah active, at least one monitored app — and feeds each `ACTIVITY_RESUMED` event to
**TriggerDecision** (`detection/TriggerDecision.kt`). That module owns every trigger rule and all
rule state; each event gets a verdict — trigger, or ignore with a reason — and the service, the
Android adapter for both modules, launches **TriggerActivity** on a Trigger.
The rules, in the order the decision applies them:

1. never on a call — a messaging app's own call screen, a system dialer/
   incall-UI package, or audio routed into a call — the call check runs
   before every other rule, and a call never even counts as leaving the
   foreground,
2. one pause per continuous foreground-stay of an app,
3. never when returning from Waqfah's own interstitial,
4. never within `CALL_GRACE_MS` of a call ending, on either the app it
   interrupted or the calling app's own screen, however many hops the
   return takes,
5. never on indirect entries (picker-mediated or worker activities),
6. never on a quick switch-back within `SWITCH_BACK_GAP_MS`, regardless of the
   cooldown,
7. otherwise the cooldown decides (`0` = "Off" triggers on every fresh
   open that gets this far); the `last_shown_at` anchor is stamped inside the
   module, exactly at trigger time, regardless of how the interstitial is
   later dismissed.

The decision is tested on the JVM through scripted resumed-activity sequences
with fake clocks (`TriggerDecisionTest`) — the three regression loops that
used to be fixed blind in the service are pinned there. The watching rhythm
itself — gate suspension, fresh windows, the 30s permission heartbeat — lives
in **MonitorSession** and is tested with virtual time (`MonitorSessionTest`).
When the monitor gate
closes (screen off, monitoring off), the session resets the decision, severing
the picker pairing and call context so nothing from before the pause pairs
into a post-wake resume. The reset deliberately spares the foreground tracker
and switch-back map: switch-back entries expire by age, so a stale one can't
suppress a later open.

**TriggerActivity** is a translucent interstitial rendering **ReadingCard**;
finishing it falls through to whatever was really underneath.

## Layers

- `data/local/core` — read-only Quran text (`quran_core.db`, bundled asset,
  rebuilt wholesale each release; destructive migration by design).
- `data/local/appstate` — user data (`monitored_apps`, `read_verses`);
  **no** destructive fallback here, migrations must be written if schema changes.
- **MonitoredAppState** (`data/monitoredapp/MonitoredAppState.kt`) owns the
  monitored-app state facts — monitored package membership and trigger stamps —
  while `MonitoredAppStateRepository` adapts them to the Room database. Its
  `toggle` operation keeps membership changes atomic, and adding an existing
  package preserves its trigger stamp. A `monitoredMembership` snapshot carries
  the current membership identity and claim revision; `claimTrigger` atomically
  stamps only that observed membership, so a removed/re-added package or a
  concurrent decision fails closed. Version 1 to 2 preserves all user data.
- **InstalledAppCatalog** (`data/installedapp/InstalledAppCatalog.kt`) owns the
  installed-app catalog seam. `PackageManagerInstalledAppCatalog` adapts Android
  launchable-app discovery, labels, and fixed-size icons for Settings and
  onboarding; it is separate from monitored-app state.
- `data/local/translation` + **TranslationRepository** — per-language
  translation `.db` files hosted in the separate waqfah-translations repo and
  downloaded into internal storage;
  atomic tmp-rename writes, sqlite-magic/schema probes, SHA-256 checksums from
  `TranslationCatalog` (regenerate those whenever a `.db` in the
  waqfah-translations repo changes), corruption-vs-transient error
  classification.
- **SettingsRepository** — DataStore-backed `UserPreferences`; enum values
  degrade to defaults instead of crashing after renames. Observers read
  `loadedPreferences` (`UserPreferences?`: null until DataStore's first
  emission, contract pinned by LoadedPreferencesTest); await-then-act readers
  use the cold `preferences` flow.
- Repositories expose Flows; ViewModels combine them into immutable UI state;
  Compose screens stay stateless where possible. Two deliberate exceptions:
  ReadingViewModel exposes its ReadingSession directly (the machine owns its
  state — screens read `session.uiState`), and ReadingPorts is owned by the
  reading machine (ui/reading) with DefaultReadingPorts (data/repository)
  implementing it — a consumer-owned port, wired in AppModule.

## Concurrency notes

- ReadingSession serializes verse mutation, render, and the preference state
  they read behind `mutationMutex`; mark-read decisions are made under that
  lock against DB truth. ReadingViewModel is only its Android adapter.
- TriggerDecision keeps cooldown policy and captures the exact wall-clock stamp;
  MonitoredAppState owns the compare-and-set trigger claim. Membership identity
  distinguishes remove-and-re-add, and the claim revision distinguishes
  concurrent claims that happen to share a wall-clock value.
- TranslationRepository guards downloads with per-id mutexes and Room handles
  with a double-checked open cache.

## Lifecycle

- Service lifetime is owned by **MonitorSupervisor** (`detection/MonitorSupervisor.kt`):
  three one-line adapters hand it an event — **SettingsViewModel.toggleActive**
  (persists the flip, then syncs), MainActivity.onResume, BootReceiver (boot,
  logging the outcome) — and it starts or stops the service from persisted
  `appActive` + required permissions. The toggle may start or stop; resume and
  boot may only start, so resuming the app never resurrects a toggled-off
  monitor. The rule's matrix is pinned in `MonitorSupervisorTest`. The session
  asks the service to self-stop if permissions are revoked mid-run (re-checked
  every 30s) — runtime self-defense, not a supervisor event. While the monitor
  gate is closed — screen off, monitoring off, no monitored apps — the
  session's loop suspends.
- **The toggle governs DETECTION only.** Home-tab reading (`ReadingViewModel` /
  `ReadingCard`) never reads `appActive` and runs entirely off Room + DataStore;
  stopping the service must never affect it. Keep these decoupled.
- Battery exemption and POST_NOTIFICATIONS are strictly optional rows rendered
  after the required two (see PermissionCatalog.recommended): battery keeps the
  monitor alive on OEMs that kill background apps — stock Android needs it less,
  and it is granted by deep-linking the user to the app's own system page
  (Battery -> Unrestricted) rather than by any declared permission — while
  POST_NOTIFICATIONS only makes that notification visible on Android 13+.
  Denials never block onboarding nor affect detection.
