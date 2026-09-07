# Release-readiness gate — whole-codebase pass 4 (fresh re-run)

- **Date:** 2026-09-06 · **Audited commit:** `1a2e43d` — production code under `app/` and `gradle/` is byte-identical to `9be22dc`, which the first pass-4 report audited (and to `0420241`, per passes 2–3) · **Spec:** [issue #12](https://github.com/ShrekBytes/waqfah/issues/12)
- **Provenance:** this file replaces the first pass-4 report (commit `f357fd5`), re-run after passes 2 and 3 were re-run (`2c9217d`, `2f3c28f`), so the gate's last sweep sees the same tree those passes reported. Every checklist item was re-checked against the current tree with fresh command evidence; the on-device findings are inherited from the first pass-4's emulator run, because the code it exercised is byte-identical to this one.
- **Method:** the two documented headless commands (`AGENTS.md`: `:app:testDebugUnitTest`, `:app:assembleDebug`) plus the release and lint tasks CI runs (`README` documents `:app:assembleRelease`; `android-ci.yml` adds `:app:lintDebug`) run in one invocation (`./gradlew :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug` → `BUILD SUCCESSFUL`, exit 0); `minifyReleaseWithR8` and `testDebugUnitTest` additionally forced fresh (`--rerun`) so the R8 warning review and the 147-test green result are not Gradle's up-to-date cache. Static checks covered the manifest, gradle files, CI workflow, keep rules, and bundled assets (SQLite `PRAGMA integrity_check` in read-only mode). No code was changed; the working tree was left untouched.
- **Overall verdict: BLOCK — do not tag a release from this tree.** Both blockers from the first pass-4 are still present and unfixed: (1) the intermittent launch crash caused by `TranslationRepository`'s initialization order, and (2) no wired signing path to an installable artifact. One further pre-release fix is now carried from the re-run pass 3 (`docs/audits/2026-09-06-security.md`, finding P2-S1): the shipped policy sentence that Android backup contradicts — a wording fix.
- **Disposition (2026-09-07):** all actionable findings fixed — blocker 1 by declaring `TranslationRepository`'s lock fields ahead of its `init` block, so the initial refresh launches only after every field it reads is written (comment on the moved block pins the ordering); blocker 2 by a `keystore.properties`/`WAQFAH_*`-env-driven `signingConfig` wired into the release build type — absent configuration still yields an unsigned APK (CI's compile check keeps passing), a half-configured one fails the build — plus a README "Releasing" section with the `keytool` step; the P2-S1 wording fix landed separately in `7ace249` (README and the in-app policy, English and Bengali, name Android's own device backup as the one exception). The four CI tasks are green after the fixes, the signed path was verified with a throwaway keystore producing a signature-verifiable `app-release.apk`, and the gate's on-device cold-start re-check ran on a physical device (POCO X3 NFC, Android 15 / LineageOS 22.2, debug build from the fix commit): **15/15 force-stop → launch cycles crash-free** (audited rate was 1-in-7) with an empty crash buffer, plus a functional smoke — the reading screen (`TriggerActivity`) appeared over a monitored app via the overlay path and the trigger stamp wrote to Room. The release-build caveat is closed (2026-09-07): the user's real keystore (wired via gitignored `keystore.properties`) produced a signed `app-release.apk` — `apksigner verify`: V2, cert `CN=Waqfah Release, O=ShrekBytes, C=BD`, SHA-256 `f446c35fbe09a11a9851e55ccf7355718c02e44d90c4f2e8a02c2c249b679c1b` — installed on the same device after removing the debug build (uninstall forced by the signature change), and the same 15-cycle cold-start loop passed on the **minified signed release** build, the one configuration the audited crash ever reproduced on: empty crash buffer, and a clean foreground smoke (`AppMonitorService` running, zero FATAL errors in logcat). Nothing blocks tagging.

- **Close-out (2026-09-07, HEAD `87abaa9`):** the gate stands closed. Since the disposition's device soak, only CI/build-config landed — `e5efa87` (the instrumented emulator job) and `87abaa9` (Room schemas packaged into androidTest assets + emulator KVM perms) — no shipped runtime code; the manifest, keep rules, bundled assets, and permission set are byte-identical to the audited commit, and versioning is unchanged (`versionCode 3` / `versionName "1.1.0"`). The gate's verification re-run fresh at HEAD: the four CI tasks in one invocation (`assembleDebug`, `assembleRelease`, `testDebugUnitTest`, `lintDebug` → `BUILD SUCCESSFUL`), with `minifyReleaseWithR8` and `testDebugUnitTest` forced `--rerun` — R8 completed with zero warnings and zero errors; unit tests: 20 suites, **161 tests, 0 failures, 0 skipped** (the count grew from the audit's 147 with pass 3's `TranslationIntegrityTest`; `StringsParityTest` and `CatalogAndLimitsTest` both green); lint: 0 errors, 23 warnings, the same profile the audit recorded. The signed path re-verified: `apksigner verify` passes on the freshly built `app-release.apk` — V2 scheme, cert `CN=Waqfah Release, O=ShrekBytes, C=BD`, SHA-256 `f446c35fbe09a11a9851e55ccf7355718c02e44d90c4f2e8a02c2c249b679c1b`, matching the disposition's fingerprint. Checklist §8's one doc-vs-CI gap is resolved: CI now runs both jobs — build (lint, unit, debug, release) and instrumented (Room-migration safety net et al. on an API-36 emulator) — and both were green on HEAD's push, delivering health-pass §5 recommendation 3. **Nothing blocks tagging.**

---

## Checklist

### 1. R8 / minification — **BLOCK (crash, root-caused; unchanged)**

The configuration side ships: `app/src/main/keepRules/rules.keep` is all comments by design (AGP 9's standard `keepRules` location, header documenting its file-combining behavior); a **fresh** `minifyReleaseWithR8 --rerun` completed with **zero warnings and zero errors** on stdout and regenerated the full mapping set (`mapping.txt`, `usage.txt`, `seeds.txt`, `configuration.txt`, `resources.txt`); CI runs `assembleRelease` as its explicit R8 check; and the minified UI was verified rendering correctly on-device by the first pass-4 (unverified this pass — inherited on byte-identical code). AGP defaults plus the libraries' consumer rules are sufficient for Hilt, Room, kotlinx-serialization, and Compose as configured — serialization's only uses are Navigation 3's `@Serializable` destinations (`ui/navigation/Destinations.kt`) and the `@Serializable` `WaqfahTab` enum they embed (`WaqfahTabBar.kt:48`), all covered by kotlinx-serialization's consumer rules and the clean fresh run.

**But the minified cold-start crash is unfixed.** The current tree still declares the `init` block that launches the initial refresh **before** the fields it reads — `TranslationRepository.kt:51-53` (`init { initScope.launch { refreshDownloadedIds() } }`) ahead of `refreshLock` (`:58`), `openDatabases` (`:69`), and `downloadLocks` (`:76`). Kotlin executes initializers in declaration order, and `launch` publishes `this` to the IO dispatcher before the constructor finishes writing those `val`s, so a worker can read `refreshLock` in its pre-write (null) state — intermittent `FATAL EXCEPTION` on release cold starts (captured on-device at 1-in-7 by the first pass-4 — unverified this pass, inherited on byte-identical code — with the full retrace to `MutexImpl`; debug control clean). **Fix (small):** declare the lock fields above the `init` block, or start the initial refresh lazily — then re-run this gate's on-device check.

### 2. `assembleRelease` + warnings — **SHIP**

Fresh build green (`BUILD SUCCESSFUL`, exit 0); the artifact is `app-release-unsigned.apk`, 6,574,394 bytes (~6.6 MB). The fresh R8 re-run produced zero warnings. `lintDebug`: **0 errors, 23 warnings** (4 `InlinedApi`, 3 `IconLocation`, 2 `UnusedResources` — the parked donation icons, 2 `UnusedAttribute`, 1 `ModifierParameter`, and 11 version/config nags: 7 `NewerVersionAvailable`, 2 `GradleDependency`, 2 `AndroidGradlePluginVersion`).

### 3. Signing — **BLOCK (unchanged)**

The release build type (`app/build.gradle.kts:26-32`) declares only `optimization { enable = true }` — **no `signingConfig`** — so `assembleRelease` emits `app-release-unsigned.apk`, which cannot be installed. There is no `keystore.properties`, and no signing/`apksigner` documentation anywhere in README or `docs/`. Distribution is APK sideloads from GitHub Releases, so a stable signing key is what keeps updates installable. The keystore itself is (correctly) not committable; what's missing is a *wired, documented* step — an env/`keystore.properties`-driven `signingConfig`, or a written `apksigner` step in a release checklist. Until then, only a human with the local key can produce a release artifact.

### 4. Versioning — **SHIP (note)**

`versionCode 3` / `versionName "1.1.0"` (`app/build.gradle.kts:20-21`), consistent. There is no in-repo changelog — release notes live on the GitHub Releases page; acceptable, just keep the privacy policy's "described in release notes" promise honest there.

### 5. Locale parity — **SHIP (note)**

`StringsParityTest` green in the fresh unit run (18 suites, **147 tests, 0 failures, 0 skipped**); `locales_config.xml` declares `en` + `bn`; the manifest declares `AppLocalesMetadataHolderService` so per-app locales persist below Android 13. Hard-coded user-facing strings in code: a grep over `ui/` for text parameters set to string literals (no `stringResource`) finds only Compose animation `label =` debug tags — no user-visible hard-coded strings. The one deliberate exception, unchanged: the three Arabic-font display names (`ArabicFont.displayName()`) are hard-coded English proper nouns, untranslated in Bengali — fine to leave.

### 6. Manifest (exported components, backup rules, FGS type) — **SHIP**

Fresh read confirms the component surface: only `MainActivity` is `exported="true"` (launcher); `TriggerActivity`, `AppMonitorService`, and `BootReceiver` are all `exported="false"`; the monitor service is `foregroundServiceType="specialUse"` with the required `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` property; the single `MAIN/LAUNCHER` `<queries>` entry is what the indirect-entry probes need on API 30+.

Backup is `allowBackup="true"` with `fullBackupContent` + `dataExtractionRules` excluding only `file/translations` — deliberate and documented in-file. The re-run pass 3 (P2-S1) established the consequence that matters for shipping: the Room DB (monitored apps, trigger stamps, read history) and the DataStore ride Android cloud backup/device transfer, while the shipped policy text (`strings.xml:208`, "None of it ever leaves your device") denies it. The manifest configuration itself is sound; the one consequence — the shipped policy text's absolute sentence is false under backup (pass 3's P2-S1, `docs/audits/2026-09-06-security.md`) — is a wording fix carried in the recommendation below, not a manifest defect.

### 7. Permissions vs. catalog — **SHIP**

The seven declared permissions are unchanged (`INTERNET`, `POST_NOTIFICATIONS`, `PACKAGE_USAGE_STATS`, `RECEIVE_BOOT_COMPLETED`, `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`). `CatalogAndLimitsTest` is green in the fresh run, pinning the required/optional partition; pass 3 traced every permission to concrete use and matched the in-app catalog, onboarding gating, and rationale screens word for word.

### 8. CI vs. documented claims — **SHIP (note)**

`.github/workflows/android-ci.yml` runs `lintDebug`, `testDebugUnitTest`, `assembleDebug`, and `assembleRelease` (the explicit R8 check), and uploads the debug APK as an artifact — exactly the two commands AGENTS.md documents plus the two CI-only tasks (`lintDebug`, `assembleRelease`), with lint-in-CI remaining the one doc-vs-CI gap the health pass recorded. It still has **no emulator/instrumented job**, so the Room-migration safety net remains manual-only (pass 2's recommendation #3 stands).

### 9. Bundled assets — **SHIP**

Read-only `PRAGMA integrity_check` = `ok` with `user_version` = 0 for all three bundled databases: `assets/databases/quran_core.db` (12,378,112 bytes), `assets/translations/en/sahih.db`, and `assets/translations/bn/taisirul.db` — exactly the state the download-validation gate accepts (pinned by `TranslationValidationInstrumentedTest`).

### 10. Metadata — **SHIP**

Adaptive launcher icon present (`mipmap-anydpi` `ic_launcher`/`ic_launcher_round` with foreground/background drawables including night variants), `@string/app_name` label, and all three font files on disk (`amiri.ttf`, `digital_khatt_indopak.otf`, `mequran.ttf`); theme/splash configuration coherent.

---

## What changed since the first pass-4 report

Nothing in code — `git diff` from the audited commits to `1a2e43d` touches only `docs/audits/`. The re-run passes 2 and 3 confirmed this report's inherited facts (CI contents, keep-rules state, manifest surface, permission tracing) and added one item this gate now carries: **P2-S1** (backup contradicts the shipped policy sentence). No first-pass-4 claim was refuted by this re-run.

## Recommendation

Fix three things, then re-run this gate:

1. **Reorder `TranslationRepository`'s lock fields ahead of its `init` block** (or start the initial refresh lazily) — the crash blocker. Small; after the fix, re-run the on-device cold-start check.
2. **Wire + document the signing step** (env/`keystore.properties`-driven `signingConfig` or a written `apksigner` step) — the artifact blocker.
3. **Soften the policy sentence Android backup contradicts** (P2-S1 from the re-run pass 3) — one line of `strings.xml`; not a configuration defect, but a false privacy claim should not ship.

Everything else is already in releasable shape; nothing regressed since the first pass-4 sweep.
