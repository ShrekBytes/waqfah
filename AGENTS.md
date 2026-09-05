# AGENTS.md

## General

### Communication

Talk to me in clear, plain language. Avoid unnecessary jargon. Explain technical terms when used. Be direct; don't pad responses. Plain language, not less detail. Give thorough explanations when needed.

Applies to conversation only. Code, docs, commit messages, etc. follow normal professional/technical conventions.

### Working rules

- Information requests get answers, not implementations. Do not make changes unless explicitly requested.
- Stay on task. Keep changes minimal and focused on what was asked. Don't fix unrelated problems unless necessary — mention them instead.
- Check installed skills when the task clearly matches one. Use the skill when it provides relevant procedures or tooling; otherwise work directly.
- Prefer the simplest solution. No abstraction, layers, dependencies, or future-proofing without concrete need. Follow existing codebase patterns even when they add structure.
- Tests match the change, never get weakened. Add/update tests when behavior changes or existing tests are relevant, nothing extra. Never skip, delete, or dumb down a test to pass; explain first if a test is genuinely wrong.
- Escalate after two failed attempts. Don't retry the same approach; change the diagnosis or approach, or ask before continuing.
- Verify before finishing. Run the narrowest relevant tests and build checks first, then broader checks when warranted. Don't claim success from reading code alone.
- Ask when scope, requirements, or intent are materially ambiguous. Resolve ordinary implementation choices by following existing patterns.
- Commits: `<type>: <summary>`, e.g. `fix: atomically claim trigger stamps`

### Before changing code

- Read the relevant code and nearby tests before editing.
- Follow existing patterns before introducing new ones.
- Check relevant domain/architecture docs when the change touches project concepts or boundaries.
- `docs/ARCHITECTURE.md` is the codebase map; read the section covering the area you're touching.

## Project — Waqfah

### Agent skills

- Issue tracker: GitHub Issues (`ShrekBytes/waqfah`) via `gh` CLI. `docs/agents/issue-tracker.md`.
- Triage labels: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. `docs/agents/triage-labels.md`.
- Domain docs: `CONTEXT.md` + `docs/adr/` at repo root. `docs/agents/domain.md`.

### Build & verify

```bash
./gradlew :app:testDebugUnitTest   # unit tests — no emulator needed
./gradlew :app:assembleDebug       # compile check
```

- `androidTest` (incl. Room migration tests) needs a device/emulator — headless verification is the two commands above.
- Gradle auto-provisions its daemon JDK (pinned in `gradle/gradle-daemon-jvm.properties`); needs Android SDK platform 37. `local.properties` is gitignored machine setup — never commit a fix.
