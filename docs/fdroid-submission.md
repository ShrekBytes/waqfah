# F-Droid submission

Working notes for getting Waqfah into the F-Droid main repository.

- **Application ID:** `com.shrekbytes.waqfah`
- **Reference:** <https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/>,
  <https://f-droid.org/docs/Inclusion_Policy/>
- **Metadata already in this repo:** `fastlane/metadata/android/en-US/`
  (short description, full description, icon, changelog `3.txt` for `versionCode 3`).

F-Droid's own advice is to package the app yourself with a merge request to
`fdroiddata`, which skips the RFP round trip. Both routes are drafted below.

---

## 1. `fdroiddata` metadata — `metadata/com.shrekbytes.waqfah.yml`

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

Builds:
  - versionName: 1.1.0
    versionCode: 3
    commit: 2a557871360b8d51fbb4840bb84fccd40c0bf99f
    gradle:
      - yes

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: 1.1.0
CurrentVersionCode: 3

AntiFeatures:
  - NonFreeAssets
```

Notes on the fields:

- `commit` is the **full hash** of the `v1.1.0` tag. Every release must be tagged
  upstream (`v<versionName>`) before a build block can reference it.
- `gradle: - yes` because the app has no product flavours.
- `UpdateCheckMode: Tags` + `AutoUpdateMode: Version` works because `versionName`
  and `versionCode` live in the standard `android { }` block of
  `app/build.gradle.kts` — no `UpdateCheckData` regex needed.
- `License` is `AGPL-3.0-only` (SPDX). `LICENSE` is the verbatim AGPL-3.0 text with
  no "or later" clause.
- `NonFreeAssets` is declared deliberately — the bKash logo is a trademarked
  asset. Declaring it honestly is better than being asked about it in review. It
  is the *only* remaining basis for the flag: the non-free font is gone and the
  bundled translations are Tanzil-covered. Drop the three payment icons and this
  line goes away entirely.
- Commit the metadata MR with the message `New App: com.shrekbytes.waqfah`.

## 2. RFP issue body (alternative route)

Open at <https://gitlab.com/fdroid/rfp/issues/new>:

```markdown
* [x] The app complies with the inclusion criteria.
* [x] The app is not already listed in the repo or issue tracker.
* [x] The app has not already been requested.
* [x] The upstream app source code repo contains the app metadata
      (summary/description/images/changelog/etc) in a Fastlane folder structure.
* [x] The original app author has been notified, and does not oppose the inclusion.

#### APPLICATION ID: com.shrekbytes.waqfah

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
```

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

* `NonFreeAssets` — the app bundles the bKash logo, a trademarked asset. The two
  bundled Arabic fonts (`amiri.ttf`, `digital_khatt_indopak.otf`) are both
  OFL-1.1.
* Both bundled translations are covered by Tanzil's terms: the Quran text license
  permits use in any application with attribution and a link to tanzil.net, and
  the translation terms permit non-commercial use (Waqfah is free and ad-free),
  with a backlink required only above three translations. Both credits are on the
  in-app Gratitude screen.
* Build toolchain is current: Gradle 9.5.0, AGP 9.3.2, `compileSdk`/`targetSdk` 37.
  `gradle/gradle-daemon-jvm.properties` pins a JDK 25 toolchain that Gradle
  downloads on first build; say the word if you would prefer a pinned local JDK
  instead and I will add it.
* Bundled `quran_core.db` is generated from upstream Quran text sources (Tanzil /
  Quranic Universal Library) and rebuilt each release; provenance can be
  documented on request.
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

**Still open:**

4. **Signing conflict.** The APK on GitHub Releases is self-signed with
   `CN=Waqfah Release, O=ShrekBytes, C=BD`
   (SHA-256 `f446c35fbe09a11a9851e55ccf7355718c02e44d90c4f2e8a02c2c249b679c1b`).
   F-Droid signs with its own key, so users cannot move between the two builds
   without uninstalling. Either stop publishing the self-signed APK, or set up
   reproducible builds and add `Binaries` + `AllowedAPKSigningKeys` to the
   metadata so F-Droid ships your signature.
5. **`ic_bkash.webp` is a trademarked logo** (the policy names trade marks
   explicitly), and `ic_nagad.webp` / `ic_rocket.webp` are unused but tracked —
   lint reports them as `UnusedResources`. Kept deliberately; expect
   `NonFreeAssets` unless they go.
6. **Document `quran_core.db` provenance.** A 12 MB binary blob with no in-repo
   generation script invites questions.
7. **Upload `bn/taisirul.db` to `waqfah-translations`.** It is not in that repo
   yet, so its new download URL 404s until it is. The pinned checksum is
   `d79f5fc49c072c6432f8521f5ad4dd2f742e98a8a94bad95672b45e6532112d9`; recover the
   exact bytes with `git show HEAD:app/src/main/assets/translations/bn/taisirul.db > taisirul.db`
   before deleting the asset, and upload that file unchanged.

## 4. Remaining manual step

`fastlane/metadata/android/en-US/images/phoneScreenshots/` is empty. F-Droid
expects at least two phone screenshots there (`1.png`, `2.png`). They have to come
from a real device or emulator run — capture the reading screen and the monitored
apps list, at least 320 px on the short edge.
