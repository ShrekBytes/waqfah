# Translation databases

Moved out of the README — this is the contributor-facing spec for the
downloadable Quran translations hosted in the separate
[waqfah-translations](https://github.com/ShrekBytes/waqfah-translations)
repository.

Downloadable translations are plain SQLite files, fetched at runtime and
opened read-only by Room. A valid file must have:

- table `translations (verse_id INTEGER PRIMARY KEY, text TEXT NOT NULL)`,
  one row per ayah id (1–6236),
- `PRAGMA user_version` equal to `1` (or `0`),
- served over HTTPS; add the entry to `TranslationCatalog` with its URL and
  SHA-256 checksum.

Files are verified (SQLite header + schema + version + checksum) before
being accepted; anything else fails fast with an error shown on the download
row.
