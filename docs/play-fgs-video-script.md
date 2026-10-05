# Play FGS demo video — script

Shooting script for the video the Play Console demands alongside the
`FOREGROUND_SERVICE_SPECIAL_USE` declaration. See `play-submission.md` §4 for
the declaration text this video accompanies.

The video's job is narrow: **prove the foreground service is real and running
before the app opens, and prove the reading screen appears because of it.** It
is not a product demo. A reviewer who watches app UI and never sees the service
has no reason to approve the declaration.

Target length **~1:46**. One continuous recording — no montage.

---

## Configuration under test

| App           | Monitored | Role in the video            |
| ------------- | --------- | ---------------------------- |
| Calculator    | On        | First trigger (shot 5)       |
| FM Radio      | On        | Repeat trigger (shot 7)      |
| KWGT          | On        | Shown on the list (shot 3)   |
| 1.1.1 VPN     | **Off**   | Negative case (shot 6)       |

Calculator and FM Radio are used for the two positive triggers so the
**per-app cooldown never bites**. Do not open the same app twice.

---

## Pre-flight

Run this before you hit record. Every item here has ruined someone's take.

- **System language = English.** The app ships a Bengali locale; a Bengali UI
  gives the reviewer nothing to verify.
- **The build you uploaded.** Ideally the Play internal-testing build, so the
  video is literally the artifact under review.
- **Battery above 50%**, no low-battery toast on screen.
- **Do Not Disturb on**, and the notification shade clear of other apps'
  notifications. No personal messages on camera.
- **Portrait, rotation locked.**
- **Disable battery optimization for Waqfah.** On MIUI/ColorOS this is the
  difference between the service surviving and the interstitial never
  appearing. Set Waqfah to "No restrictions".
- **Cooldown state.** If you have been testing, a cooldown may already be
  running for Calculator. Wait it out, or start the take with FM Radio.
- **Screen recorder:** Android's built-in recorder (Quick Settings tile). It
  captures the notification shade, which is the point. Keep the status bar in
  frame.

---

## Read-through script

Read this top to bottom while recording. **Bracketed lines are actions, not
speech** — do not say them. `(beat)` means stop talking and let the screen be
seen.

Total speech is about 220 words, roughly 1:45 at a relaxed pace; the action
beats fill the rest. **Speak slower than feels natural** — you are explaining,
not presenting.

The captions listed under each shot further down are on-screen text for the
video, not lines to read. Don't say them aloud.

---

