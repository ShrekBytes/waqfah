# Waqfah

<p align="center">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" width="210" alt="The reading screen: an ayah in Arabic script with transliteration and an English translation, above a Mark Read button">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" width="210" alt="The same reading screen shown over another app, with a button to continue into that app">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" width="210" alt="The Apps screen: a searchable list of installed apps with toggles, and a Wait before showing again control">
</p>

<p align="center">
  <a href="https://github.com/ShrekBytes/waqfah/actions/workflows/android-ci.yml"><img src="https://github.com/ShrekBytes/waqfah/actions/workflows/android-ci.yml/badge.svg" alt="Android CI status"></a>
  <img src="https://img.shields.io/badge/license-AGPL--3.0--only-blue" alt="License: AGPL-3.0-only">
  <img src="https://img.shields.io/badge/platform-Android%209%2B-3DDC84" alt="Platform: Android 9 or newer">
</p>

**Waqfah** (وقفة — "a pause") shows a single Quranic ayah before the apps you
choose. When you open a monitored app — a social network, a game, a calculator,
any app at all — Waqfah's reading screen appears first. Read the ayah or skip
it, continue into the app, and get on with your day.

That's the whole idea. Waqfah isn't trying to stop you from doing anything, fix
a habit, or change how you use your phone. There's no blocking, no limits, no
tracking, no accounts, and no servers — just an ayah, shown before a selected
app opens.

## Features

**Reading**

- Sequential (resume at the lowest unread ayah) or random (any unread ayah),
  with progress tracked across all 6,236 ayat.
- Indopak and Uthmani scripts, two bundled Arabic fonts, and adjustable text
  sizes.
- Optional transliteration, plus translations in English and Bengali — more
  available as downloads.
- Browse every surah with per-surah read counts, and jump to any ayah. Your
  sequential or random position stays where it was.

**Apps and timing**

- Pick exactly which apps Waqfah appears before.
- A per-app cooldown — or Off — sets the minimum gap before it appears again
  for that app.
- At most one reading screen per app open. Share-sheet and "Open with" entries
  never trigger it.

**Appearance and language**

- Seven themes: system default, Light, Dark, Cream, Stone, Midnight, Indigo.
- Five accent colours — Sage, Clay, Slate, Plum, Ochre — for the three base
  themes.
- Interface in English or Bengali, or follow the system language.

## Download

