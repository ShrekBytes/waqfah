# Waqfah

**Waqfah** (وقفة — "a pause") shows a single Quranic ayah before the apps you
choose. When you open a monitored app — any app at all, whether it's a social
network, a game, or a calculator — Waqfah's reading screen appears first.
Read the ayah or skip it, continue into the app, and get on with your day.

That's the whole idea. Waqfah isn't trying to stop you from doing anything,
fix a habit, or change how you use your phone. There's no blocking, no
limits, no tracking, and no lectures — just an ayah, shown before a selected
app opens, nothing more.

## Features

- **Reading modes:** sequential (resume at the lowest unread ayah) or random
  (any unread ayah), with progress tracking across the whole Quran.
- **Arabic display:** Indopak and Uthmani scripts, several bundled fonts,
  adjustable sizes, plus optional transliteration and translations in English
  or Bengali — with more available as downloads.
- **App language:** English or Bengali, or follow the system language.
- **Unobtrusive by design:** at most one reading screen per app open; a
  configurable per-app cooldown interval (or Off) controls how often repeats
  can trigger. Share-sheet and "Open with" entries never trigger it.
- **No accounts, no servers:** everything works offline except the explicit
  download of an optional translation.

## Download

Waqfah's APK is available now from
[GitHub Releases](https://github.com/ShrekBytes/waqfah/releases).
It is also coming soon — in shaa Allah — to [F-Droid](https://f-droid.org/packages/com.shrekbytes.waqfah)
and Google Play.

## Privacy

Waqfah collects nothing and sends nothing anywhere. Monitored apps, reading
progress, and preferences stay on the device. The one exception is Android's
own device backup: if it is turned on, Android includes that data in your
device's backup, stored in your Google account or transferred directly to a
new device — Waqfah itself never sends anything anywhere. The only network
traffic is downloading an optional Quran translation that you explicitly
request — fetched over HTTPS from the [waqfah-translations][translations-repo]
repository and verified against pinned SHA-256 checksums before use. There
are no ads, no analytics, no trackers, and no accounts.

Waqfah also cannot read what's inside other apps: it has no accessibility or
screen-content permissions. Usage access only tells it *which* app moved to
the foreground — never what's displayed in it.

## How it works

While the screen is on, a foreground service checks which app moved to the
foreground roughly once per second via the public Usage Stats API, and shows
a translucent reading screen over the monitored app when it opens. Dismissing
the reading screen simply falls through into that app, exactly where you left
off. Consecutive foreground events are paired so indirect entries (share
sheet, "Open with", link grabbers) never count as an app open.

## Permissions

**Required** (Waqfah asks for these during onboarding):

| Permission | Why |
|---|---|
| Usage access (`PACKAGE_USAGE_STATS`) | See which app moved to the foreground, so the reading screen appears at the right moment. Checked via AppOps; granted through system settings. |
| Display over other apps (`SYSTEM_ALERT_WINDOW`) | Let the reading screen appear over the opening app from the background on Android 10+. |

**Recommended** (optional; denying them never blocks anything):

| Setting / permission | Why |
|---|---|
| Unrestricted battery *(a system setting, not a permission)* | Stops aggressive OEM battery managers from killing the background monitor. Waqfah deep-links you to its own system app page, where you choose Battery → Unrestricted — no special permission is requested or needed. |
| Notifications (`POST_NOTIFICATIONS`) | Keeps the mandatory foreground-service notification visible on Android 13+. |

**Declared by the system or implied by the above:**

| Permission | Why |
|---|---|
| Internet (`INTERNET`) | Only used for downloading optional translations; no other requests are made. |
| Run at startup (`RECEIVE_BOOT_COMPLETED`) | Restarts the monitor after reboot, but only if Waqfah is toggled on and its permissions are still granted. |
| Foreground service (`FOREGROUND_SERVICE_SPECIAL_USE`) | Android's required mechanism for the continuous background monitor; it runs behind a silent, lowest-priority notification. |

## Building

```bash
./gradlew :app:assembleDebug      # debug APK
./gradlew :app:assembleRelease    # release APK
./gradlew :app:testDebugUnitTest  # unit tests
```

Requirements: Android SDK platform 37 (AGP 9.x-compatible tooling, e.g.
a current Android Studio). Gradle auto-provisions its daemon JDK, pinned
in `gradle/gradle-daemon-jvm.properties`.
minSdk 28 (Android 9) · targetSdk 37.

Built with Kotlin and Jetpack Compose (Material 3); persistence via Room and
DataStore; dependency injection with Hilt.

### Releasing

`./gradlew :app:assembleRelease` only produces an installable APK when a
release keystore is configured — otherwise the build still succeeds (CI's
compile check relies on that) but emits `app-release-unsigned.apk`. Signing is
wired into the release build type and reads from either:

- a **`keystore.properties`** file at the repo root (gitignored):

  ```properties
  storeFile=path/to/waqfah-release.jks   # relative to the repo root, or absolute
  storePassword=…
  keyAlias=waqfah
  keyPassword=…
  ```

- or **environment variables** — `WAQFAH_STORE_FILE`, `WAQFAH_STORE_PASSWORD`,
  `WAQFAH_KEY_ALIAS`, `WAQFAH_KEY_PASSWORD` — which override the file, so CI
  can inject the secrets without writing it to disk.

With either source complete, `assembleRelease` emits a signed, installable
`app-release.apk`. A half-configured source — some properties set, others
missing — fails the build instead of quietly emitting an unsignable artifact.

Create a keystore once (keep it out of the repository and back it up safely —
losing the key means no future update can install over the released app):

```bash
keytool -genkeypair -v -keystore waqfah-release.jks -alias waqfah \
  -keyalg RSA -keysize 4096 -validity 10000
```

## Contributing

Bug reports, feature ideas, and pull requests are welcome — please open an
issue first for larger changes. If you want to work on the code,
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) is the map to start from.

To contribute a UI translation, copy
`app/src/main/res/values/strings.xml` into a new
`values-<language-code>/` folder, translate the strings, and open a pull
request. To publish a Quran translation database instead, see the next
section.

## Contributing a translation database

Downloadable translations are plain SQLite files hosted in the separate
[waqfah-translations][translations-repo] repository, fetched at runtime and
opened read-only by Room. A valid file must have:

- table `translations (verse_id INTEGER PRIMARY KEY, text TEXT NOT NULL)`,
  one row per ayah id (1–6236),
- `PRAGMA user_version` equal to `1` (or `0`),
- served over HTTPS; add the entry to `TranslationCatalog` with its URL and
  SHA-256 checksum.

Files are verified (SQLite header + schema + version + checksum) before
being accepted; anything else fails fast with an error shown on the download
row.

## Links

- Repository: [github.com/ShrekBytes/waqfah](https://github.com/ShrekBytes/waqfah)
- Issue tracker: [github.com/ShrekBytes/waqfah/issues](https://github.com/ShrekBytes/waqfah/issues)
- Contact: [shrekbytes@duck.com](mailto:shrekbytes@duck.com)

## License

Waqfah is free software: licensed under the
[GNU Affero General Public License v3.0](LICENSE).

[translations-repo]: https://github.com/ShrekBytes/waqfah-translations
