package com.shrekbytes.waqfah.data.monitoredapp

import kotlinx.coroutines.flow.Flow

// One current selection of a package as a monitored app. The membershipId
// distinguishes this selection from a later remove-and-re-add of the package;
// triggerRevision makes a trigger claim compare-and-set safe even when the wall
// clock returns the same value for two claims.
data class MonitoredAppMembership(
    val packageName: String,
    val membershipId: String,
    val triggerStamp: Long?,
    val triggerRevision: Long,
)

// The facts and mutations for the user's monitored-app state. The Room-backed
// implementation also owns trigger stamps, because a stamp is the cooldown
// anchor of one monitored-app membership.
interface MonitoredAppState {
    val monitoredPackages: Flow<Set<String>>

    suspend fun add(packageName: String)
    suspend fun remove(packageName: String)
    suspend fun toggle(packageName: String)
    suspend fun monitoredMembership(packageName: String): MonitoredAppMembership?
    suspend fun claimTrigger(membership: MonitoredAppMembership, triggeredAt: Long): Boolean
}