Waqfah is coming soon — in shaa Allah — to F-Droid and Google Play. The store
listings aren't live yet, so there is nothing to link to until they are. Until
then you can [build it yourself](#building).

The two channels ship separate builds with separate application IDs
(`dev.shrekbytes.waqfah` for Google Play, `dev.shrekbytes.waqfah.fdroid` for
F-Droid), so both can be installed side by side. Because F-Droid signs with its
own key, the two builds are not interchangeable — switching between them means
uninstalling first, and each keeps its own data.

## Privacy

Waqfah collects nothing and sends nothing anywhere. Monitored apps, reading
progress, and preferences stay on the device.

There are no ads, no analytics, no trackers, and no accounts. The only network
traffic is downloading an optional Quran translation that you explicitly
request — fetched over HTTPS from the [waqfah-translations][translations-repo]
repository and verified against pinned SHA-256 checksums before use.

Waqfah also cannot read what's inside other apps: it has no accessibility or
screen-content permissions. Usage access only tells it *which* app moved to the
foreground — never what's displayed in it.

The one exception to "nothing leaves your device" is Android's own device
backup. If it's turned on, Android includes Waqfah's data in your device
backup, stored in your Google account or transferred directly to a new device.
Waqfah itself never sends anything anywhere.

## How it works

While the screen is on, a foreground service checks which app moved to the
foreground roughly once per second via the public Usage Stats API, and shows a
translucent reading screen over the monitored app when it opens. Dismissing the
reading screen simply falls through into that app, exactly where you left off.

Consecutive foreground events are paired so indirect entries — share sheets,
"Open with", link grabbers — never count as an app open.

## Permissions

Waqfah asks for two permissions during onboarding. Everything else is either
optional or declared by the system on its behalf.

**Required**

| Permission | Why |
|---|---|
| Usage access (`PACKAGE_USAGE_STATS`) | See which app moved to the foreground, so the reading screen appears at the right moment. Checked via AppOps; granted through system settings. |
| Display over other apps (`SYSTEM_ALERT_WINDOW`) | Let the reading screen appear over the opening app from the background on Android 10+. |

**Recommended** — optional, and denying them never blocks anything

| Setting / permission | Why |
|---|---|
| Unrestricted battery *(a system setting, not a permission)* | Stops aggressive OEM battery managers from killing the background monitor. Waqfah deep-links you to its own system app page, where you choose Battery → Unrestricted — no special permission is requested or needed. |
| Notifications (`POST_NOTIFICATIONS`) | Keeps the mandatory foreground-service notification visible on Android 13+. |

**Declared by the system or implied by the above**

| Permission | Why |
|---|---|
| Internet (`INTERNET`) | Only used for downloading optional translations; no other requests are made. |
| Run at startup (`RECEIVE_BOOT_COMPLETED`) | Restarts the monitor after reboot, but only if Waqfah is toggled on and its permissions are still granted. |
| Foreground service (`FOREGROUND_SERVICE_SPECIAL_USE`) | Android's required mechanism for the continuous background monitor; it runs behind a silent, lowest-priority notification. |

## Building

Requirements: Android SDK platform 37 and AGP 9.x-compatible tooling, such as a
current Android Studio. Gradle provisions its own daemon JDK, pinned in
`gradle/gradle-daemon-jvm.properties`. minSdk 28 (Android 9) · targetSdk 37.

```bash
./gradlew :app:assemblePlayDebug      # Play debug APK
./gradlew :app:assembleFdroidDebug    # F-Droid debug APK
./gradlew :app:assemblePlayRelease    # Play release APK
./gradlew :app:testPlayDebugUnitTest  # unit tests
```

The two flavours are `play` and `fdroid`; every variant task is prefixed with
one of them, so `assembleDebug` and `testDebugUnitTest` no longer exist.

Built with Kotlin and Jetpack Compose (Material 3); persistence via Room and
DataStore; dependency injection with Hilt.

### Releasing

`./gradlew :app:assemblePlayRelease` only produces an installable APK when a
release keystore is configured — otherwise the build still succeeds (CI's
compile check relies on that) but emits an unsigned APK. Signing is wired into
the release build type and reads from either:

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

With either source complete, the release task emits a signed, installable APK at
`app/build/outputs/apk/<flavour>/release/`. A half-configured source — some
properties set, others missing — fails the build instead of quietly emitting an
unsignable artifact.

That keystore is the **Google Play** signing key. The F-Droid build is not
signed with it: F-Droid builds from source with no keystore present and signs
the result with its own key, which is why the two flavours carry different
application IDs.

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

- **Issue tracker:** [github.com/ShrekBytes/waqfah/issues](https://github.com/ShrekBytes/waqfah/issues)
- **Contact:** [shrekbytes@duck.com](mailto:shrekbytes@duck.com)

### Translating the interface

Copy `app/src/main/res/values/strings.xml` into a new `values-<language-code>/`
folder, translate the strings, and open a pull request.

### Contributing a translation database

Downloadable translations are plain SQLite files hosted in the separate
[waqfah-translations][translations-repo] repository, fetched at runtime and
opened read-only by Room. A valid file must have:

- table `translations (verse_id INTEGER PRIMARY KEY, text TEXT NOT NULL)`,
  one row per ayah id (1–6236),
- `PRAGMA user_version` equal to `1` (or `0`),
- served over HTTPS; add the entry to `TranslationCatalog` with its URL and
  SHA-256 checksum.

Files are verified (SQLite header + schema + version + checksum) before being
accepted; anything else fails fast with an error shown on the download row.

## Credits

Waqfah's own code is AGPL-3.0. It ships and depends on several third-party
works:

| Work | Used for | Licence |
|---|---|---|
| [Amiri](https://github.com/aliftype/amiri) | Uthmani Arabic font | SIL OFL 1.1 |
| [Digital Khatt Indopak](https://github.com/DigitalKhatt/indopakfont) | Indopak Arabic font | SIL OFL 1.1 |
| [Tanzil Project](https://tanzil.net) | Quran text and bundled translation | Use in any application, with attribution and a link to tanzil.net |

Both fonts' full licence texts ship inside the APK in
[`app/src/main/assets/licenses/`](app/src/main/assets/licenses/FONTS.md), as
OFL-1.1 requires.

The bundled Quran text and translations — Sahih International (English) and
Muhiuddin Khan (Bengali) — are used under the Tanzil Project's terms, which
permit use in any application with attribution and a link to tanzil.net. The
in-app Gratitude screen carries both.

## License

Waqfah is free software: licensed under the
[GNU Affero General Public License v3.0](LICENSE).

[translations-repo]: https://github.com/ShrekBytes/waqfah-translations
