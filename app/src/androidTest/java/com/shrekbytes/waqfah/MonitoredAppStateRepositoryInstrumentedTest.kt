package com.shrekbytes.waqfah

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shrekbytes.waqfah.data.local.appstate.WaqfahAppDatabase
import com.shrekbytes.waqfah.data.repository.MonitoredAppStateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        now = 2_000L
        state.recordTrigger("com.example.one")

        now = 3_000L
        state.add("com.example.one")

        assertEquals(2_000L, state.triggerStamp("com.example.one"))
    }

    @Test
    fun toggle_addsAndRemovesAtomically() = runBlocking {
        state.toggle("com.example.one")
        assertEquals(setOf("com.example.one"), state.monitoredPackages.first())

        state.toggle("com.example.one")
        assertEquals(emptySet<String>(), state.monitoredPackages.first())
        assertNull(state.triggerStamp("com.example.one"))
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
        state.recordTrigger("com.example.one")

        state.remove("com.example.one")

        assertNull(state.triggerStamp("com.example.one"))
    }
}
