package com.shrekbytes.waqfah.data.repository

import androidx.room.withTransaction
import com.shrekbytes.waqfah.data.local.appstate.MonitoredAppEntity
import com.shrekbytes.waqfah.data.local.appstate.WaqfahAppDatabase
import com.shrekbytes.waqfah.data.monitoredapp.MonitoredAppMembership
import com.shrekbytes.waqfah.data.monitoredapp.MonitoredAppState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import java.util.UUID

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
        dao.insertIfAbsent(newMembership(packageName))
    }

    override suspend fun remove(packageName: String) = dao.remove(packageName)

    // The read-and-mutate sequence is kept inside Room's transaction seam so
    // two rapid user toggles cannot both observe the same membership.
    override suspend fun toggle(packageName: String) {
        appDatabase.withTransaction {
            if (dao.exists(packageName)) {
                dao.remove(packageName)
            } else {
                dao.insertIfAbsent(newMembership(packageName))
            }
        }
    }

    private fun newMembership(packageName: String) = MonitoredAppEntity(
        packageName = packageName,
        membershipId = UUID.randomUUID().toString(),
        addedAt = nowWall(),
    )

    override suspend fun monitoredMembership(packageName: String): MonitoredAppMembership? =
        dao.get(packageName)?.let {
            MonitoredAppMembership(
                packageName = it.packageName,
                membershipId = it.membershipId,
                triggerStamp = it.lastShownAt,
                triggerRevision = it.triggerRevision,
            )
        }

    override suspend fun claimTrigger(membership: MonitoredAppMembership, triggeredAt: Long): Boolean =
        dao.claimTrigger(
            packageName = membership.packageName,
            membershipId = membership.membershipId,
            expectedStamp = membership.triggerStamp,
            expectedRevision = membership.triggerRevision,
            triggeredAt = triggeredAt,
        ) == 1
}
