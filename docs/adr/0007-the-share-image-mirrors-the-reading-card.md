# The share image mirrors the reading card

Sharing an ayah produces an image of it: the reading card's content — the surah
name, the `ayah:surah` label, the Arabic, and whichever of pronunciation and
translation the reader has turned on — without the card's controls and without
the surah's ayah count, at a height that follows the content.

It reproduces the reader's palette and their content settings rather than a
fixed light, fully-populated card of Waqfah's own design. Two alternatives were
considered, and both were rejected for the same reason: they would make the
image disagree with the screen the reader was looking at when they tapped share.
A fixed light card overrides a palette the reader chose — a Midnight reader gets
a near-black card with lamplight-gold ink, and that is *their* reading, not an
accident to be corrected. A card that always carries Arabic, pronunciation and
translation overrides the aids the reader turned off, and worse, it silently
picks a language for them: an English pronunciation aid would appear on the
shared image of a reader whose aid is Bengali, or whose aid is none.

The mirror rule is what makes "the current ayah" unambiguous. The card renders
one ayah at a time, so the image never has to decide which of several visible
ayahs is being shared, and it never has to consult a scroll position. The same
rule settles the one genuinely ambiguous case: a reader who has tapped the
translation to compare a second one shares the translation on screen, not the
default they started from.

Dropping the surah's ayah count is not a concession to the mirror, it is a
correction of it. That line describes the collection the header opens, not the
ayah; in an image there is no header to open, and "286 ayat" beside a single
ayah says nothing about what is being shared.

The wordmark is the mirror's one deliberate exception — the only thing on the
image that is not on the card. It is there because the image travels: it will be
reposted without context, and an unattributed Quranic card is worse than a quiet
signature. It stays quiet, in `inkMuted` rather than `inkSoft`, because the
latter falls to roughly 2:1 contrast on the Dark and Midnight palettes and reads
as a smudge rather than as a signature.

The share image is not a reading surface. Nothing about it is stateful, no host
reads from it, and it produces nothing the reader can return to. It is a
rendering in the same family as the card's neighbouring-ayah peek pages: the
same content, drawn once, for a purpose outside the app.

## The page around the content

The mirror governs the content and the palette. It does not govern the page the
content sits on. The image is a designed page rather than a crop of the card: a
1dp `line` frame inset uniformly on all four sides, an accent four-point star on
the divider above the aids, and the wordmark. Two of the three — the frame and
the star — later gained a counterpart on the reading card: the saved-mark
borrows the page's language for a bookmarked ayah, drawn there only while the
shown ayah is in the collection. None of them changes a word of what is
mirrored, the mirror still runs one way — the image never carries the card's
state — and the wordmark remains the one thing that exists on the image alone.

Two of them earn their place by doing a job rather than by decorating. The frame
gives the image an edge of its own, so it does not depend on a chat bubble to
hold it together, and it makes the thing read as a page rather than as a
screenshot. The star replaces a 32dp hairline that was too quiet to register: it
marks the boundary between the scripture and the commentary, and it is what
makes the page's three voices scannable — Arabic, then pronunciation, then
meaning. It is drawn as a path rather than set as a glyph, so it cannot fall back
to tofu on a device without the character.

The frame's margin is uniform on all four sides, and that is the constraint the
wordmark had to be designed around. Sitting in the margin would have forced the
bottom inset wider than the other three; every other way of making room — a
narrower text column, a dedicated band below the frame — costs the page
something. So the wordmark is centred on the bottom rule, with the rule
interrupted behind it, which costs the layout nothing at all. The content is
padded equally above and below inside the frame, so it sits centred between the
rules; the signature then straddles the bottom rule and eats a few pixels of the
lower clearance, which is the one asymmetry in the design and is deliberate.

The wordmark is set in `inkSoft` at 55% alpha. The alpha is a deliberate
exception to the ink ladder's no-fading rule: that rule exists to stop derived
*functional* colours drifting across palettes, and a watermark is neither
functional nor read. `inkMuted` was tried and rejected — it is the same tone as
the translation and the pronunciation, so the signature competed with the text
it is supposed to sit behind.

The design is drawn out in full in `share-image-mockup.html` at the repository
root: every palette, a long ayah, the content-off cases, the Bengali case, and
the share control's slot on all three hosts. It is the visual reference for this
ADR. The prose here decides; the mockup shows.

## Consequences

- Pronunciation and translation are optional in the image exactly as they are on
  the card. An image of Arabic alone is a legitimate output, not a degraded one.
- The image is rendered at a width of its own rather than captured from the
  screen, so no size can be read from the on-screen card's actual measurements.
  Every size scales proportionally from the card's nominal width, including the
  reader's system font scale, so the image reads as a clean enlargement of what
  they were looking at.
- There is no height cap. A long ayah at a large text size produces a very tall
  image; completeness wins over a tidy aspect ratio, and cropping an ayah is
  never an option.
- There is no preview step: the share control opens the system share sheet
  directly, which shows its own thumbnail of the image. A preview was rejected
  as an extra tap for a smaller view of the same thing.
- Every reading host gains the share control, the interstitial included.
  Sharing from the pause screen lands in the share sheet, which is an indirect
  entry — choosing a monitored app there cannot earn a second trigger. The
  reader can hide the control (the Advanced clean-look settings), which hides
  the drawing, not the ability: the card's left-half long-press shares the
  same image from the same session.
- The image's height follows its content, so a longer ayah produces a taller
  frame. The frame, the star and the signature are positioned relative to the
  edges, so none of them moves when the content grows.
- The frame's margin is uniform on all four sides, and nothing is ever allowed
  to widen one of them. Anything needing room finds it inside the frame or on a
  rule.
- This is the first place in the app that fades ink with alpha. The exception
  covers the wordmark and the saved-mark's pen echo — both ornament, neither
  of them functional ink — and is not licence to reach for alpha elsewhere.
