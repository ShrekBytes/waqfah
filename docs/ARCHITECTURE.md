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
- `data/local/translation` + **TranslationRepository** — per-language
  translation `.db` files hosted in the separate waqfah-translations repo and
  downloaded into internal storage;
  atomic tmp-rename writes, sqlite-magic/schema probes, SHA-256 checksums from
  `TranslationCatalog` (regenerate those whenever a `.db` in the
  waqfah-translations repo changes), corruption-vs-transient error
  classification.
- **SettingsRepository** — DataStore-backed `UserPreferences`; enum values
  degrade to defaults instead of crashing after renames.
- Repositories expose Flows; ViewModels combine them into immutable UI state;
  Compose screens stay stateless where possible.

## Concurrency notes

- ReadingViewModel serializes verse mutation + render behind `mutationMutex`;
  mark-read decisions are made under that lock against DB truth.
- TranslationRepository guards downloads with per-id mutexes and Room handles
  with a double-checked open cache.

## Lifecycle

- Service lifetime mirrors the Waqfah on/off toggle: **SettingsViewModel.toggleActive**
  starts it (permissions permitting) or stops it outright; MainActivity.onResume
  restarts it when permissions were just granted (but never resurrects a
  toggled-off monitor); BootReceiver restarts it after reboot only when
  permissions are present AND Waqfah is toggled on. The session asks the
  service to self-stop if permissions are revoked mid-run (re-checked every
  30s). While the monitor gate is closed — screen off, monitoring off, no
  monitored apps — the session's loop suspends.
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
