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
  NonFreeAssets:
    en-US: Bundles non-free payment logos and Tanzil Quran text/translations.
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
    subdir: app
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
top rather than the bottom, and `Categories` had to be sorted alphabetically.
The block above is the output of `fdroid rewritemeta`, and re-running that task
on it produces no diff.

**Watch the trailing newline.** It is the easiest part to lose and it fails the
job on its own. Pasting this block out of a rendered Markdown view often drops
the final newline, because the selection ends at the last visible character — so
the file lands in GitLab ending `CurrentVersionCode: 4` with no `\n`, and
`rewritemeta` reports:

```
-CurrentVersionCode: 4
\ No newline at end of file
+CurrentVersionCode: 4
```

Check with `tail -c1 metadata/dev.shrekbytes.waqfah.fdroid.yml | xxd` — it must
print `0a`. To repair in place, `sed -i -e '$a\' <file>` appends one only when
missing, so it is safe to run more than once. In GitLab's web editor, place the
cursor at the very end of the last line and press Enter before committing.

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
- `subdir: app` is required, and is the field that makes the build *finish*. With it,
  fdroidserver sets its root to `<repo>/app`, so its APK search lands on
  `app/build/outputs/apk/fdroid/release/`. Without it the search looks in
  `<repo>/build/outputs/apk`, which does not exist in a standard Android Studio
  layout, and the build dies after a successful Gradle run with
  `FileNotFoundError: ... 'build/dev.shrekbytes.waqfah.fdroid/build/outputs/apk'`.
  The same pattern appears in `com.keylesspalace.tusky`, which also has an `app/`
  module plus a flavour (`subdir: app` with `gradle: - blue`). It does not weaken
  the source scan: fdroidserver scans the whole build directory, not `root_dir`.
- `gradle: - fdroid` selects the `fdroid` flavour, so F-Droid runs
  `assembleFdroidRelease` and picks up the `.fdroid` application ID. The `play`
  flavour is not built here.
- `UpdateCheckMode: Tags` + `AutoUpdateMode: Version` works because `versionName`
  and `versionCode` live in the standard `android { }` block of
  `app/build.gradle.kts` — no `UpdateCheckData` regex needed. The flavours share
  one `versionCode`, which is fine: they are different application IDs.
- `License` is `AGPL-3.0-only` (SPDX). `LICENSE` is the verbatim AGPL-3.0 text with
  no "or later" clause.
- `NonFreeAssets` is declared deliberately, and it has **two independent bases**.
  The payment logos on the donation screen (`ic_bkash.webp`, `ic_nagad.webp`,
  `ic_rocket.webp`) are brand marks whose artwork carries no free licence. More
  importantly, the bundled Quran data is not free either: Tanzil's text licence
  is verbatim-only ("changing the text is not allowed" — i.e. No Derivatives) and
  its translation terms are non-commercial. F-Droid's definition of the flag names
  exactly those two restrictions (NC and ND), so this is the stronger basis.
  **Dropping the three icons would not clear the flag.** The trademark question is
  a separate matter, governed by the inclusion policy's "must not infringe third
  party rights … including trade marks" clause — see §3.
- **Keep the reason under ~69 characters.** fdroidserver's YAML dumper sets no
  line width, so it folds plain scalars at 80 columns; a longer reason is rewritten
  onto two lines and the `rewritemeta` job fails on the diff. See §5.
- **The reason is user-facing.** F-Droid renders it on the public app page under
  "This app contains non-free assets", so it is read by people deciding whether to
  install — not only by the reviewer. State what is non-free and why, and stop.
  The licence audit of everything *else* — the OFL fonts, the provenance of the
  bundled database — belongs in the MR description and in the repo docs, never in
  this field.
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


## 2. Merge request — title and description

Open against `fdroid/fdroiddata` from `ShrekBytes/fdroiddata:master`, using the
**App inclusion** template. The RFP issue route was not used.

**Title**

```
New app: Waqfah
```

**Description**

