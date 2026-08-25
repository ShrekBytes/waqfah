# Architecture

A quick map of how Waqfah works, for anyone (human or AI) touching the code.
The detailed, code-adjacent rationale lives in doc comments next to what it
explains — those are the source of truth; this file is just the index.

## The core loop

**AppMonitorService** (foreground service, `specialUse` FGS) polls
UsageStatsManager every second while the screen is on and detection is armed.
It pairs consecutive `ACTIVITY_RESUMED` events to tell real opens apart from
share-sheet/file-viewer/link-grabber entries, then launches **TriggerActivity**
over the target app when all trigger rules pass:

1. one pause per continuous foreground-stay of an app,
2. never on indirect entries (picker-mediated or worker activities),
3. never when returning from Waqfah's own interstitial,
4. cooldown (`interval > 0`) or Off-session gap (`interval = 0`) decides
   whether a fresh open is allowed through; `last_shown_at` is stamped once,
   at trigger time, regardless of how the interstitial is later dismissed.

**TriggerActivity** is a translucent interstitial rendering **ReadingCard**;
finishing it falls through to whatever was really underneath.

## Layers

- `data/local/core` — read-only Quran text (`quran_core.db`, bundled asset,
  rebuilt wholesale each release; destructive migration by design).
- `data/local/appstate` — user data (`monitored_apps`, `read_verses`);
  **no** destructive fallback here, migrations must be written if schema changes.
- `data/local/translation` + **TranslationRepository** — per-language
  translation `.db` files downloaded from the repo into internal storage;
  atomic tmp-rename writes, sqlite-magic/schema probes, SHA-256 checksums from
  `TranslationCatalog` (regenerate those whenever a `translations/**.db`
  changes), corruption-vs-transient error classification.
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

- Service starts from MainActivity.onResume (and BootReceiver after reboot);
  it self-stops if permissions are revoked (re-checked every 30s).
- POST_NOTIFICATIONS is deliberately not declared — see AndroidManifest.
