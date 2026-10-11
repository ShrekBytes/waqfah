# Bundled third-party assets

Waqfah's own code is AGPL-3.0 (see the repository root `LICENSE`). The Arabic
fonts below are third-party works under the SIL Open Font License 1.1, and their
licenses are reproduced here in full so the notice travels with the copies that
ship inside the APK — which OFL-1.1 requires.

| Asset | Version | Copyright | License file |
|---|---|---|---|
| `res/font/amiri.ttf` | 1.003 | Copyright 2010-2022 The Amiri Project Authors | `amiri-OFL.txt` |
| `res/font/digital_khatt_indopak.ttf` | 0.1 | Copyright © 2024-2025 Amine Anane; © 2024-2025 Tarteel Inc. | `digitalkhatt-indopak-OFL.txt` |

Each license file is the upstream file verbatim, from
<https://github.com/aliftype/amiri> and
<https://github.com/DigitalKhatt/indopakfont> respectively. The copyright and
license strings embedded in the font binaries agree with them.

`digital_khatt_indopak.ttf` ships as a **format conversion** of the upstream
`DigitalKhattIndoPak-Regular.otf` (static CFF2 outlines) to TrueType `glyf`
outlines: the `cmap`, GSUB, GPOS, GDEF, `OS/2`, `hmtx`, `name` and `post`
tables are the upstream bytes unchanged — same glyphs, same character
coverage, same shaping, same advances — while `head`, `hhea` and `maxp`, which
describe the outline format itself, and the (dropped) `DSIG` signature table
necessarily differ. Upstream ships CFF2, which failed to load on at least one
OEM Android build (Realme C12, Android 11: the ayah silently rendered in the
device's fallback Arabic font, with Quranic diacritics misplaced and
`U+06DD`/`U+08D7` as tofu). TrueType outlines load on every Android version
the app supports. `QuranFontCoverageTest` pins the outline format and
re-checks glyph coverage against the bundled Quran text on every build.

The font name is kept as-is deliberately: the upstream license header above
declares no Reserved Font Name, so OFL-1.1's rename requirement for modified
fonts does not apply. Recorded here so the choice reads as checked, not
overlooked.

Not covered here: the bundled Quran text (`assets/databases/quran_core.db`) and
the bundled translations (`assets/translations/`). Those are used under the
Tanzil Project's terms, which permit use in any application with attribution and
a link to tanzil.net — both credited on the in-app Gratitude screen.