**[Screen: Waqfah Home tab. Don't touch anything yet.]**

This is Waqfah. It shows a Quran verse before the apps you choose.

*(beat)*

This video shows the foreground service that makes that possible.

**[Open the Permissions screen. Both rows should be green. If you are doing the revoke-and-regrant insert, do it here and stay quiet while you tap.]**

Waqfah needs two permissions, and both are granted from Android's own settings.

*(beat)*

Usage access lets it know which app is in the foreground. Display over other
apps lets the reading screen appear over the app that's opening.

**[Open the Apps tab. Show Calculator, FM Radio and KWGT switched on. Scroll so 1.1.1 VPN is visible and off.]**

Here are the apps I've chosen. Calculator, FM Radio and KWGT are monitored.

*(beat)*

1.1.1 VPN is left off. I'll come back to that.

**[Pull down the notification shade. Hold on the Waqfah notification. Do not dismiss it or scroll past it.]**

With detection on, Waqfah runs a continuous foreground service.

*(beat — let the notification be read)*

This is its notification, and it's already running now, before I've opened any
app.

*(beat — slow down for this one; it is the line the declaration rests on)*

It has to be running in advance, because it needs to be watching at the exact
moment an app opens.

**[Back to the launcher. Tap Calculator. Let the transition play at normal speed.]**

I'll open Calculator, which is monitored.

*(the reading screen appears — pause and let it be seen)*

The service detects the foreground change, and the reading screen appears
before the app.

**[Read it, or tap to skip. Land in Calculator.]**

I can read it, or tap to skip. And then I'm in Calculator, exactly where I left
off.

**[Back to the launcher. Tap 1.1.1 VPN.]**

Now 1.1.1 VPN, which I left unmonitored.

*(it opens straight through — pause)*

It opens straight through. Nothing from Waqfah.

**[Back to the launcher. Tap FM Radio.]**

And FM Radio, another monitored app.

*(the reading screen appears again)*

The reading screen appears again. The service never stopped running between
these opens, so detection is ready every time.

**[Pull the shade once more so the notification is the last thing on screen. Stop recording.]**

The service stays running in the background, so the next app open is covered
too.

---

## Shot list

### Shot 1 — 0:00–0:10 · What this is

**Screen.** Waqfah Home tab, reading card visible. Nothing else touched.

**Caption.** `Waqfah — a Quran verse before the apps you choose`

**Narration.**

> This is Waqfah. It shows a Quran verse before the apps you choose. This
> video shows the foreground service that makes that possible.

---

### Shot 2 — 0:10–0:28 · The two permissions

**Screen.** Permissions screen, both rows green.

**Optional, stronger.** Revoke and re-grant both on camera: tap Usage access →
toggle Waqfah off then on in the Android list → back → tap Display over other
apps → toggle off then on. Adds ~20 seconds and is the most convincing form of
this shot. Only do it if you are willing to reset your test state.

**Caption.** `Two permissions, granted in Android's own settings`

**Narration.**

> Waqfah needs two permissions, and both are granted from Android's own
> settings. Usage access lets it know which app is in the foreground. Display
> over other apps lets the reading screen appear over the app that is opening.

---

### Shot 3 — 0:28–0:42 · Which apps are monitored

**Screen.** Apps tab. Show Calculator, FM Radio and KWGT switched on. Scroll so
1.1.1 VPN is clearly visible and switched off.

**Caption.** `Calculator, FM Radio, KWGT monitored · 1.1.1 VPN left off`

**Narration.**

> Here are the apps I've chosen. Calculator, FM Radio and KWGT are monitored.
> 1.1.1 VPN is left off — I'll come back to that.

This one shot sets up both the positive case and the negative case. Do not cut
it.

---

### Shot 4 — 0:42–0:56 · The service is already running

**Screen.** Pull down the notification shade. Hold on the Waqfah monitor
notification. Do not dismiss it, do not scroll past it.

**Caption.** `The foreground service, running before any app is opened`

**Narration.**

> With detection on, Waqfah runs a continuous foreground service. This is its
> notification, and it's already running now — before I've opened any app. It
> has to be running in advance, because it needs to be watching at the exact
> moment an app opens.

**This is the shot that carries the declaration.** If you nail one shot, nail
this one. The shade must be open long enough to read.

---

### Shot 5 — 0:56–1:15 · Open Calculator

**Screen.** Back to the launcher. Tap Calculator. Let the app-open transition
play at **normal speed** — no speeding up. The reading screen appears over it.
Read it or tap to skip. You land in Calculator.

**Caption.** `Monitored app → the reading screen appears first`

**Narration.**

> I'll open Calculator, which is monitored. The service detects the foreground
> change and the reading screen appears before the app. I can read it, or tap
> to skip — and then I'm in Calculator, exactly where I left off.

---

### Shot 6 — 1:15–1:26 · Open 1.1.1 VPN

**Screen.** Back to the launcher. Tap 1.1.1 VPN. It opens straight through —
nothing from Waqfah appears.

**Caption.** `Unmonitored app → nothing appears`

**Narration.**

> Now 1.1.1 VPN, which I left unmonitored. It opens straight through — nothing
> from Waqfah.

This proves the service is doing selective work, not firing on everything. It
is the shot most people leave out, and it is worth the eleven seconds.

---

### Shot 7 — 1:26–1:40 · Open FM Radio

**Screen.** Back to the launcher. Tap FM Radio. The reading screen appears
again. Skip through.

**Caption.** `Detection stays ready — the service never stopped`

**Narration.**

> And FM Radio, another monitored app. The reading screen appears again. The
> service never stopped running between these opens, so detection is ready
> every time.

---

### Shot 8 — 1:40–1:46 · Close

**Screen.** Pull the shade once more so the still-present notification is the
last thing on screen. Stop recording.

**Narration.**

> The service stays running in the background, so the next app open is covered
> too.

---

## Upload

- **Unlisted YouTube**, not private.
- **Title.** `Waqfah — foreground service demonstration (specialUse)`
- **Description.** One line naming the `versionCode` this build is, and stating
  that the video demonstrates the `specialUse` foreground service for the Play
  foreground-service declaration.
- **Test the link in an incognito window before pasting it into the Console.**
  If it asks for a sign-in, the reviewer cannot watch it.

---

## If a shot fails

| Symptom                              | Cause                                        |
| ------------------------------------ | -------------------------------------------- |
| No interstitial on a monitored app   | Cooldown still running from earlier testing   |
| Interstitial appears late            | Battery optimization killing the service — set Waqfah to "No restrictions" |
| No notification in the shade         | `POST_NOTIFICATIONS` denied — grant it        |
| Nothing works at all                 | One of the two required permissions was revoked |
| Interstitial appears on 1.1.1 VPN    | That app is still switched on in the Apps tab |

Re-record the whole take rather than patching a single shot. A continuous
recording is more credible than an edited one.
