package oolong.client.arch

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Runs user actions such as liking a post, at most one per key at a time: tapping again while
 * the first request is out does nothing. Actions with different keys run concurrently.
 *
 * Not thread-safe: call [launch] from the main thread, as view models do.
 */
class Actions(private val scope: CoroutineScope) {
    private val running = mutableMapOf<Any, Job>()

    fun launch(key: Any, block: suspend CoroutineScope.() -> Unit) {
        if (running[key]?.isActive == true) return
        val job = scope.launch(block = block)
        running[key] = job
        job.invokeOnCompletion { if (running[key] === job) running.remove(key) }
    }
}
