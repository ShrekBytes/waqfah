package com.shrekbytes.waqfah.detection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

// The boot event's adapter: hands ACTION_BOOT_COMPLETED to the
// MonitorSupervisor, which owns the may-the-monitor-run rule — the service
// comes back only when Waqfah is toggled on AND the required permissions are
// present; otherwise the receiver logs why it stayed down. Without this
// receiver, detection silently stays off until the user happens to open
// Waqfah again.
// RECEIVE_BOOT_COMPLETED is a normal permission: no runtime prompt, no special
// Play declaration.
//
// Starting a foreground service from a BOOT_COMPLETED receiver is exempt from
// Android 12+ background-fgs-start restrictions.
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var supervisor: MonitorSupervisor

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // goAsync + a one-shot scope: reading DataStore needs a suspend context,
        // but blocking onReceive with runBlocking risks an ANR on a slow disk.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (val outcome = supervisor.sync(MonitorSupervisor.Reason.BOOT)) {
                    is MonitorSupervisor.Outcome.Started ->
                        Log.i(TAG, "Boot completed — restarting app monitor")
                    is MonitorSupervisor.Outcome.Stopped ->
                        Unit // BOOT never stops the monitor
                    is MonitorSupervisor.Outcome.KeptDown -> when (outcome.because) {
                        MonitorSupervisor.Outcome.BlockReason.MISSING_PERMISSIONS ->
                            Log.i(TAG, "Usage access or overlay missing after boot — monitoring stays off until Waqfah is opened")
                        MonitorSupervisor.Outcome.BlockReason.TOGGLED_OFF ->
                            Log.i(TAG, "Waqfah is toggled off — monitor not restarted after boot")
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
