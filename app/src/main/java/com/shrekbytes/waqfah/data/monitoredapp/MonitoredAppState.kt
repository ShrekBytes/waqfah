package com.shrekbytes.waqfah.data.monitoredapp

import kotlinx.coroutines.flow.Flow

// The facts and mutations for the user's monitored-app state. The Room-backed
// implementation also owns trigger stamps, because a stamp is the cooldown
// anchor of one monitored app.
interface MonitoredAppState {
    val monitoredPackages: Flow<Set<String>>

    suspend fun add(packageName: String)
    suspend fun remove(packageName: String)
    suspend fun toggle(packageName: String)
    suspend fun triggerStamp(packageName: String): Long?
    suspend fun recordTrigger(packageName: String)
}
