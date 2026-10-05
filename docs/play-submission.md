# Play Store submission

Working notes for getting Waqfah into Google Play.

- **Application ID (Play build):** `dev.shrekbytes.waqfah`
- **Application ID (F-Droid build):** `dev.shrekbytes.waqfah.fdroid`
- **Developer account:** **personal**, created after 2023-11-13 — so the
  12-tester / 14-day closed test is required before production access, and it
  is the critical path. See §6.
- **Privacy policy (live):** <https://shrekbytes.github.io/waqfah/>
- **Reference:** <https://support.google.com/googleplay/android-developer/>,
  <https://developer.android.com/distribute/best-practices/launch/launch-checklist>,
  <https://developer.android.com/google/play/requirements/target-sdk>
- **Metadata already in this repo:** `fastlane/metadata/android/en-US/` — title,
  short description, full description, icon, feature graphic, five phone
  screenshots, and changelog `4.txt` for `versionCode 4`.
- **Release being submitted:** `2.0.0` / `versionCode 4`.

The two store channels are separate Gradle flavours with separate application
IDs, so both can be installed side by side. Nothing in this document affects
the F-Droid submission: that reads its metadata from the tagged commit
`160271e` (`v2.0.0`), not from `main`, so editing the fastlane files here
changes what *future* F-Droid releases get, not the version under review.

---

## 1. Store listing assets

Play's listing requirements differ from F-Droid's, and the fastlane layout is
shared, so every asset below has to satisfy both.

| Asset | Requirement | State |
| --- | --- | --- |
| App title | ≤ 30 chars | `title.txt` — "Waqfah - Quran before apps" (26) |
| Short description | ≤ 80 chars | Present, 54 |
| Full description | ≤ 4000 chars | Present, 2429, plain-text reflowed (§2) |
| App icon | 512×512 PNG, no alpha | `images/icon.png` — 512×512 RGB |
| Feature graphic | 1024×500 PNG/JPEG, no alpha | `images/featureGraphic.png` — 1024×500 RGB |
| Phone screenshots | 2–8, 320–3840 px | Five at 1080×2400 |
| Release notes | ≤ 500 chars | **Play needs its own — see below** |
| Privacy policy URL | Public URL, required | `docs/privacy-policy.html` — needs hosting (§3) |
| Tablet screenshots | Optional | Not supplied; omitting costs tablet placement, nothing else |
| Promo video | Optional | Not supplied |

**The title uses a plain hyphen, not an em dash.** It has to stay that way:
`fastlane supply` pushes `title.txt`, so if the file disagrees with the title
already live in the Console, the next supply run silently rewrites the live
listing. The Console is the source of truth here, not this repo.

**Release notes are not shared.** `changelogs/4.txt` is an F-Droid changelog and
is wrong for Play's first release. It opens "First release under the new package
name, so this version installs alongside an older one rather than updating it" —
which is meaningful to someone upgrading from the old GitHub Releases APK, and
nonsense to a Play user who has never installed Waqfah. It also uses `*` bullets,
which Play renders literally.

Play's Console release-notes field is just a text box. Paste this (382 of the
500 allowed characters):

```
First release.

Waqfah shows a single Quranic ayah before the apps you choose. Pick your apps, grant two permissions, and it runs quietly in the background: no accounts, no ads, no tracking, and nothing leaves your device.

Sequential or random reading across all 6,236 ayat, Indopak and Uthmani scripts, English and Bengali translations, seven themes, and a per-app pause interval.
```

If you later drive Play uploads with `fastlane supply`, it reads
`fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` — the same path
F-Droid reads. The two audiences want different text for *this* version only,
because Play has no prior installs and F-Droid does. From `versionCode 5` onward
the same note works for both, so only this first one needs handling separately.

**The full description is shared with F-Droid, so it must be valid for both.**
F-Droid renders it as Markdown; Play renders it as plain text. Markdown syntax
that F-Droid styles (`*` bullets, `##` headings) shows up as literal
punctuation on Play. The file now uses `•` characters and short caps section
headers, which render acceptably on both. Do not reintroduce Markdown.

**The feature graphic was built from the app's own assets**, not designed by
hand: the ayah text is read verbatim from `assets/databases/quran_core.db`
(Ash-Sharh 94:5), set in the bundled `amiri.ttf`, on the design system's
`paper`/`sage-soft`/`ink` palette. To regenerate it, see §7.

