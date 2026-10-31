package com.huanchengfly.tieba.post.arch

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LoadableTest {
    @Test
    fun `starts loading and emits the result`() = runTest {
        val loadable = Loadable(backgroundScope) { "data" }
        assertEquals(LoadState.Loading, loadable.state.value)

        runCurrent()
        assertEquals(LoadState.Success("data"), loadable.state.value)
    }

    @Test
    fun `emits the error thrown by the load`() = runTest {
        val error = IOException("network")
        val loadable = Loadable<String>(backgroundScope) { throw error }

        runCurrent()
        assertEquals(LoadState.Error(error), loadable.state.value)
    }

    @Test
    fun `retry loads again after an error`() = runTest {
        var calls = 0
        val loadable = Loadable(backgroundScope) {
            if (++calls == 1) throw IOException("network") else "data"
        }
        runCurrent()
        assertIs<LoadState.Error>(loadable.state.value)

        loadable.retry()
        assertEquals(LoadState.Loading, loadable.state.value)

        runCurrent()
        assertEquals(LoadState.Success("data"), loadable.state.value)
        assertEquals(2, calls)
    }

    @Test
    fun `retry cancels the load in progress`() = runTest {
        var calls = 0
        var firstCancelled = false
        val loadable = Loadable(backgroundScope) {
            if (++calls == 1) {
                try {
                    awaitCancellation()
                } finally {
                    firstCancelled = true
                }
            }
            "second"
        }
        runCurrent()

        loadable.retry()
        runCurrent()
        assertTrue(firstCancelled)
        assertEquals(LoadState.Success("second"), loadable.state.value)
    }

    @Test
    fun `cancellation is not reported as an error`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val loadable = Loadable<String>(scope) { awaitCancellation() }
        runCurrent()

        scope.cancel()
        runCurrent()
        assertEquals(LoadState.Loading, loadable.state.value)
    }
}
