package com.shrekbytes.waqfah.detection

// Owns the monitor's service lifetime: maps each external event — toggle, app
// resume, boot — to starting or stopping AppMonitorService. The one place the
// may-the-monitor-run rule exists: persisted appActive AND the required
// permissions (usage access + overlay). The toggle may start or stop; the
// start-only events exist so resuming the app or rebooting can never
// resurrect a monitor the user explicitly stopped. Everything impure arrives
// through the constructor as a function (the MonitorSession idiom), so the
// rule is testable on the JVM with fakes.
//
// Read-only by contract: the caller persists its preference change first,
// then syncs — the supervisor always reconciles against persisted truth.
class MonitorSupervisor(
    private val appActive: suspend () -> Boolean,
    private val hasPermissions: () -> Boolean,
    private val startMonitor: () -> Unit,
    private val stopMonitor: () -> Unit,
) {
    suspend fun sync(reason: Reason): Outcome {
        if (reason == Reason.TOGGLE && !appActive()) {
            // Stopping ignores the permission probes on purpose: the user
            // turned the monitor off, so it comes down either way.
            stopMonitor()
            return Outcome.Stopped
        }
        // Permissions report before the toggle — boot's diagnostics have
        // always named the missing probe first.
        if (!hasPermissions()) return Outcome.KeptDown(Outcome.BlockReason.MISSING_PERMISSIONS)
        if (!appActive()) return Outcome.KeptDown(Outcome.BlockReason.TOGGLED_OFF)
        startMonitor()
        return Outcome.Started
    }

    enum class Reason { TOGGLE, APP_RESUME, BOOT }

    sealed interface Outcome {
        data object Started : Outcome
        data object Stopped : Outcome
        data class KeptDown(val because: BlockReason) : Outcome

        enum class BlockReason { MISSING_PERMISSIONS, TOGGLED_OFF }
    }
}
