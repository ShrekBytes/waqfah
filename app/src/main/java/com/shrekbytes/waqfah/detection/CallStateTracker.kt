package com.shrekbytes.waqfah.detection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Tracks whether the device currently has an active cellular/VoLTE/VoWiFi
// voice call. Built on the protected PHONE_STATE broadcast so no
// READ_PHONE_STATE runtime permission is needed — Android delivers that
// broadcast to any registered app.
//
// VoIP calls (WhatsApp, Messenger, …) do NOT fire PHONE_STATE, so they
// are detected separately by AppMonitorService.isCallSurface using the
// foreground activity class name. Both signals feed the trigger rule
// together.
class CallStateTracker(private val context: Context) {

    private val _inCall = MutableStateFlow(false)
    val inCall: StateFlow<Boolean> = _inCall.asStateFlow()

    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != TelephonyStates.ACTION_PHONE_STATE) return
            val state = intent.getStringExtra(TelephonyStates.EXTRA_STATE) ?: return
            // RINGING and OFFHOOK mean a call is happening (incoming or
            // active). IDLE means the call ended. Anything else is ignored
            // so we never flip the flag on an unrelated state value.
            val nowInCall = when (state) {
                TelephonyStates.STATE_RINGING,
                TelephonyStates.STATE_OFFHOOK -> true
                TelephonyStates.STATE_IDLE -> false
                else -> return
            }
            if (_inCall.value != nowInCall) {
                Log.d(TAG, "Cellular call state changed: inCall=$nowInCall")
                _inCall.value = nowInCall
            }
        }
    }

    fun register() {
        if (registered) return
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(TelephonyStates.ACTION_PHONE_STATE),
            // PHONE_STATE is a protected system broadcast; the RECEIVER_NOT_EXPORTED
            // flag simply tells the platform this receiver is for our own process,
            // it does not block the system from delivering the broadcast.
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        registered = true
    }

    fun unregister() {
        if (!registered) return
        runCatching { context.unregisterReceiver(receiver) }
        registered = false
    }

    companion object {
        private const val TAG = "CallStateTracker"
    }
}

// Kept in a tiny holder so the action/extra/state constants are named
// and grouped together. The PHONE_STATE action is the only one not
// exposed as a constant on android.content.Intent; using a string literal
// directly in the broadcast filter would be more error-prone than
// centralizing it here.
internal object TelephonyStates {
    // android.intent.action.PHONE_STATE — the platform's own broadcast action
    // for telephony state changes. Not exposed as an Intent constant in the
    // public SDK, so the string literal is used directly. Protected by the
    // platform: any app can register for it without READ_PHONE_STATE.
    const val ACTION_PHONE_STATE = "android.intent.action.PHONE_STATE"
    const val EXTRA_STATE = "state"
    const val STATE_RINGING = "RINGING"
    const val STATE_OFFHOOK = "OFFHOOK"
    const val STATE_IDLE = "IDLE"
}
