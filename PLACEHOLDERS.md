# Placeholder content — update before release

Everything below works but contains placeholder values that must be replaced
with real data before shipping publicly.

## 1. Donation accounts & icons

**File:** `app/src/main/java/com/shrekbytes/waqfah/ui/about/SupportInfo.kt`

- `DonationAccount` entries (`donations` list) use `01XXXXXXXXX` fake numbers
  for bKash / Rocket / Nagad.
- The account-type labels ("Personal") are placeholders too.

## 2. Placeholder donation icons

**Files:** `app/src/main/res/drawable/ic_bkash.png`, `ic_rocket.png`,
`ic_nagad.png`

Drawn stand-ins, not official brand icons. Replace with official assets (and
mind each brand's usage guidelines) or remove the icons entirely.

## 3. Contact / contributor info

**File:** `app/src/main/java/com/shrekbytes/waqfah/ui/about/SupportInfo.kt`

- `CONTACT_EMAIL` (`shrekbytes@duck.com`) — confirm this is the address you
  want published inside the app binary.
- `contributors` list — keep roles/links current.

## 4. Version metadata

**File:** `app/build.gradle.kts`

- `versionCode = 2`, `versionName = "1.0.1"` — bump on every release.

## 5. Release signing

Release builds currently produce an **unsigned** APK
(`app-release-unsigned.apk`). A `signingConfigs` entry (fed from env vars or
`local.properties`) is needed for store/F-Droid releases.

---

*Nothing else in the codebase is known to be placeholder data. Search tag:
`TODO(user)` (currently only in SupportInfo.kt).*
