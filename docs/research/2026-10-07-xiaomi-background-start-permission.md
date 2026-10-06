# Xiaomi / HyperOS background-activity-start permission — primary-source research

**Date:** 2026-10-07 · **Context:** PR [#28](https://github.com/ShrekBytes/waqfah/pull/28), issue [#15](https://github.com/ShrekBytes/waqfah/issues/15)
**Questions:** (1) Play risk, (2) F-Droid risk, (3) can the app self-grant, (4) how other apps cope,
(5) which OEMs are affected, (6) better detection than brand lists + reflection.
**Method:** primary sources only — official Android docs, AOSP `frameworks/base`, the Play Policy
Center, F-Droid official docs, and the source of the named libraries/apps. Blog posts were used
only to *locate* primary sources. Untraceable claims are labelled **unverified** in the last section.

---

## 1. Google Play acceptance risk

### 1a. Non-SDK interfaces — no Play policy; a platform restriction

There is **no Google Play Developer Program Policy about non-SDK interfaces, hidden APIs, or
reflection.** The Policy Center category list contains nothing on the subject (closest categories:
Device and Network Abuse, Deceptive Behavior — [Policy Center](https://support.google.com/googleplay/android-developer/topic/9858052)).
The restriction is a **platform/runtime** mechanism:

> "Starting in Android 9 (API level 28), the Android platform restricts which non-SDK interfaces an
> app can use. These restrictions apply whenever an app references a non-SDK interface or attempts
> to obtain its handle using reflection or JNI."
> — [Restrictions on non-SDK interfaces](https://developer.android.com/guide/app-compatibility/restrictions-non-sdk-interfaces)

So reflecting on `android.os.SystemProperties` and the hidden `AppOpsManager.checkOpNoThrow(int,
int, String)` overload is **not by itself a Play violation**, and there is no documented Play
Console warning for it. The real risk is **functional**: a member on the `blocked` list throws at
runtime (`NoSuchMethodException` / `IllegalAccessException`), and members move between the
`unsupported`, `max-target-x`, and `blocked` lists across releases — a technical fragility, not a
rejection reason. (Lint's `PrivateApi` check flags such reflection, but it is a warning, not a
policy.) The one Device and Network Abuse clause touching "getting at other apps" is "Apps that
circumvent Android sandbox protections in order to derive user activity or user identity from other
apps" ([Device and Network Abuse](https://support.google.com/googleplay/android-developer/answer/9888379)) —
Waqfah holds user-granted usage access and reads nothing about another app, so it is not applicable.

### 1b. Device and Network Abuse / Deceptive Behavior — no bearing

Device and Network Abuse forbids apps that "interfere with, disrupt, damage, or access in an
unauthorized manner the user's device … including … other apps on the device." Waqfah interposes
its own activity over apps **the user explicitly selected**, and that behaviour has already passed
Play review. The policy has **no sentence prohibiting launching an activity over another app**; the
only related item is the `USE_FULL_SCREEN_INTENT` restriction, which Waqfah does not use. Deceptive
Behavior is where deep-linking is addressed, and it **supports** the PR:

> "Any changes to device settings must be made with the user's knowledge and consent and be
> reversible by the user." — [Deceptive Behavior](https://support.google.com/googleplay/android-developer/answer/9888077)

Deep-linking the user into the OEM settings screen and asking them to flip the toggle is exactly
knowledge + consent + reversibility. Asking a user to change a vendor setting is **not** deceptive;
it would only become one if the app claimed the change was automatic or irreversible.

### 1c. Data Safety — nothing to declare

Play defines collection as transmission off the device: "'Collect' means transmitting data from
your app off a user's device" … "On-device access/processing: User data accessed by your app that is
only processed locally on the user's device and not sent off device does not need to be disclosed"
([Data safety](https://support.google.com/googleplay/android-developer/answer/10787469)).

Waqfah transmits nothing. `Build.MANUFACTURER`/`BRAND`/`MODEL` and vendor system properties are
**non-unique hardware attributes**, not the persistent/resettable identifiers the "Device or other
IDs" type covers (IMEI, MAC, Widevine Device ID, Firebase installation ID, advertising ID — [Declare
your app's data use](https://developer.android.com/guide/topics/data/collect-share)). Even if they
were, on-device-only use needs no disclosure. **No Data safety change is required.** (The User Data
policy separately treats the *inventory of installed apps* as sensitive —
[Personal and Sensitive User Data](https://support.google.com/googleplay/android-developer/answer/10144311) —
but Waqfah's usage-access justification is already approved and the PR adds no new enumeration.)

### 1d. SYSTEM_ALERT_WINDOW and foreground service types

Play has **no dedicated `SYSTEM_ALERT_WINDOW` policy**; it is covered by the general
sensitive-permission justification Waqfah already completed. Foreground services must declare an
FGS type, a description, the impact of deferral/interruption, and a demo video
([FGS requirements](https://support.google.com/googleplay/android-developer/answer/13392821);
[Permissions for FGS](https://support.google.com/googleplay/android-developer/answer/13315670)).
Waqfah already declares `specialUse` and has that declaration; a Xiaomi settings row changes none
of it.

**Verdict Q1:** low Play risk. The brand check and deep-link are benign; the reflection is a
*technical* risk, not a policy one.

---

## 2. F-Droid acceptance risk

Rules live in the [Inclusion Policy](https://f-droid.org/docs/Inclusion_Policy/) and the
[Anti-Features](https://f-droid.org/docs/Anti-Features/) list:

- Anti-features are **labels, not disqualifiers** — they "serve as warning indicators … without
  necessarily disqualifying applications from inclusion" (Inclusion Policy §Security & Legal
  Compliance item 4). The full set: Ads, Disabled Algorithm, Known Vulnerability, Non-Free Addons,
  Non-Free Assets, Non-Free Dependencies, Non-Free Network Services, No Source Since, Tethered
  Network Services, Tracking.
- **Tracking** requires reporting activity "to somewhere"; reading `Build.MANUFACTURER` or a system
  property locally and transmitting nothing is not tracking. Not triggered.
- **Non-Free Dependencies** is "an app that … doesn't run, or is not useful, unless you have
  [non-free software] installed". Waqfah is fully useful on AOSP; on MIUI the OS blocks one feature,
  and the vendor settings app is a **system component of the OS**, not a separate install the app
  bundles or requires. On the plain wording, **not met** (at most arguable).
- **Non-Free Addons / Assets / Network Services** — none apply.
- Reflection on hidden APIs is **not mentioned** anywhere in either document. F-Droid's hard lines
  are licensing, proprietary tracking/ad libraries, a FLOSS build toolchain, and no runtime
  executable downloads; pure reflection compiles and builds fine. Deep-linking into a proprietary
  OEM settings app bundles no dependency and maps to no anti-feature.

**Verdict Q2:** low risk. The only watch item is a reviewer's judgement call on `NonFreeDep`; the
wording does not support applying it, and the honest answer is that the feature degrades
gracefully rather than depending on the vendor app to run.

---

## 3. Can the app grant the permission itself? No.

### 3a. `setMode` / `setUidMode` require a signature permission

From AOSP `AppOpsManager.java`, both setters carry
`@RequiresPermission(android.Manifest.permission.MANAGE_APP_OPS_MODES)`:

```java
public void setUidMode(int code, int uid, @Mode int mode) { … }
public void setMode(int code, int uid, String packageName, @Mode int mode) { … }
```
— [AppOpsManager.java](https://github.com/aosp-mirror/platform_frameworks_base/blob/main/core/java/android/app/AppOpsManager.java)
([cs.android.com](https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/core/java/android/app/AppOpsManager.java))

`MANAGE_APP_OPS_MODES` is declared in AOSP's framework manifest as
`android:protectionLevel="signature|installer|verifier|role"` — no `normal`/`dangerous` component,
so it is **not grantable to an ordinary third-party app** ([frameworks/base/core/res/AndroidManifest.xml](https://github.com/aosp-mirror/platform_frameworks_base/blob/main/core/res/AndroidManifest.xml)).
(`UPDATE_APP_OPS_STATS`, used internally, is `signature|privileged|installer|role`.) **It is
impossible for a normal app to set its own AppOp mode.** `AppOps.md` agrees the mode is owned by
the provider, not the subject app — "The current state of the app-op can be read via the `appops
get` command or via `dumpsys appops`" ([AppOps.md](https://github.com/aosp-mirror/platform_frameworks_base/blob/main/core/java/android/app/AppOps.md)).

### 3b. `OP_BACKGROUND_START_ACTIVITY` / 10021 is not an AOSP op

Direct search of AOSP `frameworks/base` `AppOpsManager.java`:

- **No occurrence** of `BACKGROUND_START`, `OP_BACKGROUND_START_ACTIVITY`, or `10021` on `main`, nor
  on the `android-14.0.0_r1` / `android-15.0.0_r1` tags.
- On `android-10.0.0_r1` (op integers still inline) the ops ran **0–89** — 90 small sequential
  integers. The 10000-range is outside AOSP's numbering.
- The AOSP op that governs this behaviour is system-only:

```java
/**
 * Allows an application to start an activity while running in the background.
 * Only to be used by the system.
 * @hide
 */
public static final int OP_SYSTEM_EXEMPT_FROM_ACTIVITY_BG_START_RESTRICTION = …;
```
— [AppOpsManager.java](https://github.com/aosp-mirror/platform_frameworks_base/blob/main/core/java/android/app/AppOpsManager.java)
(opstring `android:system_exempt_from_activity_bg_start_restriction`).

And AOSP explicitly warns the integers are not stable:

> "The integer values of the app-ops are not exposed. … As the integers are not part of the API,
> they might (and have) changed between platform versions and OEM implementations."
> — [AppOps.md](https://github.com/aosp-mirror/platform_frameworks_base/blob/main/core/java/android/app/AppOps.md)

**Conclusion:** `10021` is a **vendor-range op**, consistent with Xiaomi adding a private op. It is
not in AOSP, has no public name, and is not guaranteed stable across MIUI/HyperOS versions.

### 3c. The only route for a normal app is to send the user to Settings

Because `setMode` needs a signature permission (§3a) and the op is vendor-private (§3b), a normal
app **cannot** grant it; the user (or an adb/root shell) must. The shell route exists — "App-ops
can also be set via the shell using the `appops set` command" ([AppOps.md](https://github.com/aosp-mirror/platform_frameworks_base/blob/main/core/java/android/app/AppOps.md)),
i.e. `adb shell appops set <package> <op> allow` (root likewise). For a vendor op with no public
name this still needs the vendor's code, which reinforces that the only supportable path for a
Play/F-Droid app is a **settings deep-link plus user action**.

### 3d. The AOSP exemption Waqfah relies on

> "Since Android 10 (API level 29), the platform has placed restrictions on when apps can start
> activities from the background." … "An app can start an activity from the background if one of the
> following conditions is met: … The app has the `SYSTEM_ALERT_WINDOW` permission granted by the
> user. … The app has been granted the `START_ACTIVITIES_FROM_BACKGROUND` permission. …"
> — [Restrict background activity starts](https://developer.android.com/guide/components/activities/background-starts)

Holding `SYSTEM_ALERT_WINDOW` is one of the documented AOSP exemptions. The alternative,
`START_ACTIVITIES_FROM_BACKGROUND`, is `signature|privileged|vendorPrivileged|oem|verifier|role`
(AOSP framework manifest) — also unavailable to a normal app. **The vendor may add a further,
undocumented gate on top of the AOSP exemption; that is exactly the HyperOS case.**

---

## 4. How other apps handle this

### AutoStarter (`com.github.judemanutd:autostarter`)

Source: [`AutoStartPermissionHelper.kt`](https://github.com/judemanutd/AutoStarter/blob/master/autostarter/src/main/java/com/judemanutd/autostarter/AutoStartPermissionHelper.kt).
It switches on `Build.BRAND`, then opens a per-OEM **explicit component** and checks it exists with
`queryIntentActivities` / `getInstalledApplications`. Mechanism: **deep-link only** — it opens the
settings screen and cannot grant anything.

| OEM (brand) | Package | Component |
| --- | --- | --- |
| Xiaomi / Poco / Redmi | `com.miui.securitycenter` | `com.miui.permcenter.autostart.AutoStartManagementActivity` |
| Huawei | `com.huawei.systemmanager` | `…startupmgr.ui.StartupNormalAppListActivity` (+ `…optimize.process.ProtectActivity`) |
| Honor | `com.huawei.systemmanager` | `com.huawei.systemmanager.optimize.process.ProtectActivity` |
| Oppo | `com.coloros.safecenter` / `com.oppo.safe` | `…permission.startup.StartupAppListActivity`, `…startupapp.StartupAppListActivity` |
| Vivo | `com.iqoo.secure` / `com.vivo.permissionmanager` | `…ui.phoneoptimize.AddWhiteListActivity`, `…activity.BgStartUpManagerActivity` |
| Samsung | `com.samsung.android.lool` | `com.samsung.android.sm.ui.battery.BatteryActivity`, `…sm.battery.ui.usage.CheckableAppListActivity` |
| OnePlus | `com.oneplus.security` | `com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity` (+ action `com.android.settings.action.BACKGROUND_OPTIMIZE`) |
| Asus | `com.asus.mobilemanager` | `…powersaver.PowerSaverSettings`, `…autostart.AutoStartActivity` |
| Letv | `com.letv.android.letvsafe` | `com.letv.android.letvsafe.AutobootManageActivity` |
| Nokia | `com.evenwell.powersaving.g3` | `…exception.PowerSaverExceptionActivity` |

**Limitation:** AutoStarter resolves components with `queryIntentActivities` /
`getInstalledApplications(0)`, which on API 30+ are **filtered by package visibility**
([Package visibility](https://developer.android.com/training/package-visibility)) — so its
detection silently fails unless the *host app* declares the OEM packages in `<queries>`. A real
hazard in the library itself.

### dontkillmyapp.com and the DontKillMyApp app

[dontkillmyapp.com](https://dontkillmyapp.com/) (Urbandroid) documents ~20 OEMs as a
**documentation/help** approach, not a deep-linker: per vendor it publishes `explanation`,
`user_solution`, `developer_solution` via a JSON API
([repo README](https://github.com/urbandroid-team/dont-kill-my-app)). Its vendor list (from its own
API `https://dontkillmyapp.com/api/v1/output.json`): Huawei, Xiaomi, OnePlus, Samsung, Meizu, Asus,
Ulefone/RugOne, Oppo, Wiko, Lenovo, Vivo, realme, Motorola, Blackview, Tecno, Sony, Unihertz, plus
AOSP/Google, Nokia/HMD, HTC. The companion app
([`com.urbandroid.dontkillmyapp`](https://play.google.com/store/apps/details?id=com.urbandroid.dontkillmyapp))
is a **benchmark** that measures how aggressively a device kills background work and points users
back to the site. So the ecosystem uses **both** mechanisms: deep-link (AutoStarter) and help
screen (dontkillmyapp).

### MIUI-autostart (`com.github.XomaDev:MIUI-autostart`)

Source: [`Autostart.kt`](https://github.com/XomaDev/MIUI-autostart/blob/master/library/src/main/kotlin/xyz/kumaraswamy/autostart/Autostart.kt),
[`Utils.kt`](https://github.com/XomaDev/MIUI-autostart/blob/master/library/src/main/kotlin/xyz/kumaraswamy/autostart/Utils.kt).
It **reads** MIUI autostart state via reflection on `android.miui.AppOpsUtils.getApplicationAutoStart`,
calls `HiddenApiBypass.addHiddenApiExemptions("")` (which **disables the hidden-API restriction
process-wide**), and detects MIUI with `SystemProperties` — exactly like the PR. It is genuinely
used (dontkillmyapp links it as the dev-side solution), but its technique is *more* aggressive than
the PR's: the PR only reads a value, whereas this tears down hidden-API enforcement for the whole
process. A cautionary data point, not a recommendation.

---

## 5. Which OEMs/ROMs have this class of problem

All entries are documented **non-standard background-process / autostart restrictions**. Caveat:
most sources document *process killing*, which is related to but not identical to *blocking
background activity starts*. Only Xiaomi is confirmed on-device for the activity-start symptom
specifically (issue #15).

| OEM / ROM | Vendor setting as the user sees it | Source |
| --- | --- | --- |
| Xiaomi MIUI / HyperOS | "Autostart"; MIUI 14: *Settings > Apps > app > App permissions > Background autostart*; Security Center's "Open new windows while running in the background" | [dontkillmyapp/xiaomi](https://dontkillmyapp.com/xiaomi); issue #15 (device-confirmed) |
| Huawei EMUI | *Battery > App launch* → "Manage manually"; "Protected apps"; "Startup manager"; PowerGenie | [dontkillmyapp/huawei](https://dontkillmyapp.com/huawei) |
| Honor MagicOS | AutoStarter groups Honor with Huawei (`com.huawei.systemmanager`); MagicOS naming **unverified** | AutoStarter source |
| Oppo ColorOS | "Startup manager"; ColorOS 6: *App info > Allow Auto Start-up*; "Power Saver" → run in background | [dontkillmyapp/oppo](https://dontkillmyapp.com/oppo); AutoStarter |
| Vivo FuntouchOS / OriginOS | "Autostart" (*Settings > More settings > Applications > Autostart*); "High background power consumption"; "Background power consumption management" | [dontkillmyapp/vivo](https://dontkillmyapp.com/vivo); AutoStarter (`BgStartUpManagerActivity`) |
| Samsung One UI | *Battery > Background usage limits* (Sleeping / Deep sleeping / Unused apps); "Put unused apps to sleep"; "Adaptive battery" | [dontkillmyapp/samsung](https://dontkillmyapp.com/samsung); AutoStarter |
| Meizu Flyme | *Security > Permissions > Background processes > allow running in background*; "Protected apps" | [dontkillmyapp/meizu](https://dontkillmyapp.com/meizu) |
| Transsion Tecno | *Phone Master > Toolbox > Auto-start management* → "Allow app to run in background"; *Battery Lab > Power Saving Management For Apps* | [dontkillmyapp/tecno](https://dontkillmyapp.com/tecno) |
| OnePlus | "Auto-launch" (`ChainLaunchAppListActivity`); action `com.android.settings.action.BACKGROUND_OPTIMIZE` | AutoStarter source |
| Asus, Letv, Nokia, realme, Motorola, Lenovo, Wiko, Blackview, Unihertz, Ulefone, Sony | documented by dontkillmyapp (per-vendor pages); components for Asus/Letv/Nokia in AutoStarter | dontkillmyapp.com; AutoStarter |

**Thin evidence:** Infinix and itel (also Transsion) are **not documented by name** — only Tecno
is. Sony, HTC, Nokia, and stock Android rank *well* on dontkillmyapp (little/no restriction). There
is no reliable source for a MagicOS-specific permission name, and no primary source at all for the
*exact* HyperOS op integer.

---

## 6. A better way to detect the need — without a brand list or reflection

### 6a. Capability detection (recommended primary)

**Yes, viable** — probe whether the vendor settings component exists and show the row only when it
does, avoiding both `Build.MANUFACTURER` and `SystemProperties` reflection. The package-visibility
model is the key:

- `startActivity()` is **not** filtered: "You can start another app's activity using either an
  implicit or explicit intent regardless of whether that app is visible to your app"
  ([Packages visible automatically](https://developer.android.com/training/package-visibility/automatic));
  "the `startActivity()` method doesn't require package visibility … This is true for both implicit
  and explicit intents" ([Use cases](https://developer.android.com/training/package-visibility/use-cases)).
- **Resolving/querying** *is* filtered: `queryIntentActivities()`, `resolveActivity()`,
  `getPackageInfo()`, `getInstalledApplications()` ([Package visibility filtering](https://developer.android.com/training/package-visibility)).
- The documented fix is `<queries>`. A `<package>` entry is precise: "If you declare a `<package>`
  element … then the app associated with that package name appears in the results of any query to
  `PackageManager` that matches a component from that app"
  ([Declare package visibility needs](https://developer.android.com/training/package-visibility/declaring);
  [`<queries>` reference](https://developer.android.com/guide/topics/manifest/queries-element)).

So: declare candidate OEM packages (e.g. `com.miui.securitycenter`, `com.coloros.safecenter`,
`com.iqoo.secure`, `com.huawei.systemmanager`, `com.samsung.android.lool`, …) in `<queries>`, then
`resolveActivity(vendorIntent)` returns non-null **only where that component exists**. Show the help
row when it resolves — no manufacturer check, no system-properties reflection.

**Caveats:** (i) it detects "the vendor settings app exists", not "the restriction is active" — a
user who already granted it, or a ROM that renamed the component, is detected imprecisely;
(ii) the `<queries>` list is itself static and rots, though benignly (a missing package simply
never resolves); (iii) whether an *explicit* component resolves **without** `<queries>` is not
stated in the docs — the documented-safe path is to declare the package.

### 6b. Behaviour detection (recommended complement)

Detect **failure**, not brand:

- **Platform-sanctioned (Android 16+):** `StrictMode.VmPolicy.Builder.detectBlockedBackgroundActivityLaunch()`,
  set early via `StrictMode.setVmPolicy(...)`, notifies the app "when an activity launch is blocked
  (or at risk of getting blocked when the app's target SDK is raised)"
  ([Activity security / BAL](https://developer.android.com/guide/components/activities/secure-bal)).
- **Self-observation (all versions):** when a launch is blocked, "The sender app does not receive a
  direct return value or exception … the Android System internally logs a 'Background activity
  launch blocked!' message to Logcat" (tag `ActivityTaskManager`) — same page. Waqfah already polls
  `UsageStatsManager.queryEvents` for `ACTIVITY_RESUMED`; it can look for **its own** package's
  `TriggerActivity` resume after launching. If the launch produces no self-resume within a short
  window, that is a reliable, ROM-agnostic signal, and the app can surface a one-time help prompt.
  (This is essentially the "field diagnostic" already proposed in issue #15.)

### 6c. Is `Build.MANUFACTURER` alone safe?

Yes. It is a non-unique, on-device hardware attribute; it is not a "device identifier" requiring
Data safety disclosure (§1c) and appears in no Play or F-Droid policy. AutoStarter uses `Build.BRAND`;
dontkillmyapp's own developer guidance uses `Build.MANUFACTURER` as a benign switch. Neither store
restricts it.

### 6d. Ranking

| Approach | Store risk | Correctness across ROMs | Maintenance burden |
| --- | --- | --- | --- |
| **Capability detection** (`<queries>` + `resolveActivity`) | Low | Good for "is the help screen reachable"; blind to whether the restriction is active | Low–medium (query list grows; fails safe) |
| **Behaviour detection** (self-resume / Android 16 StrictMode) | Low | **Highest** — works on any ROM that blocks, no brand knowledge | Low |
| `Build.MANUFACTURER`/`BRAND` alone | Low | Medium — misses rebrands, hits non-restricting Xiaomi devices | **High** (brand lists rot) |
| Reflection on `SystemProperties` | Low policy, **medium technical** | Medium | Medium (non-SDK membership changes per release) |
| Reflection + hardcoded op `10021` | Low policy, **high technical** | **Low** — undocumented vendor int, version-fragile | High |

**Best combination:** capability detection to decide *whether to show the row*, behaviour detection
to decide *whether to nag*, and no hardcoded vendor op at all.

---

## What this means for PR #28

1. **`Build.MANUFACTURER in {xiaomi, redmi, poco}` + `SystemProperties` reflection.**
   *Brand half safe; reflection half technically risky.* The manufacturer string is benign on both
   stores. The `SystemProperties` reflection is non-SDK — not a Play violation, but it can fail on
   a given API level and adds lint `PrivateApi` noise, and it is **redundant** once capability
   detection is used. *Change:* drop the reflection; if a brand hint is kept, use it only to
   *prioritise* the help row, never to gate.
2. **Reflective `AppOpsManager.checkOpNoThrow(int,int,String)` on op `10021`.**
   *Highest risk; remove.* `10021`/`OP_BACKGROUND_START_ACTIVITY` does not exist in AOSP (§3b), the
   integer is explicitly not API-stable and varies by OEM (§3b), and the hidden overload is non-SDK.
   Gating onboarding on it is the most fragile part of the PR: a wrong/renamed op silently reports
   "granted" (the code returns `true` on any exception — it fails open) or "not granted" forever.
   *Change:* remove the op read; do not gate Continue on it.
3. **Deep-link to `miui.intent.action.APP_PERM_EDITOR` (`com.miui.securitycenter` /
   `…PermissionsEditorActivity`, `extra_pkgname`).**
   *Safe, and the standard technique.* AutoStarter does the same (different component:
   `…autostart.AutoStartManagementActivity`). Play's Deceptive Behavior policy actively favours
   user-consented, reversible settings changes (§1b). Keep it, but (a) guard it with
   `resolveActivity` and a fallback to app-details, and (b) resolve via a `<queries>` `<package>`
   entry so it works under package visibility. The exact component may vary across HyperOS versions
   — keep a fallback list like AutoStarter does.
4. **Making it a *required* row that gates Continue.**
   *Risky by construction.* Gating on a signal you cannot reliably read means locking users out
   (fail-closed) or a false pass (fail-open). *Change:* make it a **recommended/help** row shown by
   capability detection, and let behaviour detection drive a one-time prompt — never hard-gate
   onboarding on an unreadable vendor op.

**Lowest-risk alternative in one line:** declare the candidate OEM packages in `<queries>`, show the
Xiaomi (and sibling) help row only when `resolveActivity` on the vendor intent succeeds, keep
`SYSTEM_ALERT_WINDOW` as the AOSP exemption, and use **self-resume / Android 16
`detectBlockedBackgroundActivityLaunch()`** to detect actual failure and prompt once — no
`Build.MANUFACTURER` gate, no `SystemProperties`, no hardcoded op.

---

## Could not verify

- **The op integer `10021` and the name `OP_BACKGROUND_START_ACTIVITY`.** Not traceable to AOSP or
  any public vendor source; MIUI/HyperOS internals are closed. That it is *Xiaomi's* op is plausible
  and consistent with the vendor-range numbering, but **unverified**. Only the maintainer's device
  confirmation that the *permission toggle* fixes the symptom (issue #15) is established.
- **Whether `android.os.SystemProperties.get(String)` and the hidden
  `AppOpsManager.checkOpNoThrow(int,int,String)` are on the `blocked` vs `unsupported` list for any
  specific API level.** Needs the version-specific non-SDK list and/or an on-device test; the PR's
  `try/catch` fallbacks mean behaviour differs by version.
- **Whether `resolveActivity()` on an *explicit* vendor component is filtered without `<queries>`.**
  The docs state `startActivity()` is exempt and query methods are filtered, but do not spell out
  explicit-component resolution. Treat the no-`<queries>` case as untested.
- **The exact HyperOS component name and its stability across versions.** The PR's
  `…PermissionsEditorActivity` differs from AutoStarter's `…AutoStartManagementActivity`; which is
  current on a given HyperOS build is a device question.
- **Which listed OEMs block *background activity starts* specifically, versus killing background
  processes.** dontkillmyapp documents process-killing broadly; only Xiaomi is confirmed for the
  activity-start symptom. The rest are inferred from the same "autostart/background" family.
- **Infinix and itel (Transsion)** are not documented by name anywhere I could trace; only Tecno is.
- **MagicOS-specific permission naming** (Honor) is not separately documented; AutoStarter and
  dontkillmyapp both group Honor with Huawei's `com.huawei.systemmanager`.
