---
name: Waqfah
description: A pause, never an obstacle — ink on warm paper before every app open
colors:
  # Canonical instance: Light theme + Sage accent. Sibling palettes (Dark,
  # Cream, Stone, Midnight, Indigo) and accent variants are documented in the
  # Colors section; the source of truth is ui/theme/Color.kt + Theme.kt.
  paper: "#F6F3EC"
  ink: "#2A2823"
  ink-muted: "#8A8275"
  ink-soft: "#B7AF9C"
  hairline: "#E4DFD2"
  sage: "#71835F"
  sage-ink: "#FBFAF6"
  sage-soft: "#E3E8DA"
  danger: "#A15C4B"
typography:
  title:
    fontFamily: "system default sans (Roboto)"
    fontSize: "22sp"
    fontWeight: 600
    lineHeight: "28sp"
    letterSpacing: "-0.2sp"
  body:
    fontFamily: "system default sans (Roboto)"
    fontSize: "14sp"
    fontWeight: 400
    lineHeight: "22sp"
  label:
    fontFamily: "system default sans (Roboto)"
    fontSize: "11sp"
    fontWeight: 600
    letterSpacing: "0.9sp"
rounded:
  pill: "50% (full capsule)"
  card: "16dp"
  inner: "12dp"
  field: "7dp"
spacing:
  xs: "4dp"
  sm: "8dp"
  md: "10-14dp"
  lg: "16dp"
  xl: "20dp"
  gutter: "28dp"
components:
  button-primary:
    backgroundColor: "{colors.sage}"
    textColor: "{colors.sage-ink}"
    rounded: "{rounded.pill}"
    height: "48dp"
  chip-selected:
    backgroundColor: "{colors.sage}"
    textColor: "{colors.sage-ink}"
    rounded: "{rounded.pill}"
  tab-selected:
    backgroundColor: "{colors.sage-soft}"
    textColor: "{colors.sage}"
    rounded: "{rounded.pill}"
  switch-on:
    backgroundColor: "{colors.sage}"
    rounded: "{rounded.pill}"
  card-reading:
    backgroundColor: "{colors.hairline}"
    textColor: "{colors.ink}"
    rounded: "{rounded.card}"
---

# Design System: Waqfah

## Overview

**Creative North Star: "The Quiet Page"**

Waqfah's interface is a page, not an app chrome: warm paper, one voice of
ink, hairline rules where a heavier system would draw boxes or cast
shadows. The reading pause it inserts into someone's day is visually the
same object as the Home tab that teaches it — a quiet page of ink on warm
paper that appears, is read or skipped, and falls away. Nothing glows,
nothing blurs, nothing begs. The system is calm, warm, and literary: a
printed page that happens to glow.

Depth is conveyed by ink density, never by elevation: a 1dp hairline in
`line`, or a faint tint of the same color, separates content from paper.
Motion is tactile but restrained — springs on selection, small press-shrinks,
instant color swaps where animation would read as lag. Confirmed
anti-references: neon accent glow, glass blur, gamified streaks, Material's
default ripple and floating-label fields.

**Key Characteristics:**
- Warm paper backgrounds with a full ink-to-muted-to-soft text ladder
- Full-capsule (pill) geometry for every interactive element
- Flat surfaces: 1dp hairline borders and line-tinted fills, zero shadows
- No ripples; press feedback is scale-shrink and a slow-fading highlight
- One accent voice per theme, swappable from five hand-tuned accents
- Three bundled Arabic typefaces as first-class content, not decoration

## Colors

A warm, low-chroma family: every palette is paper-and-ink at heart, with a
single muted accent doing almost no work — its rarity is the point. The app
ships six full palettes and, for the three base ones, a five-choice accent
picker; every value below is hand-tuned per palette and per mode (never
runtime-derived), so treat `ui/theme/Color.kt` as the source of truth.

### Primary
- **Sage** (`sage`, light `#71835F` / dark `#93A87D`): the default accent.
  Selected pills, primary buttons, progress, links, the mark-read pending
  state. Each of the five accents ships its own hand-tuned soft and ink
  companions for light and dark (see variant table).

