# The interstitial's identity is the TriggerActivity class name

Returning from the interstitial is recognized by matching the previous resumed
activity against Waqfah's own package plus the TriggerActivity class name —
supplied to TriggerDecision as a constructor probe, wired by AppMonitorService,
so the decision imports no Activity. The class match is what separates
"returned from the interstitial" from "opened after using Waqfah itself":
finishing the interstitial resumes the app underneath with an event identical
to a fresh open, and only the class distinguishes them.
