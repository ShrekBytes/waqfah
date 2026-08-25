package com.shrekbytes.waqfah.detection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.shrekbytes.waqfah.data.repository.PermissionsRepository
import com.shrekbytes.waqfah.data.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

// Restarts the monitor after a reboot — unless the user has Waqfah toggled off,
// in which case the service stays down exactly as they left it (SettingsViewModel
// owns the same rule for in-session toggles). Without this receiver, detection
// silently stays off until the user happens to open Waqfah again.
// RECEIVE_BOOT_COMPLETED is a normal permission: no runtime prompt, no special
// Play declaration.
//
// Starting a foreground service from a BOOT_COMPLETED receiver is exempt from
// Android 12+ background-fgs-start restrictions. Still gated on usage access +
// overlay here: without them the service would stop itself on its first check,
// so there's no point launching into that.
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var permissionsRepository: PermissionsRepository
    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // goAsync + a one-shot scope: reading DataStore needs a suspend context,
        // but blocking onReceive with runBlocking risks an ANR on a slow disk.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!permissionsRepository.hasRequiredPermissions()) {
                    Log.i(TAG, "Usage access or overlay missing after boot — monitoring stays off until Waqfah is opened")
                    return@launch
                }
                if (!settingsRepository.preferences.first().appActive) {
                    Log.i(TAG, "Waqfah is toggled off — monitor not restarted after boot")
                    return@launch
                }
                Log.i(TAG, "Boot completed — restarting app monitor")
                AppMonitorService.start(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}