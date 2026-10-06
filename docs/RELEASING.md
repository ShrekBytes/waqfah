# Releasing

Moved out of the README — this is maintainer-facing, not contributor-facing.

`./gradlew :app:assemblePlayRelease` only produces an installable APK when a
release keystore is configured — otherwise the build still succeeds (CI's
compile check relies on that) but emits an unsigned APK.

Signing is wired into the release build type and reads from either:

- a **`keystore.properties`** file at the repo root (gitignored):

  ```properties
  storeFile=path/to/waqfah-release.jks   # relative to the repo root, or absolute
  storePassword=…
  keyAlias=waqfah
  keyPassword=…
  ```

- or **environment variables** — `WAQFAH_STORE_FILE`, `WAQFAH_STORE_PASSWORD`,
  `WAQFAH_KEY_ALIAS`, `WAQFAH_KEY_PASSWORD` — which override the file, so CI
  can inject the secrets without writing it to disk.

With either source complete, the release task emits a signed, installable APK
at `app/build/outputs/apk/<flavour>/release/`. A half-configured source —
some properties set, others missing — fails the build instead of quietly
emitting an unsignable artifact.

That keystore is the **Google Play** signing key. The F-Droid build is not
signed with it: F-Droid builds from source with no keystore present and
signs the result with its own key, which is why the two flavours carry
different application IDs.

Create a keystore once (keep it out of the repository and back it up safely —
losing the key means no future update can install over the released app):

```bash
keytool -genkeypair -v -keystore waqfah-release.jks -alias waqfah \
  -keyalg RSA -keysize 4096 -validity 10000
```

## Release notes

Store release notes live at
`fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt` — keyed by
**versionCode**, not version name, because a name can be reused and a code
cannot. F-Droid reads them for the listing's "What's new", and Play's release
notes should carry the same text; Play caps that field at 500 characters, so
keep the file to a short intro and a bullet list.

This is a different artifact from the annotated tag. The tag `v<versionName>`
marks the commit that shipped and carries a short message for whoever reads the
history. It is **not** a notes store: no store reads tags, the text isn't
greppable in the working tree, and editing it means re-tagging.

The file is not optional. `checkChangelog` fails the release build when any
locale has no changelog for the current `versionCode`, and every
`assemble*Release` and `bundle*Release` task depends on it — so CI's R8 and AAB
checks carry the same gate, and bumping `versionCode` without the file stops the
build:

```
No release notes for version 2.0.0 (versionCode 4). Add en-US/changelogs/4.txt
```

The check covers every directory under `fastlane/metadata/android/`, so adding a
locale and leaving its changelog out fails too. Run it on its own with
`./gradlew :app:checkChangelog`.

## Release checklist

Two stores, one version. Both read the same two numbers out of
`app/build.gradle.kts`, so bump them together in one commit.

1. **Bump `versionCode` and `versionName`.** `versionCode` must increase or
   neither store will accept the update, and it is what keys the changelog file.
   `versionName` is what users see, and F-Droid takes the published name from
   the tag — so the two have to agree.
2. **Add the changelog** for the new `versionCode`, per "Release notes" above.
   The release build fails without it.
3. **Commit, then tag `v<versionName>`** (annotated) and **push the tag**. This
   is the only F-Droid step: `UpdateCheckMode: Tags` + `AutoUpdateMode: Version`
   make F-Droid's bot walk the tags and build the new one, with no merge request
   to `fdroiddata`. It is not instant — the previous build appeared inside
   24–48 hours.
4. **Build the AAB and upload it to Play**, pasting the same text into the
   Console's release notes. CI only proves the bundle compiles; it has no
   keystore, so what it produces is unsigned.

The tag has to come *after* the changelog is committed. F-Droid reads the
fastlane metadata from the tagged commit, so tagging first ships a version with
no release notes — the failure `checkChangelog` prevents, arriving through the
one door it cannot see.
