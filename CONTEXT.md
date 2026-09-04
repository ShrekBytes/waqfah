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
The translucent reading pause (TriggerActivity) shown over the target app.
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
The reading machine shared by both hosts — the Home tab and the
interstitial. It steps between verses, renders the current one, and marks
verses read, owning its own ordering; the ViewModel only adapts it to
Android.
_Avoid_: reading engine, reader, reading manager

**ReadingPorts**:
The reading machine's on-demand probes — the verse, progress, and translation
facts ReadingSession fetches mid-step or mid-render, as distinct from the three
signals it subscribes to. The repositories are adapted to it by
DefaultReadingPorts; tests fake it inline.
_Avoid_: probe bundle, session callbacks

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
