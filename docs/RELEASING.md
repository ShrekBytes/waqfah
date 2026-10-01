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
