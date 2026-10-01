# F-Droid submission

Working notes for getting Waqfah into the F-Droid main repository.

- **Application ID (F-Droid build):** `dev.shrekbytes.waqfah.fdroid`
- **Application ID (Play build):** `dev.shrekbytes.waqfah`
- **Reference:** <https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/>,
  <https://f-droid.org/docs/Inclusion_Policy/>
- **Metadata already in this repo:** `fastlane/metadata/android/en-US/`
  (short description, full description, icon, changelog `4.txt` for `versionCode 4`).
- **Release being submitted:** `2.0.0` / `versionCode 4` — the first version with
  the flavour split, so it is the first one F-Droid can actually build.

The two store channels are separate Gradle flavours with separate application
IDs, so both can be installed side by side. F-Droid signs its own build with its
own key, which can never match the Play signing key — the IDs must differ or
neither build could ever update over the other. Note that this also means the
two builds keep separate data, and switching between them requires an uninstall.

F-Droid's own advice is to package the app yourself with a merge request to
`fdroiddata`, which skips the RFP round trip. Both routes are drafted below.

---


## 1. `fdroiddata` metadata — `metadata/dev.shrekbytes.waqfah.fdroid.yml`

```yaml
AntiFeatures:
  - NonFreeAssets
Categories:
  - Reading
  - Religion
License: AGPL-3.0-only
AuthorName: ShrekBytes
AuthorEmail: shrekbytes@duck.com
SourceCode: https://github.com/ShrekBytes/waqfah
IssueTracker: https://github.com/ShrekBytes/waqfah/issues

AutoName: Waqfah

RepoType: git
Repo: https://github.com/ShrekBytes/waqfah.git

Builds:
  - versionName: 2.0.0
    versionCode: 4
    commit: 160271ebe38a57826b620be0bb1136aa5612e666
    gradle:
      - fdroid

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: 2.0.0
CurrentVersionCode: 4
```

The key order is not cosmetic. The fork's `fdroid rewritemeta` job fails the
pipeline if the file is not already in fdroidserver's canonical order, and the
first submission was rejected for exactly that: `AntiFeatures` belonged at the
top rather than the bottom, `Categories` had to be sorted alphabetically, and
the file needed a trailing newline. The block above is the output of
`fdroid rewritemeta`, and re-running that task on it produces no diff.

Notes on the fields:

