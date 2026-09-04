package com.shrekbytes.waqfah

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shrekbytes.waqfah.data.local.appstate.WaqfahAppDatabase
import com.shrekbytes.waqfah.data.repository.MonitoredAppStateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

// Tests the Room adapter behind MonitoredAppState. The database is in memory,
// while the real schema and transaction behavior remain the same as production.
@RunWith(AndroidJUnit4::class)
class MonitoredAppStateRepositoryInstrumentedTest {

    private lateinit var database: WaqfahAppDatabase
    private var now = 1_000L
    private lateinit var state: MonitoredAppStateRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WaqfahAppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        state = MonitoredAppStateRepository(database) { now }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun monitoredPackages_exposesOnlyPackageIdentity() = runBlocking {
        state.add("com.example.one")
        state.add("com.example.two")

        assertEquals(
            setOf("com.example.one", "com.example.two"),
            state.monitoredPackages.first(),
        )
    }

    @Test
    fun add_isIdempotent_andPreservesTriggerStamp() = runBlocking {
        state.add("com.example.one")
        val membership = state.monitoredMembership("com.example.one")!!
        now = 2_000L
        assertTrue(state.claimTrigger(membership, now))

        now = 3_000L
        state.add("com.example.one")

        assertEquals(2_000L, state.monitoredMembership("com.example.one")?.triggerStamp)
    }

    @Test
    fun toggle_addsAndRemovesAtomically() = runBlocking {
        state.toggle("com.example.one")
        assertEquals(setOf("com.example.one"), state.monitoredPackages.first())

        state.toggle("com.example.one")
        assertEquals(emptySet<String>(), state.monitoredPackages.first())
        assertNull(state.monitoredMembership("com.example.one"))
    }

    @Test
    fun concurrentToggles_areSerializedByTheRoomTransaction() = runBlocking {
        coroutineScope {
            listOf(
                launch(Dispatchers.Default) { state.toggle("com.example.one") },
                launch(Dispatchers.Default) { state.toggle("com.example.one") },
            ).joinAll()
        }

        assertEquals(emptySet<String>(), state.monitoredPackages.first())
    }

    @Test
    fun remove_discardsTheTriggerStampWithTheSelection() = runBlocking {
        state.add("com.example.one")
        val membership = state.monitoredMembership("com.example.one")!!
        state.claimTrigger(membership, now)

        state.remove("com.example.one")

        assertNull(state.monitoredMembership("com.example.one"))
    }

    @Test
    fun claimTrigger_rejectsStaleMembershipAndRevision() = runBlocking {
        state.add("com.example.one")
        val first = state.monitoredMembership("com.example.one")!!

        assertTrue(state.claimTrigger(first, 2_000L))
        val second = state.monitoredMembership("com.example.one")!!
        assertTrue(state.claimTrigger(second, 3_000L))
        assertFalse(state.claimTrigger(first, 2_000L))
        assertEquals(3_000L, state.monitoredMembership("com.example.one")?.triggerStamp)
        assertEquals(2L, state.monitoredMembership("com.example.one")?.triggerRevision)
    }

    @Test
    fun concurrentClaims_onlyOneMembershipSnapshotWins() = runBlocking {
        state.add("com.example.one")
        val membership = state.monitoredMembership("com.example.one")!!

        val results = coroutineScope {
            listOf(
                async(Dispatchers.Default) { state.claimTrigger(membership, 2_000L) },
                async(Dispatchers.Default) { state.claimTrigger(membership, 2_000L) },
            ).map { it.await() }
        }

        // The transaction's UPDATE predicate includes the revision, so the
        // second same-timestamp claim cannot also affect the row.
        assertEquals(1, results.count { it })
        assertEquals(1, results.count { !it })
        assertEquals(1L, state.monitoredMembership("com.example.one")?.triggerRevision)
        assertEquals(2_000L, state.monitoredMembership("com.example.one")?.triggerStamp)
    }

    @Test
    fun removeAndReAdd_createsANewMembership() = runBlocking {
        state.add("com.example.one")
        val first = state.monitoredMembership("com.example.one")!!
        state.remove("com.example.one")
        state.add("com.example.one")
        val second = state.monitoredMembership("com.example.one")!!

        assertNotEquals(first.membershipId, second.membershipId)
        assertFalse(state.claimTrigger(first, 2_000L))
        assertNull(second.triggerStamp)
    }
}
