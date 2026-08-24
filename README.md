# Waqfah

**Waqfah** (وقفة — "a pause") is a digital *mushaf pause* for Android: when you
open one of your chosen apps, Waqfah steps in first with a single ayah to read.
Read it (or skip it) and continue — the point is a small, deliberate pause in
the scroll.

- **Reading modes:** sequential (resume at the lowest unread ayah) or random
  (any unread ayah), with progress tracking across the whole Quran.
- **Arabic:** Indopak / Uthmani scripts, five fonts, adjustable sizes,
  transliteration and translations in English or Bengali, plus extra
  downloadable translations.
- **Gentle by design:** at most one pause per app open; a configurable cooldown
  interval (or Off) controls how often repeats can trigger. Share-sheet and
  "Open with" entries never interrupt.

## How detection works

Waqfah runs a foreground service that watches foreground app changes via the
public Usage Stats API. It needs three permissions:

| Permission | Why |
|---|---|
| Usage access | See which app moved to the foreground (checked via AppOps). |
| Display over other apps | Let the reading interstitial appear over the target app from the background on Android 10+. |
| Battery optimization exemption | Keep the monitor alive under aggressive OEM battery managers. |

Nothing leaves the device: monitored apps, reading progress and preferences
stay local, and no usage data is collected.

## Building

```bash
./gradlew :app:assembleDebug      # debug APK
./gradlew :app:testDebugUnitTest  # unit tests
```

Requirements: Android Studio with AGP 9.x-compatible tooling, JDK 17.
minSdk 28 · targetSdk 37.

## Contributing a translation database

Downloadable translations are plain SQLite files fetched at runtime and opened
read-only by Room. A valid file must have:

- table `translations (verse_id INTEGER PRIMARY KEY, text TEXT NOT NULL)`,
  one row per ayah id (1–6236),
- `PRAGMA user_version` equal to `1` (or `0`),
- served over HTTPS; add the entry to `TranslationCatalog` with its URL.

Files are verified (SQLite header + schema + version) before being accepted;
anything else fails fast with an error shown on the download row.

## License

See [LICENSE](LICENSE).
