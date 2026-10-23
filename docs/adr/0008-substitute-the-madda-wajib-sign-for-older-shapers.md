# The madda-wajib sign is substituted for shapers older than Unicode 14

Reading an ayah is the app's one thing it cannot get wrong, and on some devices
it drew words no reader of the Quran would accept. On a Realme C12 running
Android 11, every word carrying the madda-wajib sign came out broken:
أُولَٰئِكَ appeared with its lam detached from the rest, the alef-maksura in
initial form, and floating diacritics displaced. The report named the device
and showed a screenshot; it is the reason for `9929b32`, which converted the
bundled font from CFF2 to TrueType and shipped a coverage test and a startup
probe alongside it. The conversion was a real fix for a real failure — the
CFF2 outlines the device could not parse — but it did not touch this one. After
it, the same device drew the same broken word.

The cause is upstream of the font. HarfBuzz decides cursive joining from
Unicode data, not from the glyphs it is given, and Android 12 and below vendor
shaper tables generated from `ArabicShaping-12.0.0` (Android 11 ships
HarfBuzz 2.6.4, Android 12 ships 2.8.x, both with those tables). U+089C —
ARABIC MADDA WAAJIB, the sign these words carry — was assigned in Unicode 14
(2021), after those tables were cut. An unassigned codepoint resolves to
`JOINING_TYPE_X`, which HarfBuzz settles from the general category, and a
character that is neither a mark nor a format character comes out
`JOINING_TYPE_U`: a non-joining base. So the shaper places an opaque character
in the middle of a join and the word splits at exactly that point. Because the
decision happens before any font is consulted, no change to the font can fix
it, which is what the conversion discovered the hard way.

Android 13 vendors `ArabicShaping-14.0.0` tables, where U+089C is a mark and
joins normally. That is the version boundary the fix is drawn at.

## The substitution

`arabicTextFor` — the one function that decides which column of
`quran_core.db` a reading surface draws — hands Android 12 and below the same
word with U+089C replaced by U+0653, ARABIC MADDAH ABOVE. U+0653 is a mark in
every Unicode version this app can meet, so every shaper treats it as
transparent and the join holds; the bundled Digital Khatt draws it from its
own `maddahabove` glyph, so the word is drawn with the mark still where a
reader expects a madda to sit. The substitution is not cosmetic — it is the
same sign, spelled in a codepoint the platform understands.

From Android 13 up, the text goes to the shaper exactly as stored. That is the
majority of devices, and on those the font's own `maddawaajib` glyph is the
better mark — it is the sign as the mushaf sets it, where the stand-in draws
the common maddah instead. The two shapes differ (glyph-level IoU 0.61), so
applying the substitution everywhere would trade a rare broken word for a
permanent quiet alteration of every word on every device. The gate keeps the
alteration where it buys something.

The substitution happens at display time rather than in the database for three
reasons. `quran_core.db` is redistributed under Tanzil's licence, which grants
verbatim copying and forbids modification — the shipped bytes stay untouched
even though the drawn text on affected platforms is not. The database is
rebuilt out of tree with no generator committed (`docs/quran-core-db.md`), so
an edit to it would be silently reverted by the next rebuild. And the condition
is a property of the device, not of the text: the same string is right on
Android 13 and broken on Android 12, and only the display path knows which
device is asking.

Three alternatives were considered and rejected. Rewriting the text at read
time by stripping the sign would draw words missing a required madda, which is
worse than the defect it repairs. Bundling a newer HarfBuzz or calling into ICU
for shaping would put a second text stack in the app to fix one codepoint.
Rendering the ayah through a WebView, whose Chromium carries its own shaper,
would sidestep the platform entirely at the cost of replacing the app's own
rendering with a browser's.

## Scope

The sign appears 2,098 times across 1,576 verses, always in the indopak column;
the uthmani column carries none of it. The remaining U+08xx marks in the text
— the waqf and sajda signs, and U+08D9 — are all assigned in Unicode 9 or 11,
before the affected tables were cut, so every shaper already treats them as
transparent marks and none of them breaks a word. U+089C was the only codepoint
in the database that needed covering, and it is covered generally rather than
per verse: the door is in the formatting function, so a future text rebuild
cannot reintroduce the defect by including a word this fix has never seen.

## Consequences

- Android 12 and below draw the madda-wajib sign as a maddah-above mark. The
  word joins correctly; the mark is the common maddah rather than the mushaf's
  waajib sign. Android 13 and up are unchanged from before this fix.
- The gate reads `Build.VERSION.SDK_INT`, taken as an argument so both sides are
  pinned by tests on the JVM. A test asserting the Android 13 path is the thing
  that would catch a gate moved too early; a test asserting the substitution is
  the thing that catches it moved too late, or lost.
- `QuranFontProbe` still covers the failure mode this one was mistaken for —
  a font that fails to load and leaves ayahs in a fallback face. A font that
  loads and is then shaped wrongly is a different failure, and it is diagnosed
  in the shaper's Unicode tables rather than at font-load time.
- Nothing about the stored text changes, so the shipped database keeps matching
  its recorded provenance byte for byte.