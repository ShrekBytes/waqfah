package com.shrekbytes.waqfah.detection

import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.shrekbytes.waqfah.TriggerActivity
import com.shrekbytes.waqfah.data.repository.MonitoredAppsRepository
import com.shrekbytes.waqfah.data.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class WaqfahAccessibilityService : AccessibilityService() {

    @Inject lateinit var monitoredAppsRepository: MonitoredAppsRepository
    @Inject lateinit var settingsRepository: SettingsRepository

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(
        serviceJob + CoroutineExceptionHandler { _, throwable ->
            // A SupervisorJob stops one failing child from cancelling its
            // siblings, but an uncaught exception still crashes the process
            // by default — for a background service that needs to keep
            // running reliably, that's much worse than losing one trigger.
            Log.e(TAG, "Unhandled error in accessibility service coroutine", throwable)
        },
    )

    // Dedups successive window-state events fired *within* the same app (many
    // apps trigger several as internal screens change), not a cooldown itself.
    private var lastHandledPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Connected. Initial eventTypes=${serviceInfo?.eventTypes}, packageNames=${serviceInfo?.packageNames?.toList()}")

        // Only ever dispatched for apps the user actually picked, kept in sync
        // as the Apps screen changes the selection.
        //
        // Building a complete AccessibilityServiceInfo from scratch here,
        // rather than mutating whatever `serviceInfo` already held — reusing
        // the existing object assumed eventTypes had already been correctly
        // populated from the XML config, which is the leading suspect for why
        // no events were ever arriving despite packageNames looking right in
        // the logs. (description isn't set here — it has no public setter;
        // it's read-only at runtime, tied to the XML's android:description.)
        serviceScope.launch {
            monitoredAppsRepository.monitoredApps.collectLatest { apps ->
                val packages = apps.map { app -> app.packageName }.toTypedArray()
                serviceInfo = AccessibilityServiceInfo().apply {
                    eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                    notificationTimeout = 100
                    packageNames = packages
                }
                Log.d(TAG, "Watching packages: ${packages.toList()}, eventTypes now ${serviceInfo?.eventTypes}")
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // Temporary/diagnostic: if this ever logs, events ARE reaching the
            // service but as an unexpected type — tells us the filter itself,
            // not delivery, is the problem.
            Log.v(TAG, "Ignoring event type ${event.eventType} for ${event.packageName}")
            return
        }
        val packageName = event.packageName?.toString() ?: return
        if (packageName == this.packageName) return // ignore Waqfah's own windows
        if (packageName == lastHandledPackage) {
            Log.d(TAG, "Skip $packageName: same as last handled (still foregrounded)")
            return
        }
        lastHandledPackage = packageName
        Log.d(TAG, "Window changed: $packageName")

        serviceScope.launch {
            val monitored = monitoredAppsRepository.monitoredApps.first()
            if (monitored.none { it.packageName == packageName }) {
                Log.d(TAG, "Skip $packageName: not in monitored list ${monitored.map { it.packageName }}")
                return@launch
            }

            val prefs = settingsRepository.preferences.first()
            if (!prefs.appActive) {
                Log.d(TAG, "Skip $packageName: Waqfah is paused (master switch off)")
                return@launch
            }
            if (monitoredAppsRepository.isInCooldown(packageName, prefs.cooldownMinutes)) {
                Log.d(TAG, "Skip $packageName: still in cooldown (${prefs.cooldownMinutes} min)")
                return@launch
            }

            Log.d(TAG, "Triggering reading screen for $packageName")
            monitoredAppsRepository.recordShown(packageName)
            launchReadingScreen(packageName)
        }
    }

    private fun launchReadingScreen(packageName: String) {
        val intent = Intent(this, TriggerActivity::class.java).apply {
            putExtra(TriggerActivity.EXTRA_TRIGGERED_PACKAGE, packageName)
            // Required since this call comes from a Service, not an Activity.
            // TriggerActivity is excludeFromRecents + its own taskAffinity
            // (manifest), so each trigger is a fresh, short-lived instance
            // that never clutters the system app-switcher — see
            // TriggerActivity's doc comment for the full reasoning.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            // ActivityNotFoundException here almost always means TriggerActivity
            // isn't correctly declared in AndroidManifest.xml (missing entirely,
            // or a typo in android:name) — see SETUP.md section 4.
            Log.e(TAG, "Failed to launch TriggerActivity for $packageName", e)
        }
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        serviceJob.cancel()
        return super.onUnbind(intent)
    }

    private companion object {
        const val TAG = "WaqfahAccessibility"
    }
}