---

## 2. Play App Signing

Play App Signing is mandatory for new apps and is a one-way decision, so make
it deliberately.

**Let Google generate and hold the app signing key.** The existing
`waqfah-release.jks` (`CN=Waqfah Release, O=ShrekBytes, C=BD`, SHA-256
`f446c35fbe09a11a9851e55ccf7355718c02e44d90c4f2e8a02c2c249b679c1b`) is
enrolled as the **upload key**. Two reasons:

1. `keystore.properties` and the `WAQFAH_*` env vars keep working unchanged —
   the upload key is what signs the AAB, and it is the same file the Gradle
   signing config already reads.
2. If the upload key is lost, Google can reset it. If you had elected to hold
   the app signing key yourself and lost it, no future update could ever
   install over the published app. There is no recovery path.

The Play signing key is unrelated to the F-Droid key and to the retired
`com.shrekbytes.waqfah` ID — different application IDs never interact.

**Keep the `.jks` and its passwords backed up somewhere outside this machine.**
Losing the upload key is survivable; losing it *and* having no other way to
prove account ownership is not.

---

## 3. Privacy policy

Required, because the app requests Usage Access and Display-over-other-apps.

`docs/privacy-policy.html` is a standalone, dependency-free page carrying the
same text the app already ships in its policy screen — English and Bengali,
from `values/strings.xml` and `values-bn/strings.xml`, with the Android device
backup exception stated explicitly. It needs a public URL before the app can
be submitted.

**Hosting: `.github/workflows/pages.yml`.** It publishes that one file as the
site root, so the URL to give Play is:

```
https://shrekbytes.github.io/waqfah/
```

The workflow copies `docs/privacy-policy.html` to `_site/index.html` and
deploys it with `actions/deploy-pages`. It runs when the policy changes, or
manually via `workflow_dispatch`.

**One-time setup, required before the first deploy can succeed:** Settings →
Pages → Build and deployment → **Source = "GitHub Actions"**. Until that is
set the deploy job fails. As of 2026-10-05 no Pages site is configured for this
repository at all.

Why not point Pages at `/docs` instead — it is one toggle and no new file.
Because `/docs` is the whole documentation tree: `docs/audits/*.md`,
`docs/RELEASING.md` and `docs/fdroid-submission.md` would all be served as
indexable pages. Nothing there is confidential — the repository is public under
AGPL-3.0 — but a store reviewer should not land on maintainer notes, and the
site root would 404 instead of showing the policy.

Play reviewers open the URL and expect a rendered page. Do not submit a
`github.com/.../blob/...` link, and not a `raw.githubusercontent.com` one
either: raw serves the file as plain text, so the reviewer sees HTML source
rather than a policy.

---

## 4. App content declarations

Console → **App content**. Every item is a form, and this is where review
actually gets decided. The wording below is drawn from the README's
Permissions section and the in-app rationale screens, so it matches what a
reviewer sees if they install the app.

### App access

Play asks whether any functionality is restricted, and if so, for instructions
so a reviewer can reach it. **Answer yes** — Waqfah's core feature does not work
until the user grants two permissions from system settings, and a reviewer who
is not told this will install the app, see nothing happen, and reject it.

Suggested wording for the reviewer instructions:

> The app's core feature needs two permissions, both granted from Android
> settings rather than an in-app prompt:
>
> 1. Open Waqfah and complete the short tour, or skip it.
> 2. On the Permissions screen, tap "Usage access" and enable Waqfah in the
>    system list that opens.
> 3. Tap "Display over other apps" and enable it in the system list that opens.
> 4. Open the Apps tab and enable any app (for example, Settings or Calculator).
> 5. Leave Waqfah and open that app. The reading screen appears over it.
>
> No account or login is needed. Nothing is restricted behind a sign-in.

### Data safety

- Does the app collect or share any required user data types? **No.**
- Everything the app stores — monitored apps, reading progress, preferences —
  stays in local storage and never leaves the device.

One nuance worth getting right: Android's own device backup copies the Room
database to the user's Google account. That is Google's backup mechanism, not
the developer collecting data, so it does not make any Data safety answer
"yes". The app's own policy text states the exception, which is the honest
position and matches what the form says.

### Content rating (IARC questionnaire)

