# Release-readiness gate — whole-codebase pass 4

- **Date:** 2026-09-06 · **Audited commit:** `9be22dc` (code identical to `0420241`) · **Spec:** [issue #12](https://github.com/ShrekBytes/waqfah/issues/12)
- **Method:** full checklist sweep with the documented build commands actually run, plus an on-device run of the minified release build on the emulator (Medium Phone API 36.1, headless). The release APK was signed with the local **debug keystore for testing only** — never for distribution. No code was changed.
- **Overall verdict: BLOCK — do not tag a release from this tree.** One intermittent launch crash in the minified build (root-caused below, small fix), and no wired signing path to an installable artifact. Everything else on the checklist passes.

---

## Checklist

### 1. R8 / minification — **BLOCK (crash, root-caused)**

The release path runs for real: `minifyReleaseWithR8` + `optimizeReleaseResources` both execute, a `mapping.txt` is emitted, and the keep-rules setup is sound — `app/src/main/keepRules/` is AGP 9's standard rule location, the file is all comments by design, and the UI renders correctly under minification (screenshot on the emulator), so AGP defaults plus library consumer rules are sufficient for Hilt/Room/serialization as configured.

**But the minified build crashes on first launch.** Captured on the emulator (1 of ~7 cold starts; intermittent, which is the nature of the defect):

```
FATAL EXCEPTION: DefaultDispatcher-worker-2
java.lang.NullPointerException: Attempt to invoke virtual method
  'java.lang.Object z71.e(lw)' on a null object reference
  at com.shrekbytes.waqfah.data.repository.TranslationRepository
      .refreshDownloadedIds(TranslationRepository.kt:352)
  at com.shrekbytes.waqfah.data.repository.TranslationRepository
      .access$refreshDownloadedIds(TranslationRepository.kt:35)
  at com.shrekbytes.waqfah.data.repository.TranslationRepository$1
      .invokeSuspend(TranslationRepository.java:52)
```

Retraced: `z71` is `kotlinx.coroutines.sync.MutexImpl` — the crash is `refreshLock.withLock` on a **null** mutex, reached from the repository's `init { initScope.launch { refreshDownloadedIds() } }`.

**Root cause (present on every build, not an R8 bug):** in `TranslationRepository`, the `init {}` block (which launches `refreshDownloadedIds`) is declared **before** the `refreshLock = Mutex()` and `downloadLocks` fields. `launch` publishes `this` to the IO dispatcher before the constructor finishes writing those `val` fields, so the worker can read `refreshLock` in its pre-write (null) state — unsafe publication of a half-constructed singleton. Whether it fires is a thread race: the debug build and warm launches win it; some release cold starts lose it. The shipped v1.1.0 has presumably just been lucky.

**Fix sketch (small):** declare `refreshLock`, `openDatabases`, and `downloadLocks` **above** the `init {}` block (Kotlin executes initializers in declaration order), or start the initial refresh lazily rather than from `init`. After the fix, re-run this gate's on-device check.

### 2. `assembleRelease` — **SHIP**

Builds clean in 3m41s (`BUILD SUCCESSFUL`, zero errors); APK is 6.6 MB after shrinking; lint completes with **zero errors** (23 cosmetic warnings: 4× `InlinedApi` — all API-guarded in code, 2× `UnusedResources` for the parked donation icons, 3× `IconLocation`, plus version/config nags and one `ModifierParameter`).

### 3. Signing — **BLOCK (for an agent-driven release)**

No `signingConfig` exists on the release build type: `assembleRelease` produces `app-release-unsigned.apk`, which cannot be installed. Distribution is APK sideloads from GitHub Releases, so a stable signing key is what keeps updates installable. The keystore is (correctly) not committable — what's missing is a *wired, documented* signing step: either an env/`keystore.properties`-driven `signingConfig` or a written `apksigner` step in a release checklist. Until then, only a human with the local key can produce a release artifact.

### 4. Versioning — **SHIP (note)**

`versionCode 3` / `versionName 1.1.0`, consistent; the About screen renders `BuildConfig.VERSION_NAME`. There is no in-repo changelog — release notes live on the GitHub Releases page; acceptable, just keep the privacy-policy's "described in release notes" promise honest there.

### 5. Locale parity — **SHIP (note)**

`StringsParityTest` green (247 string/plural keys mirrored in `values-bn`), `locales_config` declares `en` + `bn`, per-app locales flow through AppCompat with the persistence service declared. One cosmetic note: the three Arabic-font display names (`ArabicFonts.displayName()`) are hard-coded English proper nouns, untranslated in Bengali — fine to leave; resource-ify only if it matters to Bengali users.

### 6. Manifest — **SHIP**

Reviewed in passes 1 and 3: only the launcher activity is exported; the interstitial, monitor service (`specialUse` FGS with its required subtype property), and boot receiver are all closed; backup/extraction rules are deliberate and documented in-file.

### 7. Permissions vs. catalog — **SHIP**

Verified in pass 3: all seven declared permissions traced to concrete uses; the in-app permission catalog, onboarding gating, and rationale screens match the manifest exactly.

### 8. CI vs. documented claims — **SHIP (note)**

The workflow runs `lintDebug`, `testDebugUnitTest`, `assembleDebug`, and `assembleRelease` (the R8 check that would have caught a config-level minification failure — though not this crash, which is timing-dependent). It does **not** run the instrumented suite: there is still no emulator job, so the Room-migration safety net remains manual-only (pass 2's recommendation #2 stands).

### 9. Bundled assets — **SHIP**

`quran_core.db`, bundled `sahih.db`, and bundled `taisirul.db` all pass SQLite `PRAGMA integrity_check` with `user_version` 0 — exactly the state the download-validation gate accepts (pinned by `TranslationValidationInstrumentedTest`).

### 10. Metadata — **SHIP**

Launcher icon (adaptive mipmap), label, notification icon present and referenced; theme/splash configuration coherent (`Theme.Waqfah.Starting` → splash → themed content).

---

## On-device evidence log

- Emulator: Medium Phone API 36.1, headless. Release APK zipaligned and signed with the local debug keystore (test-only).
- Cold starts: **1 crash in 7 tries** (first-ever launch), then clean across 3 warm launches and 4 fresh-install cold starts; logcat `FATAL` scan per run; full stack captured and retraced against `mapping.txt`.
- Debug-build cold-start control on the same emulator: clean.
- Minified UI renders correctly with the app holding window focus (screenshot reviewed: Welcome screen, themed, edge-to-edge).
- Test app uninstalled and emulator shut down afterward; no repo files touched.

## Recommendation

Fix the two blockers, then re-run this gate: (1) reorder `TranslationRepository`'s field declarations ahead of its `init` block (or start the refresh lazily), and (2) wire + document the signing step. Both are small. Everything else is already in releasable shape.
