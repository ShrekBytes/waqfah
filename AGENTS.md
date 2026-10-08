# AGENTS.md

These are defaults, not rigid rules. Use judgment. When there's meaningful uncertainty, prefer the safer and simpler option.

## General

### Communication

Use clear, plain language. Don't add jargon just to sound technical, and explain a term when it matters.

Be direct. Don't pad responses with unnecessary summaries or filler. Plain language does not mean less detail — for substantial tasks, give enough detail to make the reasoning and changes easy to follow.

These communication preferences apply to chat. Code, comments, documentation, commit messages, and other project artifacts should follow the project's existing technical style.

### Working rules

- **Answer questions without changing the code.** If I'm asking for information or an explanation, answer it without changing the code. Only make changes when I ask you to.
- **Surface uncertainty; don't guess past it.** Use existing code, tests, documentation, and project conventions to resolve ordinary choices. If ambiguity would materially change what gets built, stop and ask rather than choosing arbitrarily.
- **Stay on task.** Keep changes focused on the request. Don't fix unrelated problems unless necessary; mention them instead if they're worth calling out.
- **Prefer the simplest solution.** Don't add abstractions, dependencies, layers, or future-proofing without a concrete need. Avoid defensive handling for purely hypothetical scenarios.
- **Follow existing patterns.** Match the project's naming, structure, formatting, error handling, architecture, and conventions unless there is a concrete reason not to.
- **Touch only what you must.** Don't refactor, rename, or reformat adjacent code unless the change requires it. Remove imports, variables, or functions that your own change makes unused.
- **Tests should reflect the change.** Add or update relevant tests when behavior changes or existing tests cover the area you're touching. Never weaken, skip, delete, or rewrite a test merely to make it pass. If an existing test appears wrong, explain why before changing it.
- **Escalate after two failed attempts.** Don't repeat the same approach. Reconsider the diagnosis or change the approach; if the next step isn't clear, ask.
- **Verify before finishing.** Start with the narrowest relevant test or check, then run broader checks when warranted. Don't claim success based only on reading code.
- **For substantial or multi-step tasks, state a brief plan before starting.** Keep it practical and include how each step will be checked:

  ```
  1. [step] → verify: [check]
  2. [step] → verify: [check]
  ```

  Keep the plan practical and update it if the approach materially changes.

### Guardrails

- Sensitive local configuration stays out of the repository: `local.properties` (SDK paths) and `keystore.properties` (release signing) are gitignored — never commit them, secrets, or API keys. Flag anything sensitive already committed.
- Don't perform destructive or irreversible actions unless I've explicitly asked for them and the scope is clear.

## Before changing code

For anything beyond a mechanical edit:

1. Read the relevant code and nearby tests first.
2. Look for existing patterns before creating new ones.
3. Read the relevant domain docs when the change touches project concepts or boundaries — `CONTEXT.md` (glossary) and `docs/adr/`.
4. `docs/ARCHITECTURE.md` is the codebase map; read the section covering the area you're touching.

Don't read the entire documentation tree when a targeted section is enough.

## Skills

- Use a relevant skill when one exists for the task.
- Follow the skill's instructions for that task.
- If instructions conflict, follow the applicable instruction hierarchy rather than assuming this file overrides other instructions.

## Project — Waqfah

This section is project-specific and may change as the repository evolves.

Waqfah is a single-module Android app (Kotlin, Jetpack Compose, Material 3; Room + DataStore; Hilt) that shows a Quranic ayah over monitored apps when they open.

### Project resources

- Issue tracker: GitHub Issues (`ShrekBytes/waqfah`) via `gh` CLI. `docs/agents/issue-tracker.md`.
- Triage labels: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. `docs/agents/triage-labels.md`.
- Domain docs: `CONTEXT.md` + `docs/adr/` at repo root. `docs/agents/domain.md`.
- Architecture/codebase map: `docs/ARCHITECTURE.md`.
- Commit format: `<type>: <summary>`, e.g. `fix: atomically claim trigger stamps`.

### Build & verify

Two flavours build the two store channels — `play` (`dev.shrekbytes.waqfah`)
and `fdroid` (`dev.shrekbytes.waqfah.fdroid`) — so every variant task is
prefixed with one of them:

```bash
./gradlew :app:testPlayDebugUnitTest   # unit tests — no emulator needed
./gradlew :app:assemblePlayDebug       # compile check
```

- Headless-safe: the two commands above. CI (`.github/workflows/android-ci.yml`) additionally runs `:app:lintPlayDebug` and `:app:assemblePlayRelease` (proves the R8 release path compiles).
- `androidTest` (incl. Room migration tests) needs a device; CI runs them on an API 36 emulator (see the device rule below).
- **Need a device? Use the user's connected physical device — never an AVD.** If a task needs a device and none is attached, stop and ask the user to connect one rather than spinning up an emulator. Prefer the `fdroid` flavour for anything on-device (`:app:assembleFdroidDebug`, `:app:testFdroidDebugUnitTest`, `:app:installFdroidDebug`). The only exception is CI, which has no physical device and runs `androidTest` on its own emulator.
- Extra setup: Android SDK platform 37. Release signing reads `keystore.properties` (gitignored) or `WAQFAH_*` env vars; without it the release task still succeeds but emits an unsigned APK — CI's R8 check relies on that. That keystore is the Google Play key; F-Droid signs its own build.
- Toolchain: Gradle auto-provisions its daemon JDK (pinned in `gradle/gradle-daemon-jvm.properties`); dependency versions live in `gradle/libs.versions.toml`, fetched by the wrapper on first build.
- `namespace` and `applicationId` are both `dev.shrekbytes.waqfah` (the flavours add `.fdroid` to the ID only), but they are independent knobs. The Kotlin packages, generated `R`, Hilt wiring, the Room schema directory under `app/schemas/` — which is named after the fully-qualified database class — and `TriggerActivity::class.java.name` (which the interstitial-return rule matches on) all key off the namespace. A store-ID change does not require touching it; a namespace change does require moving that schema directory in step.

### Project-specific rules

- **User data must survive schema changes.** `data/local/appstate` is real user data (Room): every schema change gets a hand-written migration, never a destructive fallback. `data/local/core` (read-only Quran text) is rebuilt wholesale each release; destructive migration is fine there.
- **The active toggle governs detection only.** Home-tab reading runs entirely off Room + DataStore and never reads `appActive`; stopping the monitor service must never affect it (ADR-0002). Keep these decoupled.
- **Strings parity:** every user-visible string in `values/strings.xml` is mirrored in `values-bn/strings.xml`; `StringsParityTest` enforces this (`translatable="false"` opts out).
- **Docs never restate the current version.** A version written into `README.md`, `PRODUCT.md`, or a submission doc is wrong the moment it is bumped, and nothing flags it. Point at the store listing — the README's F-Droid badge reads the live version — and keep version numbers only where they describe a specific, frozen release: the `fdroiddata` `Builds:` block, a changelog filename, a recorded build output.