Category **Reference** or **Religion**. Answer no to violence, sexuality,
language, controlled substances, gambling, and user interaction. Expect
**Everyone / 3+**.

### Target audience

Select **13+**. Do **not** select any under-13 group: that pulls the app into
the Families policy, which brings advertising and consent requirements that a
Quran reader has no reason to take on.

### Other declarations

Ads **no** · News app **no** · Government app **no** · Financial features
**no** · Health **no** · COVID-19 contact tracing **no**.

The donate screen copies a mobile-wallet number to the clipboard. It takes no
payment, links to no payment page, and sells no digital content, so no
Payments policy declaration applies. If that screen ever grows a "Donate"
button that opens an external payment page, re-read the Payments policy before
shipping it.

### Foreground service type

The service is declared `specialUse`, and the Console asks for a justification.
**Use the same wording as the manifest's
`PROPERTY_SPECIAL_USE_FGS_SUBTYPE` value**, or a reviewer flags the mismatch:

> Checks which app moved to the foreground so the reading pause can appear
> before selected apps open

The longer explanation, if the form asks for more:

> Waqfah's only feature is showing a short reading screen before the apps the
> user has chosen. To know when one of those apps opens, the app must know
> which app moved to the foreground. The service is continuous because it has
> to be running before the monitored app opens. No standard foreground service
> type covers this: it is not playing media, not locating, not syncing, and
> not completing a short task. It never reads the contents of other apps.

Two more fields in that declaration are easy to miss, and both are mandatory.

**A demonstration video is required**, one link per declared foreground service
type, showing the steps the user takes to trigger the feature. This is not
optional and it is the field most likely to stall the declaration.
`play-fgs-video-script.md` is the shot-by-shot script for Waqfah's. Upload it
unlisted and open the link in an incognito window before pasting it into the
Console — a link that prompts for a sign-in cannot be reviewed.

**The description must also cover what breaks if the task is deferred or
interrupted.** The form asks for that explicitly, and the explanation above only
covers why the service is needed, not why it cannot start late or be paused.
Append:

> **Why it must start immediately.** The service has to already be running at
> the moment a monitored app is opened. If the system deferred the start,
> Waqfah would learn about the open only after the user was already inside the
> app, and the reading screen could not appear before it. The feature would
> simply not work.
>
> **Why it cannot be paused or restarted.** The service's only job is to notice
> a foreground change the instant it happens. A paused or restarted service
> misses the transitions that occur while it is not running, and there is no
> event to replay later — by the time it resumes, the app is already open and
> the moment to show the reading screen has passed.

**Use case: "Other".** No preset maps to this, and the manifest agrees —
`foregroundServiceType="specialUse"` is the only honest choice.

### Sensitive permissions

Both Usage Access and Display-over-other-apps fall under the
*Permissions and APIs that Access Sensitive Information* policy and need a
justification. Reuse this, split per permission:

> **Usage access.** Waqfah's core feature is showing a Quran verse before the
> user's chosen apps open. Knowing when one of those apps has opened requires
> knowing which app is in the foreground. Usage access is used only for that
> decision. Waqfah does not read the contents of other apps, does not build a
> usage history, and sends nothing off the device.

> **Display over other apps.** The reading screen must appear over the app
> that is opening. Android's background-activity-start restrictions prevent
> that without this permission, so it is required for the app's single core
> feature. It is used only to display Waqfah's own reading screen, only when
> the user opens one of their chosen apps, and the user can revoke it at any
> time in device settings.

Worth knowing: the two permissions that most often get apps in this category
rejected are **absent from this app entirely**. There is no
`QUERY_ALL_PACKAGES` (the manifest declares a single `MAIN`/`LAUNCHER`
`<queries>` entry), and there is no AccessibilityService. That is the single
biggest reason to expect this to pass review.

### What the shipped artifact actually declares

Play reviews the uploaded bundle, not the source tree, so the answers above were
checked against the **merged release manifest**
(`app/build/intermediates/merged_manifests/playRelease/…/AndroidManifest.xml`),
not the hand-written one. For `playRelease` it declares:

- `minSdkVersion 28`, `targetSdkVersion 37`, `versionCode 4`,
  `versionName 2.0.0`, package `dev.shrekbytes.waqfah`
- Exactly the seven permissions from the source manifest — `INTERNET`,
  `POST_NOTIFICATIONS`, `PACKAGE_USAGE_STATS`, `RECEIVE_BOOT_COMPLETED`,
  `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`
