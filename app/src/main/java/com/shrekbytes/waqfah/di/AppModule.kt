package com.shrekbytes.waqfah.di

import android.content.Context
import android.content.Intent
import com.shrekbytes.waqfah.data.installedapp.InstalledAppCatalog
import com.shrekbytes.waqfah.data.monitoredapp.MonitoredAppState
import com.shrekbytes.waqfah.data.repository.DefaultReadingPorts
import com.shrekbytes.waqfah.data.repository.MonitoredAppStateRepository
import com.shrekbytes.waqfah.data.repository.PackageManagerInstalledAppCatalog
import com.shrekbytes.waqfah.data.repository.PermissionsRepository
import com.shrekbytes.waqfah.data.repository.SettingsRepository
import com.shrekbytes.waqfah.detection.AppMonitorService
import com.shrekbytes.waqfah.detection.MonitorSupervisor
import com.shrekbytes.waqfah.ui.reading.ReadingPorts
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // Application-lifetime scope for app-singleton flows that must stay warm
    // (SettingsRepository.loadedPreferences). SupervisorJob so one failed
    // child doesn't kill the scope.
    @Provides
    @Singleton
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    @Named("wallClock")
    fun provideWallClock(): () -> Long = { System.currentTimeMillis() }

    @Provides
    @Singleton
    fun provideMonitoredAppState(repository: MonitoredAppStateRepository): MonitoredAppState = repository

    @Provides
    @Singleton
    fun provideInstalledAppCatalog(catalog: PackageManagerInstalledAppCatalog): InstalledAppCatalog = catalog

    // The supervisor is the one owner of the monitor's service lifetime; the
    // Android gestures — starting the foreground service, stopping it — are
    // this app's adapters for it, exactly as AppMonitorService wires
    // MonitorSession's probes.
    @Provides
    @Singleton
    fun provideMonitorSupervisor(
        @ApplicationContext context: Context,
        settingsRepository: SettingsRepository,
        permissionsRepository: PermissionsRepository,
    ): MonitorSupervisor = MonitorSupervisor(
        appActive = { settingsRepository.preferences.first().appActive },
        hasPermissions = { permissionsRepository.hasRequiredPermissions() },
        startMonitor = { AppMonitorService.start(context) },
        stopMonitor = { context.stopService(Intent(context, AppMonitorService::class.java)) },
    )

    // Monitored-app state and the installed-app catalog each have one explicit
    // adapter binding. Callers depend on the domain seam, not Room or Android.
    //
    // The reading machine's probes: same adapter story as the supervisor above
    // — the repositories own the facts, ReadingPorts is the machine-facing
    // interface, and this is the one place the two are wired together.
    @Provides
    @Singleton
    fun provideReadingPorts(ports: DefaultReadingPorts): ReadingPorts = ports
}