### Secondary
The accent picker's alternatives, light/dark pairs — **Clay** (`#A6634C` /
`#C98F79`), **Slate** (`#5E7A93` / `#86A4BE`), **Plum** (`#8B6483` /
`#AC8AA3`), **Ochre** (`#9C7936` / `#C7A667`). Only one is ever live.

### Tertiary
**Danger** (`#A15C4B` light / `#C97A64` dark): the sole warning voice,
terracotta rather than red, matching the paper family.

### Neutral
- **Paper** (`#F6F3EC`): the light background — warm off-white, never pure
  white.
- **Ink** (`#2A2823`): primary text, warm near-black.
- **Ink Muted** (`#8A8275`): secondary text.
- **Ink Soft** (`#B7AF9C`): disabled and tertiary text.
- **Hairline** (`#E4DFD2`): 1dp borders, dividers, toggle track, and — at
  12% alpha — the reading card's paper tint.

### Fixed-accent palettes
Four themes ship fixed accents (no picker): **Cream** (`#EAE2CE` paper,
terracotta accent `#B2543D`), **Stone** (monochrome sage-gray `#B0BAB0`,
accent = ink — the only palette where accent and ink are the same voice),
**Midnight** (OLED `#0C0B09`, lamplight-gold accent `#C9A96B`), **Indigo**
(night-sky `#161A2E`, gold accent `#D4A15C`).

### Named Rules
**The One Voice Rule.** The accent appears on at most one or two elements
per screen. If a second element needs emphasis, promote it with ink weight,
not a second color.

**The Ink Ladder Rule.** Text hierarchy is ink → inkMuted → inkSoft, never
opacity fades of ink and never new grays. Every palette ships all three.

**The Hand-Tuned Rule.** Never derive a companion color at runtime (lerps,
luminance thresholds). Every accent/palette/mode combination is a
hand-written value, because derivation visibly drifts in dark themes.

## Typography

**Display Font:** none — the UI scale tops out at Title; the system default
sans (Roboto) carries every UI string.
**Body Font:** system default sans, with fallback.
**Content Fonts:** Digital Khatt Indopak, MeQuran, Amiri — the three bundled
Arabic faces, user-selectable per script (Indopak/Uthmani).

**Character:** The UI type is deliberately invisible — small, close-set,
system-native — so the user's chosen Arabic face is always the largest,
most beautiful thing on screen. UI type never competes with scripture.

### Hierarchy
- **Title** (SemiBold, 22sp, -0.2sp): screen titles (SettingsScaffold).
- **Body** (Regular, 14sp/22sp): primary reading-level UI text.
- **Body Small** (Medium, 13-13.5sp): list titles, stepper values.
- **Label** (SemiBold, 11sp, +0.9sp): tab labels and small caps-like tags.
- **Arabic ayah** (user-set size, `sp`-scaled): the only display-scale text.

### Named Rules
**The System Voice Rule.** UI text uses the system font at modest sizes;
custom typefaces are reserved for Quranic content. Importing a display font
for chrome would break the page metaphor.

## Layout

Single-column phone layout, edge-to-edge: the theme's root `Surface` paints
the full screen and an inner box applies safe-drawing insets, so system bars
never expose an unthemed gap. Screen gutters are 28dp on sub-screens
(SettingsScaffold) and 20dp around the floating tab bar; card interiors use
16dp; list and control rhythm clusters at 4/8/10/14dp. Density is airy by
default — generous vertical padding around rows (10-12dp) and section
separation via dividers or spacing, not boxed cards.

## Elevation & Depth

**The Hairline Rule.** This system is flat by conviction: there is not one
shadow in the codebase, and `Modifier.shadow` never appears. Depth is
conveyed three ways only — a 1dp hairline border in `line` (the crisp card
treatment), a 12%-alpha tint of `line` as a fill (the reading-card paper),
and, in dark themes only, a subtle lightening of floating surfaces (the tab
bar lerps 7% toward white) to read as a lifted sheet. Never introduce
shadows, gradients-as-depth, or blur.

## Shapes

