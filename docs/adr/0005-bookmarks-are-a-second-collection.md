# Bookmarks are a second collection, not a third reading mode

**Amended by ADR-0006.** A third `ReadingMode` value exists now and the
interstitial follows it. Everything below about the *collection* still holds —
the tab, the second session, the empty state, the separate table. Only the
closing argument against a third mode is superseded, and only for the pause
screen.

The Bookmarks tab reads the bookmark collection through its own ReadingSession
instance, constructed with a collection-scoped stepper, rather than by adding a
`BOOKMARKED` value to `ReadingMode`. The tab is always present, with an
empty-state message instead of a feature flag; saving a verse is always
available.

A third `ReadingMode` value looks cheaper and is not. `ReadingMode` answers one
question — which verse a *fresh session* opens on — and is consulted in exactly
two places (`loadStartingVerse` and `switchModeAndRestart`). Stepping on both
existing modes walks the mushaf by global verse id and ignores the mode
entirely, so a bookmarks mode would have had to change what `next()`/`previous()`
mean, not what the mode enum holds. It would also have collided with the
completion popup's "switch to the other mode" action, which toggles
`SEQUENTIAL`/`RANDOM` and has no coherent third answer, and with the
everything-read fallbacks that keep the card from ever being empty — Home
cannot be empty, but a bookmark collection can.

Ordering is the real fault line. Home's session reads *the Quran*; the
bookmarks session reads *the user's collection*, and the two must hold
independent positions — sharing one session would teleport Home's card whenever
the user switched tabs. Same machine, different collection, two instances.

The always-on choice is a consequence of the same reasoning. A feature flag
hiding a third tab also hides the save control, which is the only way a user
would ever discover the collection exists; a flag that hides only the tab
leaves dead code and a setting nobody can evaluate. An empty collection is not
an error state — it is the invitation to start one.

## Consequences

- `ReadingSession` gains a collection-scoped construction path. Its existing
  mushaf-wide ordering and every read-status behaviour are unchanged; the
  interstitial and the Home tab keep using it exactly as before. This is the
  bulk of the feature's cost, and it lands in a heavily-tested class.
- Bookmark state is a shared observable fact: two sessions (Home's and the
  bookmarks tab's) plus the interstitial's must agree on whether a verse is
  saved, so the collection is published as one observable set rather than
  re-read per card.
- Bookmarks live in their own table under the appstate database, in their own
  repository. "Reset progress" wipes read history and must never reach them.
- The bookmark toggle is verse-keyed and reflects the database result, not an
  optimistic flip — the read-status pattern (`isMarkedRead` as a bare boolean
  on the shared state object) is deliberately not copied.
- The bookmarks card's header opens the bookmarks list where Home's opens the
  surah picker. The card is otherwise shared.
