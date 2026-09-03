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

**Resumed activity**:
One observation of an app coming to the foreground, including which of its
screens showed.
_Avoid_: usage event

**Monitor gate**:
The conditions — screen on, Waqfah active, at least one monitored app — under
which detection runs at all.
_Avoid_: polling gate

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
