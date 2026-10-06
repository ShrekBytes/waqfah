# Vendor background-start restrictions are detected by capability, not by brand

Android exempts an app holding `SYSTEM_ALERT_WINDOW` from the background
activity start restriction, and Waqfah relies on that exemption to raise its
interstitial over a monitored app. Some vendor ROMs add a second, undocumented
gate on top of it: on MIUI and HyperOS the launch is still blocked until the
user grants a vendor permission that appears in Security Center as "Open new
windows while running in the background". Nothing in the AOSP API surface
reports that gate, and the app cannot grant it — `AppOpsManager.setMode` carries
`@RequiresPermission(MANAGE_APP_OPS_MODES)`, which is
`signature|installer|verifier|role` and therefore unavailable to an ordinary
application. So the only route is to tell the user the toggle exists and send
them to the screen that owns it.

That much is settled. What this ADR decides is how the app decides *whether to
tell them*, because the two obvious answers are both wrong.

The first rejected alternative is detecting the brand: `Build.MANUFACTURER` and
`Build.BRAND`, optionally backed by the `ro.miui.*` system properties. It reads
as reliable and is not. The maintainer's own test device reports
`MANUFACTURER=xiaomi` and `BRAND=POCO` while running a LineageOS-based ROM on
Android 15 — no MIUI, no HyperOS, no `com.miui.securitycenter` installed at all.
A brand check fires on that device, which means it would offer a vendor
permission for a vendor stack that is not present. The brand is a property of
the hardware, and the restriction is a property of the software, and on hardware
whose owner replaced the software the two come apart.

The second rejected alternative is reading the vendor AppOp to learn the
permission's state, so the row can show a tick and onboarding can gate on it.
That op is `10021`, it does not exist in AOSP — AOSP's own op for this behaviour
is the system-only `OP_SYSTEM_EXEMPT_FROM_ACTIVITY_BG_START_RESTRICTION` — and
AOSP's `AppOps.md` states that the integers are not part of the API and have
changed between platform versions and OEM implementations. A read of an
unstable integer can only fail in one of two directions, and both are bad: fail
open and the row claims a permission the user may not have, fail closed and a
user who has granted it is locked out of onboarding by a value the app is
guessing at. A fail-open read is worse than no read at all, because it is
indistinguishable in behaviour from not reading and still costs a reflective
call on every check — and reflection on hidden APIs is a member of a list that
moves between platform releases.

So the app asks a question it can actually answer: does the screen that owns
this setting exist on this device? The vendor packages are declared in the
manifest's `<queries>` block, and the row is offered only when resolving the
vendor settings intent succeeds. Package visibility filtering is why the
`<queries>` declaration is load-bearing rather than decorative — `startActivity`
is never filtered, but `resolveActivity` is, so without the declaration the
probe would silently answer "no" on every device. Capability detection needs no
brand, no system property, no reflection, and no vendor op, and it is correct in
exactly the case that defeats the brand check: on the LineageOS device the
intent resolves to nothing and no row is shown.

## The row this produces

The permission is genuinely required for the feature on the ROMs that impose it,
but the row cannot report its state, because nothing can read that state
reliably. That has three consequences for the interface, and they are the reason
this decision is worth recording rather than being left to the code.

The row never shows a tick. It is a pointer — "on this device, also enable X" —
that opens the vendor settings screen, and the user judges for themselves
whether the toggle is on. It is not a `PermissionToggleRow` and does not pretend
to be one.

The row does not gate onboarding's Continue button. The `Required` section means
"the rows gated behind Continue", so a row that cannot gate does not belong in
it, and a row that cannot report state cannot gate. Filing it under
`Recommended` would be worse in the other direction, because it would understate
that the feature is dead without it. It gets a section of its own, named for
what it is: needed on this device, not on every device.

The monitor gate is unchanged. `hasRequiredPermissions()` stays usage access and
overlay, because the vendor permission cannot be added to a gate without
reintroducing the unreadable value this ADR exists to avoid.

## Consequences

- The `<queries>` list is static and will rot as vendors rename components. It
  rots safely: a package that no longer resolves simply produces no row, rather
  than a row that cannot be satisfied.
- Because the row appears only when the vendor intent resolves, the failure mode
  where a user is sent to a settings page that does not contain the setting
  cannot occur. The row and a working deep-link are the same condition.
- Only the vendor stack confirmed on a real device is seeded. Other OEMs are
  documented to restrict background work, but for process killing rather than
  for blocking activity starts, which is a different symptom with a different
  remedy — the battery exemption row already covers it. An OEM gets a row when
  someone reports the symptom, not when a brand list says it might.
- The honest limitation is that on a ROM imposing the restriction, a user who
  ignores the row still gets an app that silently does nothing. Closing that gap
  needs a signal the app has not got yet: watching for its own `TriggerActivity`
  to resume after a launch and treating the absence of a self-resume as
  evidence. That is a separate change, and it is the only route to a row that
  could eventually show state — earned from observed behaviour rather than read
  from a vendor integer.
