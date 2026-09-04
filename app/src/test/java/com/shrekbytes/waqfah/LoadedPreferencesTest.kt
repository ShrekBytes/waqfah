package com.shrekbytes.waqfah

import com.shrekbytes.waqfah.data.model.UserPreferences
import com.shrekbytes.waqfah.data.repository.loadedIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Pins the loadedPreferences seam's contract once, for every observer:
// null until the upstream's first value, the value after, updates carried
// through — "not yet loaded" is part of the type, so no caller invents its
// own sentinel or inherits one by coincidence.
@OptIn(ExperimentalCoroutinesApi::class)
class LoadedPreferencesTest {

    @Test
    fun `null until the upstream speaks, then values and updates`() = runTest {
        // Replay-less but buffered flow: nothing has been emitted yet, exactly
        // like DataStore before its first disk read, and tryEmit delivers
        // (a zero-buffer SharedFlow would silently drop it).
        val upstream = MutableSharedFlow<UserPreferences>(extraBufferCapacity = 8)
        val loaded = upstream.loadedIn(backgroundScope)
        runCurrent() // let the eager collector subscribe

        assertNull(loaded.value)

        val first = UserPreferences(appActive = false)
        upstream.tryEmit(first)
        runCurrent()
        assertEquals(first, loaded.value)

        val second = first.copy(cooldownMinutes = 5)
        upstream.tryEmit(second)
        runCurrent()
        assertEquals(second, loaded.value)
    }
}
