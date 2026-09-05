# The active toggle governs detection only

The Settings on/off toggle (`appActive`) starts and stops the monitor — via
MonitorSupervisor — and nothing else. Home-tab reading never reads
`appActive`: it runs entirely off Room and DataStore, so stopping the monitor
must never affect an open reading session.

A user flipping the toggle expects the background watcher to stop, not their
reading session to vanish. Keeping the rule absolute also keeps reading code
unbreakable by detection changes, and detection unbreakable by reading changes.
