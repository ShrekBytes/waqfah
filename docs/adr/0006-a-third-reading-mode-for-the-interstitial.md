# A third reading mode, for the interstitial alone

`ReadingMode` gains `BOOKMARKS`. Only the interstitial follows it: when the mode
is `BOOKMARKS` the pause screen walks the bookmark collection in Quran order
instead of the mushaf. Home stays on the whole Quran whatever the chip says, and
so do the tour's practice card and the surah picker, which share Home's session.

This amends ADR-0005, which rejected a third `ReadingMode` value. That rejection
was right about the tab and wrong about the mode. ADR-0005 was answering "how
does a reader reach their collection", and the answer is the tab — always
present, with the save control on every card. It still is. But it argued from
`ReadingMode`'s *shape*: the enum answers one question, which verse a fresh
session opens on, while `next()`/`previous()` walk the mushaf by global id and
ignore the mode entirely. A bookmarks mode would therefore have to change what
stepping means, and that is a real cost — it is just not a reason to refuse, once
the change is confined to the one surface that wants it. ADR-0005 priced the cost
correctly and declined to pay it anywhere. This pays it in exactly one place.

The mechanism is a third `VerseSequence`. `ModeAwareVerseSequence` composes the
two existing siblings — `VerseSelection` and `BookmarkedAyahStepper` — and
latches which one it walks when a fresh session starts. Latching is what keeps
the settled promise that changing the chip lands on the next fresh session: the
walk already under way keeps the collection it began on, so `next()` can never
step from a mushaf id into the bookmark collection because the mode changed
mid-read. The mode selects a collection; the latch keeps that selection stable
for the walk it governs.

Confining it to the interstitial is what makes the fallback coherent. Home is the
Quran, and it already has the surah picker as its header affordance; a Home card
walking bookmarks would sit beside the tab that exists for that job and would
strand the picker. So `BOOKMARKS` is not a mushaf ordering at all, and
`VerseSelection.start` treats it as `SEQUENTIAL` — a fresh Home-side session
opens on the first unread ayah. One stored setting, one honest meaning per
surface: the chip says what the pause screen reads, and Home reads the Quran.

An empty collection shows the empty state rather than falling back to the mushaf,
because the collection *is* the content of a bookmarks walk — the same rule the
Bookmarks tab already follows. With nothing saved and the chip on `BOOKMARKS`,
the pause screen is the invitation to start a collection, not an ayah.

## Consequences

- The interstitial gets its own ViewModel. `ReadingViewModel` keeps the plain
  mushaf selection and serves the Home-side surfaces only; the pause screen's
  package-label plumbing moved with it.
- `isMushafWide` is no longer a static property of the interstitial's sequence.
  It reports the latched choice, so the completion popup and the
  progress-reset-relands rules follow the walk the session is actually on —
  which is the collection-scoped behaviour ADR-0005 already defined.
- The chip is one setting, so a reader cannot have Home on Random while the pause
  screen is on Bookmarks. Choosing `BOOKMARKS` moves Home's *starting verse* to
  the first unread one; it does not change what Home reads.
- The completion popup's "switch to the other mode" action is unreachable on a
  bookmarks walk — it is not mushaf-wide, so the popup never appears there.