- **One permission that is not in the source manifest:**
  `dev.shrekbytes.waqfah.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, injected by
  `androidx.core` at `protectionLevel="signature"`. Every modern AndroidX app
  carries it, it grants nothing to other apps, and the Console never asks about
  it — but it is a source-vs-artifact difference worth knowing before someone
  diffs the two and worries.
- No `QUERY_ALL_PACKAGES`. The only `<queries>` entry is a single
  `MAIN`/`LAUNCHER` intent, which is exactly what the package-visibility policy
  wants to see.
- `foregroundServiceType="specialUse"` with `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`
  present and matching the justification text above, word for word.
- `allowBackup="true"` with `dataExtractionRules` and `fullBackupContent` — the
  configuration the Data safety answer assumes.
- No AccessibilityService, and no `android:debuggable`, so it defaults to false.

One component a reviewer might look twice at:
`androidx.profileinstaller.ProfileInstallReceiver` is `exported="true"`. It is
guarded by `android.permission.DUMP`, a signature-or-privileged permission, so
ordinary apps cannot reach it. That is the library's standard configuration,
not a choice made here.

---

## 5. Build and upload

**Play does not accept APKs from new apps.** The upload artifact is an Android
App Bundle.

```bash
./gradlew :app:bundlePlayRelease
# → app/build/outputs/bundle/playRelease/app-play-release.aab
```

Verified end to end, in both signing configurations:

- **With the upload key** (`keystore.properties` present): `BUILD SUCCESSFUL`,
  53 tasks, `signPlayReleaseBundle` runs, and the output is a signed
  `app-play-release.aab` — 7,564,789 bytes, cert `CN=Waqfah Release,
  O=ShrekBytes, C=BD`, SHA-256 `F4:46:C3:5F:…:9B:67:9C:1B`, matching the
  keystore fingerprint in the release-readiness audit. The package inside the
  bundle is `dev.shrekbytes.waqfah`, not `.fdroid`, so the flavour split is
  intact.
- **Without any keystore** (a clean clone, the CI condition): `BUILD SUCCESSFUL`
  in 3m 22s, emitting an **unsigned** `app-play-release.aab` of 7,538,715
  bytes. `signPlayReleaseBundle` still appears in the task graph but becomes a
  no-op copy.

Note the naming asymmetry: the unsigned APK is `app-release-unsigned.apk`, but
the unsigned bundle is plain `app-play-release.aab` with no `-unsigned` suffix.
Do not use the filename to tell whether a bundle is signed — check with
`keytool -printcert -jarfile`.

The release build type applies `optimization { enable = true }` (AGP 9's R8 and
resource shrinking), and the bundle path handles it with no extra
configuration. Signing comes from `keystore.properties` or the `WAQFAH_*` env
vars exactly as `docs/RELEASING.md` describes.

**CI:** `.github/workflows/android-ci.yml` now runs `:app:bundlePlayRelease`
alongside the APK release check. This closes a real gap — the Play upload
artifact was the one output nothing in the repo ever built, so the bundle path
could have broken without CI noticing. CI has no keystore, so the step proves
the bundle compiles and packages, not that it is signed. `assemblePlayRelease`
remains, because it is what the existing R8 check runs.

---

## 6. Tracks and the testing gate

**Internal testing** → **Closed testing** → **Production**.

Internal testing is available within minutes and is the right place to
smoke-test the Play-signed build: the signature differs from every local build
you have installed, so the first install will require uninstalling the debug
build. Grant Usage Access and Display-over-other-apps by hand and confirm the
reading screen still appears over a monitored app — that path is the whole
product, and it is the one Play's signature change could plausibly disturb.

**Pre-launch report** runs automatically on the uploaded AAB, on real devices.
It will drive the UI, rotate, and force-stop. Because Waqfah needs two
permissions granted by hand, expect the report to show the app mostly idle;
that is not a failure. What it is good for is surfacing crashes.

### The gate — personal accounts only

This account is a **personal** account created after 2023-11-13, so the
requirement applies and it is the longest pole in the whole submission.
**Internal testing does not satisfy it.** Only a closed test does.

The rule, precisely:

- **At least 12 testers must be opted in**, not merely invited. An invitation
  nobody accepted counts for nothing.
- They must stay opted in **continuously for 14 days**, and the qualifying
  period is the 14 days *immediately preceding* the production request.
- **A tester who opts out before day 14 never counts.** Opting back in later
  does not stitch the two periods into one continuous 14-day window. That
  tester's clock restarts.
- Recruit a **buffer above 12**. One dropout at day 10 can drop the qualifying
  count below the minimum and cost a fresh 14 days for that slot.
- Reaching day 14 is a milestone, **not approval**. Google can request
  additional testing, and it does.

### Why Waqfah's testers need a briefing

This is the part that makes this app's closed test different from a typical
one, and the part most likely to be mishandled.

The requirement is about continuous opt-in, but the production-access
application asks **substantive questions about engagement**: whether testers
used the available features, whether usage matched production expectations,
what feedback came back, and what changed as a result. A roster of 12 people
who opted in, installed, saw a blank-looking app, and never touched it again
produces weak answers to all of those — and "insufficient tester engagement" is
one of Google's stated reasons for demanding another round.

Waqfah does nothing until two permissions are granted from system settings.
Nobody grants those unprompted. **Send every tester the instructions below
before they install**, not after they report it as broken.

### Tester instructions (send this to all of them)

> Thanks for helping test Waqfah. It takes about three minutes to set up, and
> you need to do one step in Android settings or the app will look like it does
> nothing.
>
> 1. Open the opt-in link I sent you and accept. Then install Waqfah from the
>    Play Store link on the same page.
> 2. Open Waqfah. A short tour runs — you can skip it.
> 3. On the Permissions screen, tap **Usage access** and switch Waqfah on in
>    the Android list that opens. Come back and tap **Display over other apps**
>    and switch it on too. Both of these are in Android's own settings, not
>    inside the app, so it feels like leaving the app. That is normal.
> 4. Go to the **Apps** tab and turn on a couple of apps you use often —
>    anything, say your messaging app and a game.
> 5. Now leave Waqfah and open one of those apps. A screen with a Quranic verse
>    should appear over it. Read it or tap to skip, and you continue into the
>    app.
> 6. Try it a few times over the next two weeks — open your monitored apps as
>    you normally would.
>
> Please tell me anything that looks wrong, feels slow, or is confusing.
> Especially: if the verse screen does not appear, tell me which app you
> opened and what your phone is.
>
> One thing to know: Waqfah and an older build cannot be installed at the same
> time. If you have Waqfah installed already, uninstall it first.

### Practical mechanics

- **Access method:** an email list or a Google Group, set when you create the
  closed track. An email list is simpler; a Google Group scales better and
  lets testers add themselves.
- **Verify opt-ins, don't count invitations.** Play Console shows the actual
  opted-in tester count. Track your own roster alongside it so you notice a
  dropout the same week rather than on the day you apply.
- **Keep a feedback log** as you go. The production application asks what you
  learned and what you changed; reconstructing that after the fact from memory
  produces vague answers.
- **Fix what testers report** during the window. It is free evidence for the
  production application, and it is what the 14 days are actually for.
- **Timing.** The window is 14 continuous days from the day the 12th tester
  opts in. From that date, expect production access some days after the
  application, not the same day.

### Applying

Dashboard → **Apply for production**. The form covers the closed test, the app,
and production readiness, and it asks about tester engagement, what you learned,
and how you decided the app was ready. Answer it with what actually happened —
Google can request another round, and an application that does not match the
observable test data is a good way to get one.

---

## 7. Regenerating the feature graphic

The graphic is generated, not hand-drawn, so it stays consistent with the app
if the palette or the bundled font ever changes.

The approach: build an HTML page at exactly 1024×500, embed `amiri.ttf` as a
base64 `@font-face` (so no font installation is needed and shaping is
guaranteed to match), and screenshot it with headless Chromium.

Three things worth knowing, all of which cost time the first run:

- **The bundled font's family name is `Amiri Quran`, not `Amiri`.** Asking for
  `Amiri` silently falls back to a system sans and renders the ayah as
  unshaped boxes. Check with
  `fc-query --format='%{family}\n' app/src/main/res/font/amiri.ttf`.
- **Use Chromium, not `rsvg-convert`.** Both shape Arabic, but Chromium uses
  HarfBuzz — the same engine Android uses — so the output matches what the app
  renders. `rsvg-convert` produced visibly different diacritic placement.
- **Read the ayah from the database, never retype it.** The text is Tanzil's,
  redistributed under a verbatim-only licence, so it must be byte-identical:

  ```bash
  sqlite3 app/src/main/assets/databases/quran_core.db \
    "SELECT arabic_uthmani FROM verses WHERE surah_no=94 AND ayah_no=5;"
  ```

  Avoid ayat containing the waqf mark `U+06D7`: Amiri Quran draws that glyph
  far above the baseline, which reads as a rendering fault in a graphic this
  small. Ash-Sharh 94:5 has no pause marks.

Render command:

```bash
chromium --headless=new --disable-gpu --no-sandbox --hide-scrollbars \
  --force-device-scale-factor=1 --window-size=1024,500 \
  --screenshot=featureGraphic.png feature.html
