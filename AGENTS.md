# AGENTS.md

## General

### Communication

Talk to me in clear, plain language — avoid unnecessary jargon, explain
technical terms when used. Be direct; don't pad responses. Plain language, not
less detail — give thorough explanations when needed.

Applies to conversation only. Code, docs, commit messages, etc. follow normal
professional/technical conventions.

### Working rules

**Questions get answers, not implementations.** If I'm asking what, why, or
how, respond with information only — no edits, builds, or issue changes.
Implement when I'm asking you to change something, even if the request is
phrased as a question ("can you fix X?" is a request).

**Stay on task.** Keep changes minimal and focused on what was asked. Don't fix
unrelated problems unless they are necessary for the requested change; mention
them instead.

**Use skills when they help.** Use an installed skill when it is genuinely
useful for the task. Choose the lightest applicable workflow; don't load a
heavy implementation, testing, or review workflow for a small change that
doesn't need it. If no useful skill applies, work directly.

**Prefer the simplest solution.** Don't add abstraction, layers, dependencies,
or "future-proofing" without a concrete need. Follow the codebase's existing
patterns even when they add structure — consistency beats local simplicity.

**Tests should match the change.** Add or update tests when the change affects
behavior or existing tests are relevant. Don't create unnecessary tests for
changes that don't need them.

**Verify before finishing.** Run the relevant tests and build checks for the
change. Don't claim success from reading code alone. When the right commands
aren't obvious, find them in the README or CI config rather than guessing.

**Never weaken a failing test.** Don't skip, delete, or dumb down a test to make
the suite pass. If the test is genuinely wrong, explain why before changing
its expected behavior.

**Escalate after two failures.** If the same approach fails twice, stop and
reassess rather than repeating minor variations without new evidence.

**Commits:** `type: summary` — lowercase and specific, with a dash clause when
it earns its place (`refactor: extract sync loop — the worker owns its retries`).

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

No emulator or device is attached to this environment. Never attempt
instrumented tests or `connected` checks unless explicitly asked.

### Where to look

* `README.md` — the product story, permissions model, and translation-db spec.
* `CONTEXT.md` — the project's vocabulary; use these terms exactly.
* `docs/ARCHITECTURE.md` — the map of the code; code comments next to what
  they explain are the source of truth.
