# A skipped tour persists nothing

Finishing the feature tour persists `hasCompletedFeatureTour = true`, and it
never auto-shows again. Skipping persists nothing — there is no "skipped" flag
— so the tour re-offers on every future launch until finished once; within the
running session, a session-local flag keeps it quiet. The re-offer is the
point, so don't "fix" this by persisting a skip flag.
