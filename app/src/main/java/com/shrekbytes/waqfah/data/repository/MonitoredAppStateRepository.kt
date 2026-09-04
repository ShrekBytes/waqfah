package com.shrekbytes.waqfah.data.repository

import androidx.room.withTransaction
import com.shrekbytes.waqfah.data.local.appstate.MonitoredAppEntity
import com.shrekbytes.waqfah.data.local.appstate.WaqfahAppDatabase
import com.shrekbytes.waqfah.data.monitoredapp.MonitoredAppState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class MonitoredAppStateRepository @Inject constructor(
    private val appDatabase: WaqfahAppDatabase,
    @Named("wallClock") private val nowWall: () -> Long,
) : MonitoredAppState {

    private val dao by lazy { appDatabase.monitoredAppDao() }

    override val monitoredPackages: Flow<Set<String>> =
        dao.observeAll().map { apps -> apps.map { it.packageName }.toSet() }

    // INSERT IGNORE preserves an existing app's addedAt and trigger stamp.
    // Adding an already monitored app is therefore idempotent.
    override suspend fun add(packageName: String) {
        dao.insertIfAbsent(MonitoredAppEntity(packageName, addedAt = nowWall()))
    }

    override suspend fun remove(packageName: String) = dao.remove(packageName)

    // The read-and-mutate sequence is kept inside Room's transaction seam so
    // two rapid user toggles cannot both observe the same membership.
    override suspend fun toggle(packageName: String) {
        appDatabase.withTransaction {
            if (dao.exists(packageName)) {
                dao.remove(packageName)
            } else {
                dao.insertIfAbsent(MonitoredAppEntity(packageName, addedAt = nowWall()))
            }
        }
    }

    override suspend fun triggerStamp(packageName: String): Long? = dao.getLastShown(packageName)

    override suspend fun recordTrigger(packageName: String) {
        dao.updateLastShown(packageName, nowWall())
    }
}