The form language is round: **every** interactive element is a full capsule
(`RoundedCornerShape(50)` or `CircleShape`) — buttons, chips, switches,
steppers, the mark-read pill, the search field, the floating tab bar. Cards
use 16dp; small inner surfaces 12dp; text-field-adjacent boxes 7dp. Corners
are never sharp; borders are always 1dp `line`, never 2dp, never darker
than `line`.

## Components

### Buttons
- **Shape:** full pill (50%), 48dp tall, full-width for primary CTAs.
- **Primary:** accent fill, accentInk text, 15sp SemiBold. Disabled: accent
  at 35% alpha, accentInk at 70%.
- **Press:** the shared press language is scale-shrink (0.96-0.97, spring)
  — never a Material ripple.
- **Pill actions** (Grant, Mark Read): smaller capsules, accentSoft fill
  with accent text, 12.5-13sp SemiBold.

### Chips
- **Style:** pill; selected = accent fill + accentInk SemiBold; unselected =
  transparent, inkMuted Medium, no border.
- **State:** radio-style selection (one live at a time) with real
  `Role.RadioButton` semantics; groups flow-wrap with 8dp gaps.

### Cards / Containers
- **Corner Style:** 16dp.
- **Background:** `line` at 12% alpha over paper.
- **Shadow Strategy:** none — see The Hairline Rule.
- **Border:** 1dp `line`.
- **Internal Padding:** 16dp.

### Inputs / Fields
- **Style:** hairline pill search field — 1dp `line` border, paper-tone
  fill, no floating label, forced LTR so RTL content never flips the caret.
- **Focus:** border remains `line`; the field does not glow or thicken.
- **Toggles:** custom slim switch (44×26dp, 20dp paper knob, 3dp inset);
  on = accent, off = `line` track; real `Role.Switch` semantics.

### Navigation
Floating detached pill bar, two destinations (Home, Settings), 20dp side
insets. Selected tab: accentSoft pill behind a 22dp icon + 11sp SemiBold
label; unselected: transparent, inkMuted. Selection swaps colors instantly
(animated color here mid-flight reads as a flash); the pill itself bounces
via spring scale. Light themes add the 1dp hairline; dark themes lift the
fill instead.

### Signature: the Reading Card
The system's reason to exist, identical on Home and in the interstitial:
16dp card, hairline border, line-tinted paper; centered surah header with
18dp divider dashes flanking the ayah label; the Arabic face at
user-chosen size; optional transliteration and translation stacked beneath
short center rules. All content centered, all sizing user-controlled in
`sp`, RTL handled per-segment rather than app-wide.

### Signature: the Mark-Read pill
The single gamification-free reward: pending = solid accent ("Mark read"),
marked = accentSoft with accent check. State swaps snap when swiping between
ayahs (no cross-fade lag) and tween smoothly (160ms) on direct taps.

## Do's and Don'ts

### Do:
- **Do** use the 9-role custom palette (`WaqfahTheme.colors`) — accent,
  accentInk, accentSoft, background, ink, inkMuted, inkSoft, line, danger.
- **Do** put every interactive element in a capsule; circle for icon-only.
- **Do** give press feedback as scale (springs, MediumBouncy for selection)
  or the row highlight (60ms in, 220ms out).
- **Do** hand-write every new color's light and dark values per palette.
- **Do** give custom controls real semantics (Role.Switch, Role.RadioButton,
  Role.Tab) and merge duplicated TalkBack announcements away.

### Don't:
- **Don't** add shadows, elevation, blur, or gradient depth — The Hairline
  Rule has no exceptions.
- **Don't** use Material's default ripple, Switch, floating-label TextField,
  or filled arrow icons (the hand-drawn two-segment chevron exists because
  Material's glyphs read heavier).
- **Don't** invent grays or fade ink with alpha — climb the ink ladder.
- **Don't** let a second color compete with the accent on one screen.
- **Don't** animate a color swap that must feel instant (tab selection,
  ayah-swipe state) — snap it.
- **Don't** fix Arabic text sizes in `dp` or hardcode LTR around RTL content.