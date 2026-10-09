# Waqfah

An Android app that watches which app is in the foreground and, when the user
opens a monitored app, interposes a Quranic reading pause — the interstitial —
before the app is used.

## Language

### Detection

**Trigger**:
The moment Waqfah launches the interstitial over a monitored app. The one
thing the trigger rules decide.
_Avoid_: fire, pause event

**Interstitial**:
The translucent reading pause (TriggerActivity) shown over the monitored app.
Finishing it falls through to the app underneath.
_Avoid_: overlay, dialog, reading screen

**Fresh open**:
A deliberate, user-initiated opening of a monitored app — the only thing that
can earn a trigger.
_Avoid_: real open, user-initiated open

**Indirect entry**:
An arrival at a monitored app through a picker or a worker activity (share
sheet, file viewer, link grabber). Never counts as a fresh open.
_Avoid_: passive entry, share target

**Switch-back**:
Returning to an app within the switch-back window of leaving it. The same
session, never a fresh open.
_Avoid_: quick return

**Call grace**:
The short window after a call ends during which the interrupted app and the
calling app's screens stay quiet, however many hops the return takes.
_Avoid_: post-call cooldown

**Cooldown**:
The user-set minimum gap between triggers for one app. `0` means Off: every
fresh open triggers.
_Avoid_: per-app interval, interval

**Monitored app**:
An app the user selected to be subject to triggers.
_Avoid_: target app, watched app

**Monitored-app state**:
The current set of monitored apps together with each app's trigger stamp.

**Monitored-app membership**:
One current selection of a package as a monitored app. Removing a package ends
that membership; selecting it again starts a new membership, even if its trigger
stamp is empty.

**Installed-app catalog**:
The launchable apps Waqfah offers for the user to choose as monitored apps.
_Avoid_: all installed packages

**Resumed activity**:
One observation of an app coming to the foreground, including which of its
screens showed.
_Avoid_: usage event

**Monitor gate**:
The conditions — screen on, Waqfah active, at least one monitored app — under which
detection runs at all.
_Avoid_: polling gate

**MonitorSession**:
The watching session that runs while detection is live — it holds the monitor gate open
and feeds every resumed activity to the trigger decision, ending when permissions are
revoked or Waqfah stops watching.
_Avoid_: poller, monitor loop, foreground watcher

**MonitorSupervisor**:
The module that owns the monitor's service lifetime: it maps each external event —
toggle, app resume, boot — to starting or stopping the monitor, from the persisted
toggle and the required permissions. Resume and boot may only start; only the toggle
may stop.
_Avoid_: monitor starter, service sync, lifecycle handler

**InterstitialSession**:
The interstitial's machine. It owns how the interstitial presents itself over the
monitored app and how it recovers when the monitored app covers it — the once-only
re-assert. The service and the activity are its Android adapters.
_Avoid_: retry logic, relaunch guard, interstitial helper

### The trigger decision

**TriggerDecision**:
The module that tracks the foreground and turns each resumed activity into a
verdict.

**Verdict**:
The outcome for one resumed activity: trigger, or ignore with a reason.
_Avoid_: decision result

**Trigger stamp**:
The persisted record of when an app last triggered — the cooldown's anchor.
Written once, at trigger time, never on dismissal.
_Avoid_: last shown, cooldown write

### Reading

**ReadingSession**:
The reading machine shared by all three hosts — the Home tab, the
Bookmarks tab and the interstitial. It steps between verses, renders the
current one, and marks verses read, owning its own ordering; the ViewModels
only adapt it to Android.
_Avoid_: reading engine, reader, reading manager

**ReadingPorts**:
The reading machine's on-demand probes — the verse, progress, and translation
facts ReadingSession fetches mid-step or mid-render, as distinct from the three
signals it subscribes to. The repositories are adapted to it by
DefaultReadingPorts; tests fake it inline.
_Avoid_: probe bundle, session callbacks

**Reading mode**:
Which collection a reading session walks. `SEQUENTIAL` and `RANDOM` are mushaf
orderings; `BOOKMARKS` walks the bookmark collection in Quran order and only the
interstitial follows it — Home is always the whole Quran, and the mushaf treats
`BOOKMARKS` as sequential (ADR-0006).
_Avoid_: reading order, mode

**Verse sequence**:
The verses a reading session walks: which verse a fresh session opens on, and
which verse comes next or previous, wrapping at the ends. Two implementations,
deliberately siblings — verse selection walks the whole mushaf, the
bookmarked-ayah stepper walks the collection — and the session is handed
whichever applies. The interstitial is handed the mode-aware sequence instead:
a composite of those two, which picks one when a fresh session starts and holds
it for that walk.
_Avoid_: verse walk, verse order

