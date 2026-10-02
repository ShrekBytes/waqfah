# `quran_core.db` — what it is and where it comes from

`app/src/main/assets/databases/quran_core.db` is the app's bundled Quran text. It
is **read-only data, not code**: it ships as an asset, is copied to internal
storage on first use, and is rebuilt wholesale each release (see
`docs/ARCHITECTURE.md`, `data/local/core`). It contains no executable content.

This file records its provenance so a reviewer can check the claim rather than
take it on trust.

## Shape

Verified with `sqlite3` in read-only mode:

| Property | Value |
|---|---|
| Size | 12,378,112 bytes |
| `PRAGMA user_version` | `0` |
| `PRAGMA integrity_check` | `ok` |
| `page_size` × `page_count` | 4096 × 3022 |
| `surahs` rows | 114 |
| `verses` rows | 6,236 |
| `SUM(surahs.ayah_count)` | 6,236 (agrees with the verse count) |
| `verses.id` | contiguous 1…6236, no gaps |
| Distinct `verses.surah_no` | 114 |
| Empty text values | none, in any of the four text columns |

```sql
CREATE TABLE surahs (
    id INTEGER NOT NULL, surah_no INTEGER NOT NULL UNIQUE,
    name_arabic TEXT, name_english TEXT, name_bengali TEXT,
    ayah_count INTEGER NOT NULL, PRIMARY KEY(id)
);
CREATE TABLE verses (
    id INTEGER NOT NULL, surah_no INTEGER NOT NULL, ayah_no INTEGER NOT NULL,
    arabic_indopak TEXT NOT NULL, arabic_uthmani TEXT NOT NULL,
    bn_transliteration TEXT NOT NULL, en_transliteration TEXT NOT NULL,
    PRIMARY KEY(id)
);
```

## Provenance of `arabic_uthmani` — verified

The Uthmani column is the **Tanzil Project's Uthmani Quran text, verbatim**. Every
one of the 6,236 verses is accounted for against Tanzil's published text:

| | Verses |
|---|---|
| Byte-identical to Tanzil | 5,925 |
| Differ only by a leading rub-el-hizb (`U+06DE`) or sajdah (`U+06E9`) mark | 199 |
| Differ only because Tanzil's leading Basmalah is stripped | 112 |
| Unexplained | **0** |

The 112 Basmalah cases are exactly the surah-initial verses excluding surahs 1
and 9 — `SELECT COUNT(*) FROM verses WHERE ayah_no = 1 AND surah_no NOT IN (1, 9)`
returns 112. Tanzil prefixes the Basmalah to ayah 1 of every surah but Al-Fatihah
and At-Tawbah; this database drops it, so those surahs begin at their first
numbered ayah. Al-Fatihah's Basmalah is kept, because there it *is* ayah 1.

To re-check:

```bash
curl -skL -o /tmp/tanzil.txt \
  'https://tanzil.net/pub/download/index.php?quranType=uthmani&outType=txt-2&agree=true&tatweel=true'
# then compare each verses.arabic_uthmani against /tmp/tanzil.txt, keyed by surah|ayah,
# allowing for a leading U+06DE/U+06E9 and for Tanzil's Basmalah prefix
```

## Provenance of the other columns — not independently verified

- `arabic_indopak` — all 6,236 verses end with `U+06DD` (end of ayah), and 1,834
  embed the ayah number after it. No leading rub-el-hizb marks.
- `en_transliteration`, `bn_transliteration` — Latin and Bengali-script
  transliteration, no empty values.
- `surahs.name_arabic` / `name_english` / `name_bengali` — surah names in three
  scripts.

These come from the **Quranic Universal Library** (QUL) per the app's own credits
screen. They have **not** been compared against an upstream copy the way the
Uthmani column has, so treat that attribution as unverified.

## Licence basis

Tanzil's text licence grants exactly what the app needs:

> "Permission is granted to copy and distribute verbatim copies of the Quran text
> provided here, but changing the text is not allowed. The text can be used in any
> website or application, provided that its source (Tanzil Project) is clearly
> indicated, and a link is made to tanzil.net to enable users to keep track of
> changes."

Both conditions are met: the in-app Gratitude screen credits the Tanzil Project
with a link to `tanzil.net`. QUL is credited there too. The text is redistributed
unmodified apart from the two documented transformations above.

**This is not a free licence, and that is deliberate.** The verbatim-only clause is
a No Derivatives restriction, and Tanzil's translation terms are non-commercial —
the two restrictions F-Droid's `NonFreeAssets` anti-feature is defined around. The
bundled Quran data is therefore one of the two bases for the `NonFreeAssets` flag in
`metadata/dev.shrekbytes.waqfah.fdroid.yml` (see `docs/fdroid-submission.md` §1); the
other is the payment-logo artwork on the donation screen. F-Droid's inclusion policy
allows this: assets may use non-commercial licences provided they permit
redistribution, and Tanzil's do. So it is a filterable label, not a compliance
problem, and it does not block the submission.

## The gap

**No generation script exists in this repository.** The file was assembled
out of tree, and nothing here records the exact steps, source revisions, or the
transformations beyond the two established above. Anyone rebuilding the database
from scratch would have to reconstruct the process.

Closing that gap would mean committing a generator (or at least a written recipe)
that reproduces this file byte-for-byte, and a test that fails when the shipped
database drifts from it. Not done yet.
