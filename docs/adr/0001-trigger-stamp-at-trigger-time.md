# The trigger stamp is written at trigger time, never on dismissal

The cooldown anchor for a monitored app is persisted inside TriggerDecision the
moment its verdict fires — before the interstitial is shown — and dismissal
never touches it. Stamping on dismissal would leave the cooldown unset whenever
the user backs out instantly, so a quickly-dismissed app could re-trigger on
every resume; stamping at trigger time means one pause per cooldown window no
matter how the interstitial is later dismissed.

## Consequences

- A user who dismisses the interstitial instantly still consumes their app's
  cooldown window.
- Dismissal is a pure fall-through: it reveals the app underneath and records
  nothing.