```markdown
## App

**Waqfah** — `dev.shrekbytes.waqfah.fdroid`. I am the author.

Waqfah shows a single Quranic ayah before the apps you choose. When you open a
monitored app a translucent reading screen appears first: read the ayah or skip
it, and you continue into the app. It does not block apps, set limits, or track
usage.

- **Reading** — sequential (resume at the lowest unread ayah) or random, with
  progress tracked across all 6,236 ayat. Indopak and Uthmani scripts, two
  bundled Arabic fonts, adjustable sizes, optional transliteration, and
  translations in English and Bengali with more available as downloads.
- **Control** — pick which apps trigger it; a per-app cooldown (or Off) sets the
  minimum gap between triggers.
- **Interface** — English or Bengali, seven themes, five accent colours.
- **No accounts, no servers, no ads, no analytics.** Everything works offline
  except an optional translation download, fetched over HTTPS and verified
  against pinned SHA-256 checksums.

Source: <https://github.com/ShrekBytes/waqfah>

## Checklist

### Policy

* [x] The app complies with the [inclusion criteria](https://f-droid.org/docs/Inclusion_Policy).
* [x] The original app author has been notified (and does not oppose the inclusion). — I am the author.
* [x] The upstream app source code repo contains the app metadata in a [Fastlane](https://gitlab.com/snippets/1895688) folder structure. — `fastlane/metadata/android/en-US/` has the summary, full description, a 512×512 icon, five phone screenshots, and `changelogs/4.txt` for versionCode 4.

### Docs

* [x] Please read [the guide](https://gitlab.com/fdroid/fdroiddata/-/blob/master/CONTRIBUTING.md) first if this is your first contribution.
* [x] Please make sure your metadata follows the best practice in [our templates](https://gitlab.com/fdroid/fdroiddata/tree/master/templates).
* [x] Please read the [Build Metadata Reference](https://f-droid.org/docs/Build_Metadata_Reference/) and make sure your metadata is valid.
* [x] Please read the [Quick Start Guide](https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/).

### Merge Request Setup

* [x] The title of this merge request should follow "New app: app name" format.
* [x] Please make sure your fdroiddata fork is public and your branch is not protected.
* [x] Please read [our Git guide](https://gitlab.com/fdroid/wiki/-/wikis/Tips-for-fdroiddata-contributors/Git-Usage) if you don't know how to rebase your branch. Don't rebase your branch if there is no conflict. — not rebased; no conflict.
* [ ] All related [fdroiddata](https://gitlab.com/fdroid/fdroiddata/issues) and [RFP issues](https://gitlab.com/fdroid/rfp/issues) have been referenced in this merge request — none exist for this app.
* [x] Please only submit one app in one MR.

### Metadata

* [x] Metadata must be put in `metadata/<applicationId>.yml`. — `metadata/dev.shrekbytes.waqfah.fdroid.yml`
* [x] Metadata must be a valid YAML file.
* [x] Metadata must use LF as line ending.
* [x] Don't add summary/description/changelog/images or anything that should be provided in upstream repo. — the MR adds exactly one file; the Changes tab has nothing else.
* [x] Releases are tagged and auto update is enabled unless there is a special reason. — `v2.0.0` tagged upstream; `UpdateCheckMode: Tags` and `AutoUpdateMode: Version`.
* [x] There is an issue tracker and contact info of the author.
* [x] An AuthorName must be added. It doesn't need to be the real name.
* [x] External repos are added as git submodules instead of srclibs. — n/a, no external repos.
* [ ] Enable [Reproducible Builds](https://f-droid.org/docs/Reproducible_Builds). — **not enabled; reason below.**
* [ ] Setup abi split if the APK is large and the splitted ones can be much smaller. — n/a, the APK is about 6.5 MB.
* [x] Only the latest versions should be kept in the metadata before it's merged.
* [x] Don't add any disabled versions in the metadata.
* [x] The `commit` field should be the full hash. Please don't use tag or branch in commit.

### Pipeline

* [x] All pipelines should pass.
* [x] All warnings and errors in the Reports tab should be fixed or explained.
* [x] F-Droid CI runners are under GitLab's FOSS program, so there's no need for you to pay for any CI time.

## Notes for the reviewer

**Reproducible Builds are deliberately not enabled.** The two store channels use
different application IDs on purpose — `dev.shrekbytes.waqfah` for Google Play,
`dev.shrekbytes.waqfah.fdroid` for F-Droid — so F-Droid's signing key never has
to match the Play key and there is no published signature to reuse. F-Droid
should sign with its own key.

**`NonFreeAssets` is declared deliberately**, and it covers two things. The
donation screen bundles the bKash, Nagad and Rocket payment logos, used
nominatively to indicate which methods are accepted; the artwork itself is not
under a free licence. The bundled Quran text and translations come from the
Tanzil Project, whose terms permit verbatim redistribution only ("changing the
text is not allowed") and non-commercial use of translations — the NC and ND
restrictions the flag exists for.

The provenance of `quran_core.db` is written up in `docs/quran-core-db.md`,
including a verse-by-verse check showing the Uthmani column is Tanzil's text
verbatim; both the text and the translations are credited on the in-app
Gratitude screen, with a link to tanzil.net.

Everything else bundled is freely licensed: both Arabic fonts — Amiri and
Digital Khatt Indopak — are OFL-1.1, and their licence texts ship inside the APK
in `app/src/main/assets/licenses/`.

**Build notes.** Two product flavours select the store channel; `subdir: app` is
required because the Android module lives in `app/`. `compileSdk`/`targetSdk` 37,
`minSdk` 28, Gradle 9.5.0, AGP 9.3.2, Kotlin 2.2.10.
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

1. **The payment logos stay, but they are not the basis for the flag.**
   `ic_bkash.webp` is a brand mark whose artwork is under no free licence, and the
   app ships it deliberately: using a payment brand's mark to say which method is
   accepted is ordinary nominative use, and the brand recognition is worth more on
   a donate screen than a clean listing. The flag rests on the *licence* position
   (no free licence for the artwork, plus Tanzil's ND/NC terms — see §1), **not** on
   the trademark. F-Droid's `NonFreeAssets` definition is licence-based and never
   mentions trade marks; trade marks live in the inclusion policy's
   "must not infringe third party rights" clause, which the flag does not satisfy
   or excuse. So the logo is not what makes the flag correct, and removing it
   would not remove the flag.
   `ic_nagad.webp` and `ic_rocket.webp` are placeholders for accounts that will
   be added later, so they stay too. Lint reports both as `UnusedResources` until
   the rows referencing them are uncommented — two warnings, neither fatal, and
   resource shrinking already keeps them out of the release APK.

**Done:**

1. ~~Upload `bn/taisirul.db` to `waqfah-translations`~~ — **uploaded and
   verified.** The file is now in that repo (5,062,656 bytes) and its SHA-256 is
   `d79f5fc49c072c6432f8521f5ad4dd2f742e98a8a94bad95672b45e6532112d9`, matching the
   pinned value exactly, so the catalog's download path resolves.
2. ~~Phone screenshots~~ — **installed.** Five 1080×2400 PNGs at
   `fastlane/metadata/android/en-US/images/phoneScreenshots/1.png` … `5.png`
   (reading screen, monitored apps, the pause over another app, settings, surah
   list).
3. ~~Stale APKs~~ — deleted. The pre-flavour `app-debug.apk` and
   `app-release.apk` still carried the dead `com.shrekbytes.waqfah` ID and would
   never have been regenerated.


## 4. Remaining steps

Everything the repo has to supply is in place: metadata, screenshots, a clean
licence position, and `v2.0.0` tagged at `160271e`. That tag has been verified to
build the way the buildserver builds it — a clean clone with no
`keystore.properties` and no `local.properties`, JDK 21 only, and Gradle's
toolchain auto-download disabled, produces `app-fdroid-release-unsigned.apk`.

On the fork the metadata is in place and **the pipeline is fully green** — all
nine jobs pass, `fdroid build` and `check apk` included.

What is left:

1. ~~Push `main` and the re-pointed tag~~ — **done.** The remote tag peels to
   `160271e`.
2. ~~Get the fork's pipeline green~~ — **done.** Four failures, each in a
   different layer; see §5.
3. ~~Open the merge request~~ — **done.** [!50834](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/50834),
   title `New app: Waqfah`, source `ShrekBytes/fdroiddata` `master` →
   `fdroid/fdroiddata` `master`, not a Draft. The Reproducible Builds item is
   answered in the description rather than enabled: the two channels use different
   application IDs on purpose, so F-Droid's signing key never has to match the Play
   key and there is no published signature to reuse.
4. **Answer the first review round — this is the outstanding action.** linsui
   (2026-10-02) asked for two things, neither of which needs a rebase:
   - *"Please enable 'Squash commits'."* — tick **Squash commits when merge
     request is accepted** in the MR edit page. This supersedes the older "do not
     squash" advice that used to sit here; the reviewer asked for it directly.
   - *"Add the reason."* — an inline comment anchored to line 2 of the metadata,
     i.e. the `NonFreeAssets` line. Replace it with the reason shown in §1,
     keeping it under ~69 characters so `rewritemeta` does not fold it (§5).
   Commit to the fork's `master`; the MR picks it up on its own. No force-push.
5. Wait for a packager. Review happens in the MR, not by email. Once merged, the
   app takes roughly 24–48 hours to appear, because signing is a human step.

## 5. Pipeline runs — what failed, and why

Three separate problems surfaced across the first two pipelines on the fork
(`ShrekBytes/fdroiddata`). All are fixed upstream. Everything else passed on
those runs — `fdroid lint`, `checkupdates`, `check source code`,
`schema validation`, `tools check scripts` — so the metadata itself was sound
each time. Each failure was in a different layer: metadata formatting, a
build-time dependency, then the build output path.

### `fdroid rewritemeta` — key order, then the trailing newline

Purely cosmetic to a human, fatal to the job. `fdroidserver` compares the
committed file against its own canonical serialisation and fails if they
differ. Three deltas on the first attempt: `AntiFeatures` belonged at the top,
not the bottom; `Categories` had to be alphabetical (`Reading` before
`Religion`); and the file needed a trailing newline.

It took two rounds, because only the first two were fixed and the newline was
still missing on the second run — the job then reported a single one-line hunk
whose only content was `\ No newline at end of file`. See the warning in §1: the
newline is the easiest part to lose when pasting from a rendered Markdown view,
and it fails the job by itself.


### `fdroid rewritemeta`, again — line folding

Adding the `NonFreeAssets` reason on 2026-10-02 brought the same job back for a
third reason. fdroidserver's dumper (`ruamel.yaml` with `indent(mapping=2,
sequence=4, offset=2)`) sets no line width, so it folds plain scalars at 80
columns. A reason of 106 characters was rewritten as two lines — with a trailing
space after the break, which is easy to lose when copying — and the job would have
failed on the diff even though the YAML was valid.

Reproduced locally against `ruamel.yaml` with the same settings: the break lands at
the last space that still fits within 80 columns. Since `    en-US: ` occupies 11 of
those, **a reason of roughly 69 characters or less stays on one line**; 74 already
folds. The committed reason is 66 characters and produces no diff.

Shortening the reason is the cheap fix. There is no local `fdroid` binary in this
project and no `fdroiddata` clone, so reproducing the folded bytes by hand — trailing
space included — is not worth it. If a long reason is ever genuinely needed, run
`fdroid rewritemeta` and commit its output rather than hand-wrapping.


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


### `fdroid build`, second attempt — `subdir: app`

The second pipeline got much further: **Gradle itself succeeded**, compiling and
packaging the APK in 4m 12s. The job still failed, in fdroidserver's post-build
step:

```
BUILD SUCCESSFUL in 4m 12s
ERROR: Could not build app dev.shrekbytes.waqfah.fdroid due to unknown error:
FileNotFoundError: [Errno 2] No such file or directory:
'build/dev.shrekbytes.waqfah.fdroid/build/outputs/apk'
```

`fdroidserver/build.py` searches for the artifact under
`<root_dir>/build/outputs/apk/…`, where `root_dir` is the build directory unless
`subdir` is set. In a standard Android Studio layout the module lives in `app/`,
so the real artifact is at `<repo>/app/build/outputs/apk/fdroid/release/` and the
search path never exists. Worse, the flavour branch calls `os.listdir()` on that
missing directory without guarding it, so a *successful* build is reported as an
unknown error.

`subdir: app` is the fix, and it is the standard one — `com.keylesspalace.tusky`
uses exactly this combination (`subdir: app` plus a flavour) for the same reason.
Verified by replaying fdroidserver's own lookup logic against a real build tree:
without `subdir` it raises the same `FileNotFoundError` on the same path; with
`subdir: app` it resolves to
`app/build/outputs/apk/fdroid/release/app-fdroid-release.apk`.

`output:` would also work — setting it switches fdroidserver to its `raw` output
method, which globs an explicit path instead — but `subdir: app` is the
convention reviewers will recognise.