```

Play requires no alpha channel. A Chromium screenshot of an opaque page is
already 24-bit RGB — confirm with
`identify -format "%wx%h alpha=%A\n" featureGraphic.png`, which must print
`1024x500 alpha=Undefined`.

---

## 8. Remaining steps

The account is **personal**, so the closed test is the critical path and
everything else is parallel work that fits inside its 14 days.

**Submission is behind us.** The app content declarations cleared review and
the app is in closed testing with a tester roster above the 12 minimum, so what
remains is the testing window and the production application. Everything that
used to be on this list is in the done section below.

1. **Record the day the 12th tester opted in.** That date starts the 14
   continuous days, and nothing else on this list is on the critical path. Play
   Console counts testers who are *opted in*, not invitations — an invitation
   nobody accepted counts for nothing, and a tester who opts out before day 14
   does not count at all: opting back in later restarts that slot's clock.
2. **Run the test for real.** Log feedback, fix what comes back, keep a record.
   The production application asks what you learned and what changed, and
   reconstructing that from memory at the end produces the vague answers that
   earn another round.
3. **Apply for production access** from the Dashboard once the window closes,
   then roll out. Day 14 is a milestone, not approval — Google can and does
   request additional testing.
4. **Bengali store listing.** The app ships a full Bengali interface and the
   policy page is bilingual, but `fastlane/metadata/android/bn/` does not
   exist. Do not machine-translate the listing copy: it is user-facing
   marketing for a religious app, which is exactly where a bad translation
   does damage. Translate it properly or reuse the wording already in
   `values-bn/strings.xml`.
5. **Release notes.** Do **not** reuse `changelogs/4.txt` — it is an F-Droid
   upgrade note and reads as nonsense to a first-time Play user. Paste the
   first-release text from §1 into the Console's release-notes field.

Done, recorded here so they are not re-checked:

- ~~Create the app record~~ — created, default language `en-US`, Free, App.
- ~~Complete App content (§4)~~ — cleared review. App access, Data safety,
  content rating, target audience, the foreground-service declaration including
  its demonstration video, and both sensitive-permission justifications were
  all accepted. The App access instructions mattered: without them a reviewer
  installs the app, sees nothing happen, and rejects it.
- ~~Enroll in Play App Signing (§2)~~ — `waqfah-release.jks` enrolled as the
  upload key, Google holds the app signing key.
- ~~Upload the AAB and start the closed test~~ — bundle uploaded, closed track
  live with 15+ testers recruited, above the 12 minimum.
- ~~Privacy policy hosting~~ — live at <https://shrekbytes.github.io/waqfah/>,
  served by `.github/workflows/pages.yml`.
- ~~Confirm `targetSdk 37` is accepted~~ — Android 17 / API 37 went stable on
  2026-06-16, so it is a released API level and Play accepts it. Play's floor is
  36 for new apps since 2026-08-31. No change needed.
- ~~Verify the AAB build path~~ — built and verified both signed and unsigned;
  `:app:bundlePlayRelease` now runs in CI.

Nothing here changes `versionCode 4` / `versionName 2.0.0` — that is a valid
first Play release. After it, each channel's `versionCode` only needs to
increase within itself, since the two application IDs are independent.

### Outstanding on the F-Droid side

Not a Play blocker, but do not drop it: the F-Droid MR
([!50834](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/50834)) still
has two unanswered reviewer requests — enable **Squash commits**, and add the
`NonFreeAssets` reason under ~69 characters. See `docs/fdroid-submission.md`
§4.
