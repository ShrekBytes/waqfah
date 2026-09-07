# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

Practicing Muslims who want more daily Quran exposure. Their situation: a
busy day spent moving between phone apps, with no dedicated reading habit.
The job Waqfah does for them: fitting consistent Quran reading into that
day, one ayah at a time, without demanding a new routine.

## Product Purpose

Waqfah (وقفة — "a pause") shows a single Quranic ayah before the apps the
user chooses. When a monitored app opens, a translucent reading screen
appears first; the user reads or skips it, then continues into the app
exactly where they left off. It is not trying to stop anyone from doing
anything, fix a habit, or change how the phone is used. Success is
consistent, low-friction daily exposure to the Quran — not screen-time
reduction and not behavior change.

## Positioning

A pause, never an obstacle. No other product can truthfully copy the
combination: an interposition at app-open (Usage Stats + display-over-apps)
that shows exactly one ayah and then falls through — no blocking, no limits,
no tracking, no lectures. Digital-wellbeing blockers prevent; Waqfah pauses.

## Operating Context

- Android 9+ (minSdk 28, targetSdk 37); Kotlin, Jetpack Compose (Material 3),
  Room, DataStore, Hilt.
- Onboarding requests the two required grants (Usage access, Display over
  other apps) and offers two recommended rows (unrestricted battery,
  notifications) whose denial never blocks anything.
- Detection runs as a `specialUse` foreground service polling roughly once
  per second while the screen is on; the reading pause appears over the
  monitored app. See `docs/ARCHITECTURE.md`.
- Offline-first: everything works on-device; the only network traffic is an
  explicitly requested, checksum-verified translation download from the
  separate waqfah-translations repository.
- App UI in English and Bengali, or follows system language; Arabic content
  renders RTL.
- Distribution: GitHub Releases APK today; F-Droid and Google Play coming.

## Capabilities and Constraints

- Reading modes: sequential (lowest unread ayah) or random (any unread ayah),
  with progress tracking across the whole Quran (6236 ayat).
- Arabic display: Indopak and Uthmani scripts, bundled fonts, adjustable
  sizes; optional transliteration; translations bundled in English and
  Bengali with more available as downloads.
- Trigger restraint: at most one reading screen per app open; a per-app
  cooldown (or Off) controls repeats; share-sheet and "Open with" entries
  never trigger; never on calls; never on quick switch-backs.
- Feature tour over the Home tab with TryIt steps on the live reading card;
  a skipped tour persists nothing.
- No accounts, no servers, no analytics today. The privacy stance (nothing
  collected, nothing sent) is current-state positioning, changeable only
  with explicit user consent — not a binding constraint.
- Licensed AGPL-3.0; free software.
- Terminology is normative: `CONTEXT.md` defines trigger, interstitial,
  fresh open, indirect entry, switch-back, cooldown, monitored app, and
  related terms. Use those words, not synonyms.

## Brand Commitments

- Name: Waqfah (وقفة — "a pause").
- Binding identity: a pause, never an obstacle. No blocking, no limits, no
  lectures; Waqfah never tries to stop the user from doing anything. This is
  permanent product identity — future work must preserve it.
- Voice: plain and non-preachy. The product explains what it does and asks
  nothing of the user beyond the moment of reading.

## Evidence on Hand

- `README.md` — mechanism, features, permissions table, privacy statement,
  building/releasing, translation-database contribution format.
- `CONTEXT.md` — the full ubiquitous-language glossary (source of product
  terminology).
- `docs/ARCHITECTURE.md` and `docs/adr/0001…0004` — durable behavioral
  rulings (trigger stamp at trigger time; toggle governs detection only;
  skipped tour persists nothing; interstitial identity is the class name).
- Bundled data: `app/src/main/assets/databases/quran_core.db`, bundled
  translation DBs (en/sahih, bn/taisirul); UI strings in
  `values/strings.xml` (~246 strings) with a `values-bn` variant; launcher
  icons in `mipmap-anydpi`.
- Absences future work must not fabricate: no testimonials, no press, no
  marketing site, no user research or metrics.

## Product Principles

1. **A pause, never an obstacle.** The interstitial falls through; restraint
   rules (one per open, no triggers on indirect entries or calls) are the
   product's identity expressed in behavior, not edge-case handling.
2. **Consistency over intensity.** Success is an ayah a day across months;
   friction is the enemy — dismissal is always one step, never a negotiation.
3. **Respect autonomy absolutely.** No guilt, streak-shaming, or
   dark patterns; the user can always skip, and skipping is never punished.
4. **Trust through verifiability.** Everything stays on-device today; the
   single network fetch is pinned by checksum. Privacy posture changes only
   with explicit consent.
5. **Honesty in the small print.** Permissions, network use, and limits are
   stated plainly in-product (onboarding, privacy policy screen), not just
   in the README.

## Accessibility & Inclusion

- RTL support for Arabic content (`supportsRtl`, locale config); per-app
  locales persist below Android 13.
- Adjustable Arabic text sizes, two scripts (Indopak/Uthmani), and several
  bundled fonts serve differing reading traditions and eyesight needs.
- App language: English, Bengali, or system.
- Recommended permission denials never block or degrade onboarding.
- No formal audit standard has been established beyond Android platform
  conventions; none was confirmed in this interview.
