package com.shrekbytes.waqfah.detection

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.shrekbytes.waqfah.R
import com.shrekbytes.waqfah.TriggerActivity
import com.shrekbytes.waqfah.data.repository.MonitoredAppsRepository
import com.shrekbytes.waqfah.data.repository.PermissionsRepository
import com.shrekbytes.waqfah.data.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

// Watches foreground app changes by polling UsageStatsManager and launches
// TriggerActivity when a monitored app comes to the front. Runs as a specialUse
// foreground service; polling keeps battery cost proportional to how long
// Waqfah runs, not to how often the device switches apps.
@AndroidEntryPoint
class AppMonitorService : Service() {

    @Inject lateinit var monitoredAppsRepository: MonitoredAppsRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var permissionsRepository: PermissionsRepository

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(
        serviceJob + Dispatchers.Default + CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "Unhandled error in app monitor coroutine", throwable)
        },
    )

    // Dedups polls reporting the same still-foregrounded package.
    private var lastHandledPackage: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        serviceScope.launch { monitorLoop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startInForeground() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Background monitoring", NotificationManager.IMPORTANCE_MIN),
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.monitor_notification_title))
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Constant is compile-time inlined, so it's safe on API 28 despite
            // being added in Q; only the three-arg overload needs the guard.
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private suspend fun monitorLoop() {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        var windowStart = System.currentTimeMillis()

        while (serviceScope.isActive) {
            delay(POLL_INTERVAL_MS)

            if (!permissionsRepository.hasRequiredPermissions()) {
                Log.d(TAG, "Usage access or overlay permission revoked — stopping")
                stopSelf()
                break
            }

            val windowEnd = System.currentTimeMillis()
            val foregroundPackage = latestForegroundPackage(usageStatsManager, windowStart, windowEnd)
            windowStart = windowEnd

            if (foregroundPackage == null || foregroundPackage == lastHandledPackage) continue
            lastHandledPackage = foregroundPackage

            val monitored = monitoredAppsRepository.monitoredApps.first()
            if (monitored.none { it.packageName == foregroundPackage }) continue

            val prefs = settingsRepository.preferences.first()
            if (!prefs.appActive) continue
            if (monitoredAppsRepository.isInCooldown(foregroundPackage, prefs.cooldownMinutes)) continue

            Log.d(TAG, "Triggering reading screen for $foregroundPackage")
            monitoredAppsRepository.recordShown(foregroundPackage)
            launchReadingScreen(packageName = foregroundPackage)
        }
    }

    private fun latestForegroundPackage(manager: UsageStatsManager, from: Long, to: Long): String? {
        val events = manager.queryEvents(from, to)
        val event = UsageEvents.Event()
        var latest: String? = null
        var latestTimestamp = Long.MIN_VALUE
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED && event.timeStamp >= latestTimestamp) {
                latestTimestamp = event.timeStamp
                latest = event.packageName
            }
        }
        return latest?.takeUnless { it == packageName } // never trigger on Waqfah itself
    }

    private fun launchReadingScreen(packageName: String) {
        val intent = Intent(this, TriggerActivity::class.java).apply {
            putExtra(TriggerActivity.EXTRA_TRIGGERED_PACKAGE, packageName)
            // Required from a Service context; allowed in the background because
            // Waqfah holds SYSTEM_ALERT_WINDOW (see AndroidManifest).
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch TriggerActivity for $packageName", e)
        }
    }

    companion object {
        private const val TAG = "AppMonitorService"
        private const val CHANNEL_ID = "app_monitor"
        private const val NOTIFICATION_ID = 1
        private const val POLL_INTERVAL_MS = 1_000L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, AppMonitorService::class.java))
        }
    }
}
