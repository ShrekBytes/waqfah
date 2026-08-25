package com.shrekbytes.waqfah.detection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.shrekbytes.waqfah.data.repository.PermissionsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

// Restarts the monitor after a reboot. Without this, detection silently stays
// off until the user happens to open Waqfah again — MainActivity.onResume is
// the only other start path. RECEIVE_BOOT_COMPLETED is a normal permission:
// no runtime prompt, no special Play declaration.
//
// Starting a foreground service from a BOOT_COMPLETED receiver is exempt from
// Android 12+ background-fgs-start restrictions. Still gated on usage access +
// overlay here: without them the service would stop itself on its first check,
// so there's no point launching into that.
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var permissionsRepository: PermissionsRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!permissionsRepository.hasRequiredPermissions()) {
            Log.i(TAG, "Usage access or overlay missing after boot — monitoring stays off until Waqfah is opened")
            return
        }
        Log.i(TAG, "Boot completed — restarting app monitor")
        AppMonitorService.start(context)
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}