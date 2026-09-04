package com.shrekbytes.waqfah.di

import android.content.Context
import android.content.Intent
import com.shrekbytes.waqfah.data.repository.PermissionsRepository
import com.shrekbytes.waqfah.data.repository.SettingsRepository
import com.shrekbytes.waqfah.detection.AppMonitorService
import com.shrekbytes.waqfah.detection.MonitorSupervisor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

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
}