**Verse selection**:
The choice of which verse to show: the fresh-session start (sequential
first-unread, random random-unread, each with its everything-read fallback, and
`BOOKMARKS` falling in with sequential because it is not a mushaf ordering),
the in-surah continue (first-unread-in-surah, else the surah's first ayah),
and next/previous stepping with wrap-around.
_Avoid_: verse picker

**Pronunciation**:
The transliteration aid: the ayah rendered in Latin or Bengali script, shown
under the Arabic and set independently to English, Bengali or off. It has two
names on purpose — **pronunciation** is the user-facing word (the app's UI, the
README, store copy), **transliteration** is the developer-facing one
(`quran_core.db`'s `en_transliteration`/`bn_transliteration` columns,
`docs/quran-core-db.md`). Match the audience: pronunciation for anything a
reader sees, transliteration for schema and developer docs.
_Avoid_: mixing the two on one surface

**Bookmark**:
One verse the user saved to their own collection. It is a verse and nothing
else — no note, no label, no trigger context; the verse's text is never
affected by it.
_Avoid_: favourite, like, saved item

**Bookmark collection**:
The user's bookmarks, in Quran order. Not read progress: marking a verse read
never removes a bookmark, and wiping progress never empties the collection.
_Avoid_: favourites list, saved ayahs

**Bookmark toggle**:
Saving a verse to, or removing it from, the bookmark collection. The only
thing that changes the collection.
_Avoid_: favourite action, star

**Saved mark**:
The reading card's decoration of a bookmarked ayah — the accent star just above
the ayah reference and the pen stroke under the Arabic, borrowed from the share
image's page language — drawn while the shown ayah is in the collection. It
is the bookmark state's indicator even where the bookmark toggle is hidden,
and its change is the bookmark long-press's feedback. State is not content:
it is never mirrored into the share image.
_Avoid_: bookmark highlight, saved decoration

**Bookmarks tab**:
The Home tab's sibling holding the bookmarks card. Always present; when the
collection is empty it shows the empty-state message rather than hiding.
_Avoid_: favourites tab

**Bookmarks card**:
The bookmarks tab's reading surface — the same card as Home, over the
bookmark collection instead of the whole Quran. Its header is the one thing
that differs: a ribbon tile and the collection total in place of Home's ayah
count, so the two cards are told apart at a glance.
_Avoid_: favourites screen

**Bookmarked-ayah stepper**:
The collection's own stepping: next/previous move within the bookmarks, wrapping
inside them. Independent of the mushaf-wide stepping verse selection does, and of
the read/unread rules that applies. The bookmarks card always walks it; the
interstitial walks it when the reading mode is `BOOKMARKS`, which is a choice of
collection rather than a way of stepping.
_Avoid_: filtered selection, bookmark filter

**Bookmarks list**:
The bookmark collection as browsable rows — the surahs holding at least one
bookmarked verse, each expanding to its bookmarked verses. Opened from the
bookmarks card's header; a row jump retargets the card.
_Avoid_: bookmark picker, favourites list

**Bookmarks header**:
The bookmarks card's top pill. There it opens the bookmarks list; on Home the
same pill opens the surah picker. The interstitial draws the header too, as the
surah name and its ayah count with no pill — the pause screen has nowhere to
send the reader, so only the container is missing.
_Avoid_: bookmark go-to

### Sharing

**Share image**:
The ayah as it leaves Waqfah — the reading card's content without its controls
and without the surah's ayah count, at a height that follows its content. Not a
reading surface: nothing about it is stateful, and no host reads from it.
_Avoid_: share card, screenshot, export

**Share control**:
The entry point that produces a share image, and the only thing that does.
Present on every reading host. The Advanced clean-look settings can hide its
drawing on the reading card; the card's left-half long-press keeps sharing
reachable when they do.
_Avoid_: share button, share icon

### Feature tour

**Feature tour**:
The guided walkthrough introducing Waqfah and having the user practice on the
live reading card. It auto-shows over the Home tab on every launch until
finished once; skipping persists nothing. Started from FAQ & troubleshooting,
it shows over FAQ and the reader stays there.
_Avoid_: onboarding tour, tutorial

**TourSession**:
The tour's machine. It owns the current step, decides when a TryIt task is
done from the reading facts it is fed, and owns dismissal — finishing
persists completion, skipping persists nothing. The overlay and the
visibility gate are its adapters.
_Avoid_: tour state, tour logic

**TryIt step**:
A tour stop that asks the user to perform a real action on the live reading
card. It completes when the action happens — never by tapping through.
_Avoid_: practice step, exercise

**Sandbox**:
The real reading card (and, for go-to, the real surah/ayah picker) embedded
inside a TryIt step, so what the user practices is the actual thing.
_Avoid_: practice area, demo

**Task anchor**:
The reading-card snapshot a TryIt step compares against — taken when the step
becomes current, re-taken once loading resolves. The task is done when live
reading moves off the anchor.
_Avoid_: baseline, initial state

### Translations

**Translation library**:
The module that decides which translations are available for a language and
which one is active. The one place those two questions are answered.
_Avoid_: translation service, translation manager

**Available translation**:
A catalog entry usable right now: bundled (always, even before its file is
first copied), or downloaded to disk.
_Avoid_: downloaded translation, installed translation

**Stored translation**:
The translation the user last picked for a language, persisted in
preferences. May be unavailable: removed from the catalog, or its file
missing.
_Avoid_: saved translation, preferred translation

**Active translation**:
What a language actually renders: the stored translation when available,
otherwise the language's bundled one. Settings' active label and the reading
card never disagree about this.
_Avoid_: default translation, selected translation
