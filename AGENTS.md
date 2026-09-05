# AGENTS.md


## General

### Communication

Talk to me in clear, plain language — avoid unnecessary jargon, explain
technical terms when used. Be direct; don't pad responses. Plain language, not
less detail — give thorough explanations when needed.

Applies to conversation only. Code, docs, commit messages, etc. follow normal
professional/technical conventions.

### Working rules

- **Questions get answers, not implementations.** If a message asks what, why,
  or how, respond with information only — no edits, no builds, no issue
  changes. Implement only when asked to change something, even if the request
  is phrased as a question ("can you fix X?" is a request).
- **Stay on task.** Keep changes minimal and focused on what was asked. Fix
  unrelated problems only when asked; if you notice something, say so instead
  of fixing it.
- **Use the installed skills.** When a task matches one, invoke it rather than
  improvising: implementing → `/implement`, hunting a bug → `/diagnosing-bugs`,
  tests first → `/tdd`, reviewing code → `/code-review`. In an environment
  with no skills, do the work directly.
- **Simplest thing that works.** No abstraction, layers, or "future-proofing"
  without a concrete need. Follow the codebase's existing patterns even when
  they add structure — consistency beats local simplicity.
- **Ask before adding dependencies.** Don't pull in a library, tool, or plugin
  to solve a small problem; propose it first and prefer what the project
  already uses.
- **Add or update tests for the code you change**, even if nobody asked.
- **Verify before finishing.** Run the project's tests and a build before
  claiming anything works — never report success from reading code alone. If
  the commands aren't obvious, find them in the README or CI config rather
  than guessing or skipping.
- **Never weaken a failing test.** Don't skip, delete, or dumb down a test to
  get the suite green. Fix the code; if the test itself is wrong, say why and
  ask.
- **Escalate after two failures.** If the same fix fails twice, stop and ask —
  don't keep trying variations.
- **Commits**: `type: summary` — lowercase, specific, with a dash clause when
  it earns its place (`refactor: extract sync loop — the worker owns its
  retries`).

## Project — Waqfah

### Agent skills

#### Issue tracker

Issues live in GitHub Issues for this repo (`ShrekBytes/waqfah`), managed via
the `gh` CLI. See `docs/agents/issue-tracker.md`.

#### Triage labels

Default five-role triage vocabulary (`needs-triage`, `needs-info`,
`ready-for-agent`, `ready-for-human`, `wontfix`). See
`docs/agents/triage-labels.md`.

#### Domain docs

Single-context layout: `CONTEXT.md` + `docs/adr/` at the repo root. See
`docs/agents/domain.md`.

### Build & verify

```bash
./gradlew :app:testDebugUnitTest   # JVM unit tests — run after logic changes
./gradlew :app:assembleDebug       # proves the app compiles
```

No emulator or device is attached to this environment — never attempt
instrumented tests or `connected` checks unless explicitly asked.

### Where to look

- `README.md` — the product story, permissions model, and translation-db spec.
- `CONTEXT.md` — the project's vocabulary; use these terms exactly.
- `docs/ARCHITECTURE.md` — the map of the code; the doc comments next to what
  they explain are the source of truth.
