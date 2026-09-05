# AGENTS.md

## General

### Communication

Talk to me in clear, plain language — avoid unnecessary jargon, explain
technical terms when used. Be direct; don't pad responses. Plain language, not
less detail — give thorough explanations when needed.

Applies to conversation only. Code, docs, commit messages, etc. follow normal
professional/technical conventions.

### Working rules
- Questions get answers, not implementations. What/why/how = information only, no edits/builds/issue changes.
- Stay on task. Keep changes minimal, focused on what was asked. Don't fix unrelated problems unless necessary — mention them instead.
- Use skills when they help. Most skills are installed globally. Check for a relevant installed skill before starting; use it only if appropiate, or work directly if none apply.
- Prefer the simplest solution. No abstraction, layers, dependencies, or future-proofing without concrete need. Follow existing codebase patterns even when they add structure.
- Tests match the change, never get weakened. Add/update tests when behavior changes or existing tests are relevant, nothing extra. Never skip, delete, or dumb down a test to pass; explain first if a test is genuinely wrong.
- Verify before finishing. Run relevant tests and build checks, don't claim success from reading code alone.
- Escalate after two failures. Same approach fails twice → stop, ask before a third attempt.
- Ask when ambiguous. Unclear scope, approach, or intent → ask before working.
- Commits: `type: summary`

## Project — Waqfah
### Agent skills
- Issue tracker: GitHub Issues (`ShrekBytes/waqfah`) via `gh` CLI. `docs/agents/issue-tracker.md`.
- Triage labels: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. `docs/agents/triage-labels.md`.
- Domain docs: `CONTEXT.md` + `docs/adr/` at repo root. `docs/agents/domain.md`.

### Build & verify
```bash
./gradlew :app:testDebugUnitTest   # after logic changes
./gradlew :app:assembleDebug       # compiles
```
No emulator/device attached. Never run instrumented/`connected` tests unless asked.

### Where to look

* `README.md` — the product story, permissions model, and translation-db spec.
* `CONTEXT.md` — the project's vocabulary; use these terms exactly.
* `docs/ARCHITECTURE.md` — the map of the code; code comments next to what
  they explain are the source of truth.