- `commit` is the **commit** the annotated `v2.0.0` tag points at — not the tag
  object, and not the tag name. Beware: for an annotated tag, `git rev-parse v2.0.0`
  returns the *tag object* (`git cat-file -t` says `tag`), and F-Droid cannot check
  that out. Use the peeled form:

  ```bash
  git rev-parse v2.0.0^{commit}   # -> 160271ebe38a57826b620be0bb1136aa5612e666
  git rev-list -n 1 v2.0.0        # same thing
  ```

  `v2.0.0` was re-pointed twice before submission, both times because a build
  fix had to live inside the tagged commit: first from `31f92fd` to `4f99d70`
  (the JDK 25 daemon toolchain pin, see §2), then to `160271e` (dropping the
  foojay toolchain plugin that fdroidserver's suss scanner blocks, see §5). The
  tag is annotated in every case, so the peeled form is the only safe way to
  read it.

  Every release must be tagged upstream (`v<versionName>`) before a build block
  can reference it, and the tag must be created only once the repo is final —
  F-Droid reads the fastlane metadata from the tagged source, so tagging before
  the screenshots are committed would bake in a release without them.
- `gradle: - fdroid` selects the `fdroid` flavour, so F-Droid runs
  `assembleFdroidRelease` and picks up the `.fdroid` application ID. The `play`
  flavour is not built here.
- `UpdateCheckMode: Tags` + `AutoUpdateMode: Version` works because `versionName`
  and `versionCode` live in the standard `android { }` block of
  `app/build.gradle.kts` — no `UpdateCheckData` regex needed. The flavours share
  one `versionCode`, which is fine: they are different application IDs.
- `License` is `AGPL-3.0-only` (SPDX). `LICENSE` is the verbatim AGPL-3.0 text with
  no "or later" clause.
- `NonFreeAssets` is declared deliberately — the bKash logo is a trademarked
  asset. Declaring it honestly is better than being asked about it in review. It
  is the *only* remaining basis for the flag: the non-free font is gone and the
  bundled translations are Tanzil-covered. Drop the three payment icons and this
  line goes away entirely.
- Commit the metadata MR with the message `New App: dev.shrekbytes.waqfah.fdroid`.

This file has been checked with F-Droid's own tooling, not just by eye. `fdroid lint`
passes with no findings, and `fdroid checkupdates` was run against the real
repository: given deliberately wrong `CurrentVersion`/`CurrentVersionCode` values it
cloned the repo, walked the tags, and corrected them to `2.0.0 (4)` — so
`UpdateCheckMode: Tags` + `AutoUpdateMode: Version` genuinely work, and future
releases only need tagging to be picked up.

One note if you re-run it: fdroidserver rewrites the file with keys in alphabetical
order (`AntiFeatures` first, `Categories` sorted). That is normalisation, not an
error.


## 2. RFP issue body (alternative route)

Open at <https://gitlab.com/fdroid/rfp/issues/new>:

````markdown
* [x] The app complies with the inclusion criteria.
* [x] The app is not already listed in the repo or issue tracker.
* [x] The app has not already been requested.
* [x] The upstream app source code repo contains the app metadata
      (summary/description/images/changelog/etc) in a Fastlane folder structure.
* [x] The original app author has been notified, and does not oppose the inclusion.

#### APPLICATION ID: dev.shrekbytes.waqfah.fdroid

```yaml
Categories:
  - Religion
  - Reading
License: AGPL-3.0-only
AuthorName: ShrekBytes
AuthorEmail: shrekbytes@duck.com
SourceCode: https://github.com/ShrekBytes/waqfah
IssueTracker: https://github.com/ShrekBytes/waqfah/issues
AutoName: Waqfah
RepoType: git
Repo: https://github.com/ShrekBytes/waqfah.git
````

Why do you want this app added to F-Droid: I am the author. Waqfah is free
software under AGPL-3.0 with no ads, no tracking, and no proprietary
dependencies, and it is useful precisely to people who deliberately run
de-Googled devices, so F-Droid is where it belongs.

Summary: Read a Quranic ayah before the apps you choose to open

Description: Waqfah shows a single Quranic ayah before the apps you choose.
When you open a monitored app, the reading screen appears first; read the ayah
or skip it, then continue into the app. It does not block apps, set limits, or
track usage. There are no accounts and no servers — everything works offline
except the optional download of an extra translation. Reading can be sequential
or random, with progress tracked across the whole Quran, and the Arabic text can
be shown in Indopak or Uthmani script with several bundled fonts and adjustable
sizes. Translations and transliteration are optional and available in English or
Bengali, with more available as downloads. The interface is available in English
and Bengali, or follows the system language.

#### Notes for the reviewer

- `NonFreeAssets` — the app bundles the bKash logo, a trademarked asset. The two
  bundled Arabic fonts (`amiri.ttf`, `digital_khatt_indopak.otf`) are both
  OFL-1.1.
- Both bundled translations are covered by Tanzil's terms: the Quran text license
  permits use in any application with attribution and a link to tanzil.net, and
  the translation terms permit non-commercial use (Waqfah is free and ad-free),
  with a backlink required only above three translations. Both credits are on the
  in-app Gratitude screen.
- Build toolchain is current: Gradle 9.5.0, AGP 9.3.2, `compileSdk`/`targetSdk` 37.
  `gradle/gradle-daemon-jvm.properties` pins the daemon JVM to 21. The buildserver
  is Debian trixie with `default-jdk-headless`, i.e. JDK 21, and sets
  `org.gradle.java.installations.auto-download=false`, so the pin must not exceed
  the JDK the image already ships — a newer pin fails there with "Toolchain
  auto-provisioning is not enabled" while a local build hides it, because Gradle
  silently downloads the JDK. `compileSdk` 37 is not a problem: the buildserver
  leaves `platforms/` and `build-tools/` group-writable so Gradle fetches it.
- Bundled `quran_core.db` is read-only Quran text data rebuilt each release. Its
  contents and provenance are written up in `docs/quran-core-db.md`, including a
  verse-by-verse check showing the Uthmani column is Tanzil's text verbatim.

```
```


## 3. Asset and licensing status

F-Droid's inclusion policy treats anti-features as *labels*, not disqualifiers
("without necessarily disqualifying applications from inclusion"). The hard line
is elsewhere: "All assets need to have valid legal licenses or be in the public
domain while being free of copyright infringement", and apps "must not infringe
third party rights, including ... copyright and trade marks."

**Resolved:**

1. ~~`mequran.ttf`~~ — **deleted.** Tanzil distributes me_quran for "personal and
   non-commercial use", which does not clearly grant redistribution. The app now
   ships two Arabic faces, both OFL-1.1.
2. ~~Bundled translations~~ — **swapped.** `taisirul` (a commercially published
   book, not distributed by Tanzil) moved to the download-only list and
   `muhiuddinkhan` became the bundled Bengali entry. `sahih` stays bundled:
   Tanzil's translation terms permit non-commercial use in applications, require
   a backlink only above three translations, and the Gratitude screen already
   links Tanzil. `quran_core.db` is covered by Tanzil's text license, which
   grants use in "any website or application" with attribution and a link.
3. ~~OFL text for the bundled fonts~~ — **added.** Both `amiri.ttf` and
   `digital_khatt_indopak.otf` are OFL-1.1, which requires the notice to travel
   with the copies. `app/src/main/assets/licenses/` now ships each font's
   upstream OFL file verbatim, so they are inside the APK as well as the repo.
4. ~~`quran_core.db` provenance~~ — **documented.** `docs/quran-core-db.md`
   records the schema, the verified invariants, and a verse-by-verse check
   proving the Uthmani column is Tanzil's text verbatim (5,925 byte-identical,
   311 fully explained, 0 unexplained). It also states plainly that no generator
   script exists in the repo.
5. ~~Signing conflict~~ — **resolved by splitting the channels.** The GitHub
   Releases APK is being dropped, and the two store builds now carry different
   application IDs (`dev.shrekbytes.waqfah` for Play, `dev.shrekbytes.waqfah.fdroid`
   for F-Droid), so F-Droid's signing key and the Play signing key never have to
   agree. No reproducible-build setup is needed.
   Two consequences to keep in mind: the v1.1.0 APK on GitHub Releases was
   `com.shrekbytes.waqfah`, an ID nothing will use again — anyone who installed it
   must reinstall and cannot carry their data across. And the two store builds are
   separate apps to Android, so Play users and F-Droid users each keep their own
   monitored-app list and reading progress.

**Decided — accepted, no action:**

1. **`ic_bkash.webp` is a trademarked logo, and the app ships it deliberately.**
   The policy names trade marks explicitly, so the metadata declares
   `NonFreeAssets` and the badge is expected. That is a filterable label rather
   than a rejection, and using a payment brand's mark to say which method is
   accepted is ordinary nominative use — plenty of F-Droid apps carry this badge.
   The brand recognition is worth more on a donate screen than a clean listing,
   so the logo stays. If it is ever dropped, remove the `AntiFeatures:` block in
   §1 as well: it is the only remaining basis for the flag.
   `ic_nagad.webp` and `ic_rocket.webp` are placeholders for accounts that will
   be added later, so they stay too. Lint reports both as `UnusedResources` until
   the rows referencing them are uncommented — two warnings, neither fatal, and
   resource shrinking already keeps them out of the release APK.

**Done:**

2. ~~Upload `bn/taisirul.db` to `waqfah-translations`~~ — **uploaded and
   verified.** The file is now in that repo (5,062,656 bytes) and its SHA-256 is
   `d79f5fc49c072c6432f8521f5ad4dd2f742e98a8a94bad95672b45e6532112d9`, matching the
   pinned value exactly, so the catalog's download path resolves.
3. ~~Phone screenshots~~ — **installed.** Five 1080×2400 PNGs at
   `fastlane/metadata/android/en-US/images/phoneScreenshots/1.png` … `5.png`
   (reading screen, monitored apps, the pause over another app, settings, surah
   list).
4. ~~Stale APKs~~ — deleted. The pre-flavour `app-debug.apk` and
   `app-release.apk` still carried the dead `com.shrekbytes.waqfah` ID and would
   never have been regenerated.

## 4. Remaining steps

Everything the repo has to supply is in place: metadata, screenshots, a clean
licence position, and `v2.0.0` tagged at `160271e`. That tag has been verified to
build the way the buildserver builds it — a clean clone with no
`keystore.properties` and no `local.properties`, JDK 21 only, and Gradle's
toolchain auto-download disabled, produces `app-fdroid-release-unsigned.apk`.
What is left:

1. ~~Push `main` and the re-pointed tag~~ — **done.** The remote tag peels to
   `160271e`. The force-push was needed because the tag was moved: a tag that
   exists only locally, or that still points at the old commit on the remote, is
   invisible to or wrong for F-Droid.
2. ~~Open the `fdroiddata` merge request~~ — **opened.** See §5 for what the
   first pipeline run rejected.
3. Expect review feedback in that MR rather than by email. Once merged, the app
   takes roughly 24–48 hours to appear, because signing is a human step.

## 5. First pipeline run — what failed, and why

The first pipeline on the fork (`ShrekBytes/fdroiddata`, pipeline 2902336999)
failed two jobs. Both are now fixed upstream. `fdroid lint`, `checkupdates`,
`check source code`, `schema validation` and `tools check scripts` all passed
on the same run, so the metadata itself was sound — the problems were
formatting and one build-time dependency.

### `fdroid rewritemeta` — key order

Purely cosmetic to a human, fatal to the job. `fdroidserver` compares the
committed file against its own canonical serialisation and fails if they
differ. Three deltas: `AntiFeatures` belonged at the top, not the bottom;
`Categories` had to be alphabetical (`Reading` before `Religion`); and the file
needed a trailing newline. The block in §1 is the corrected form — the output
of `fdroid rewritemeta`, which is idempotent on it.

### `fdroid build` — the foojay toolchain plugin

The job aborted during source scanning, before compiling anything:

```
ERROR: Found usual suspect 'org.gradle.toolchains.foojay-resolver' at settings.gradle.kts
ERROR: Could not build app dev.shrekbytes.waqfah.fdroid: Can't build due to 1 error while scanning
```

`fdroidserver`'s scanner checks every `.gradle`/`.gradle.kts` line against
[`suss.json`](https://fdroid.gitlab.io/fdroid-suss/suss.json), a list of
components that fetch code at build time. The entry for
`org.gradle.toolchains.foojay-resolver-convention` is Apache-2.0, so this is
not a licensing problem — it is that the plugin downloads a JDK from the
network mid-build, which the isolated buildserver will not do. The plugin has
been removed from `settings.gradle.kts`.

It was redundant: nothing in the project declares a `java { toolchain { … } }`
requirement, and Gradle's own daemon JVM criteria provisions the daemon JDK from
the `toolchainUrl` entries in `gradle/gradle-daemon-jvm.properties` without any
plugin. Verified by building a minimal project containing only that file, with
no plugin and an empty `GRADLE_USER_HOME`: Gradle fetched JDK 21 unaided.

Two things worth knowing:

- `scanignore` could in principle list `settings.gradle.kts` to silence the
  scanner, but that suppresses the check rather than resolving it. Reviewers
  would rightly object. Do not do it.
- The scanner also strips `gradle/gradle-daemon-jvm.properties` and
  `gradle/wrapper/gradle-wrapper.jar` from the source tree before building, and
  uses its own `gradlew-fdroid`. So the daemon pin does not apply on the
  buildserver at all — it matters for local and CI builds, not for F-Droid's.
