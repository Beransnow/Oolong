package com.huanchengfly.tieba.post.arch.query

import com.huanchengfly.tieba.post.arch.LoadStatus
import com.huanchengfly.tieba.post.arch.Page
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class QueryClientTest {
    private data class Thread(val id: Long, val title: String = "t$id", val liked: Boolean = false)

    private data class Feed(val threads: List<Thread>)

    private val Threads = EntityType<Long, Thread>("thread") { it.id }

    private fun TestScope.client() = QueryClient(backgroundScope, testScheduler.timeSource)

    /** A query counting its requests, answering with [answer]. */
    private class Counted<K : Any, T : Any>(val answer: suspend (K) -> T) {
        var calls = 0
        val fetch: suspend (K) -> T = {
            calls++
            answer(it)
        }
    }

    /** Collects [flow] in the background; the list holds what it emitted last. */
    private fun <T> TestScope.collect(flow: Flow<T>): Pair<MutableList<T>, Job> {
        val values = mutableListOf<T>()
        val job = backgroundScope.launch { flow.collect { values += it } }
        return values to job
    }

    @Test
    fun `requests for the same key at once are sent once`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val counted = Counted<Long, String> {
            gate.await()
            "page $it"
        }
        val query = client().query("thread", fetch = counted.fetch)

        val first = async { query.get(1) }
        val second = async { query.get(1) }
        runCurrent()
        gate.complete(Unit)

        assertEquals("page 1", first.await())
        assertEquals("page 1", second.await())
        assertEquals(1, counted.calls)
    }

    @Test
    fun `a fresh result is used again and a stale one loads again`() = runTest {
        val counted = Counted<Long, String> { "page $it" }
        val query = client().query("thread", staleTime = 1.minutes, fetch = counted.fetch)

        query.get(1)
        advanceTimeBy(59.seconds)
        query.get(1)
        assertEquals(1, counted.calls)

        advanceTimeBy(2.seconds)
        query.get(1)
        assertEquals(2, counted.calls)
    }

    @Test
    fun `observing loads, and a failure keeps the last result`() = runTest {
        var fail = false
        val query = client().query<Long, String>("thread") { if (fail) throw IOException("network") else "page $it" }
        val (states, _) = collect(query.observe(1))
        runCurrent()
        assertEquals(QueryState("page 1"), states.last())

        fail = true
        query.refresh(1)
        runCurrent()
        assertEquals("page 1", states.last().data)
        assertIs<LoadStatus.Error>(states.last().status)
    }

    @Test
    fun `a result nothing observes is dropped after its gcTime`() = runTest {
        val query = client().query<Long, String>("thread", staleTime = 10.minutes, gcTime = 5.minutes) { "page $it" }
        val (_, job) = collect(query.observe(1))
        runCurrent()
        job.cancel()

        advanceTimeBy(4.minutes)
        assertEquals("page 1", query.peek(1))
        advanceTimeBy(2.minutes)
        assertNull(query.peek(1))
    }

    @Test
    fun `observing again before gcTime keeps the result`() = runTest {
        val query = client().query<Long, String>("thread", staleTime = 10.minutes, gcTime = 5.minutes) { "page $it" }
        val (_, job) = collect(query.observe(1))
        runCurrent()
        job.cancel()

        advanceTimeBy(4.minutes)
        collect(query.observe(1))
        advanceTimeBy(2.minutes)
        assertEquals("page 1", query.peek(1))
    }

    @Test
    fun `results show the latest version of the entities in them`() = runTest {
        val client = client()
        var title = "old"
        val feed = client.query<Unit, Feed>("feed", normalize = { feed -> feed.copy(threads = feed.threads.map { Threads(it) }) }) {
            Feed(listOf(Thread(1, title), Thread(2)))
        }
        val thread = client.query<Long, Thread>("thread", normalize = { Threads(it) }) { Thread(it, "new") }
        val (feeds, _) = collect(feed.observe(Unit))
        runCurrent()
        assertEquals("old", feeds.last().data!!.threads[0].title)

        thread.get(1)
        runCurrent()
        assertEquals(listOf("new", "t2"), feeds.last().data!!.threads.map { it.title })
        assertEquals("new", client.get(Threads, 1)?.title)
    }

    @Test
    fun `a mutation shows at once, outlives a reload and is kept when it succeeds`() = runTest {
        val client = client()
        val thread = client.query<Long, Thread>("thread", normalize = { Threads(it) }) { Thread(it) }
        thread.get(1)
        val response = CompletableDeferred<Unit>()

        val mutation = async { client.mutate({ update(Threads, 1L) { it.copy(liked = true) } }) { response.await() } }
        runCurrent()
        assertEquals(true, client.get(Threads, 1)?.liked)

        thread.refresh(1)
        runCurrent()
        assertEquals(true, client.get(Threads, 1)?.liked)

        response.complete(Unit)
        mutation.await()
        assertEquals(true, client.get(Threads, 1)?.liked)
    }

    @Test
    fun `a failed mutation is rolled back`() = runTest {
        val client = client()
        val thread = client.query<Long, Thread>("thread", normalize = { Threads(it) }) { Thread(it) }
        thread.get(1)

        assertFailsWith<IOException> {
            client.mutate({ update(Threads, 1L) { it.copy(liked = true) } }) { throw IOException("network") }
        }
        assertEquals(false, client.get(Threads, 1)?.liked)
    }

    @Test
    fun `a mutation runs on when its caller is cancelled`() = runTest {
        val client = client()
        client.query<Long, Thread>("thread", normalize = { Threads(it) }) { Thread(it) }.get(1)
        val response = CompletableDeferred<Unit>()
        val caller = CoroutineScope(coroutineContext + Job())

        caller.launch { client.mutate({ update(Threads, 1L) { it.copy(liked = true) } }) { response.await() } }
        runCurrent()
        caller.cancel()
        response.complete(Unit)
        runCurrent()

        assertEquals(true, client.get(Threads, 1)?.liked)
    }

    @Test
    fun `invalidating loads observed results again`() = runTest {
        val counted = Counted<Long, String> { "page $it" }
        val query = client().query("thread", staleTime = 10.minutes, fetch = counted.fetch)
        collect(query.observe(1))
        runCurrent()

        query.invalidate { it == 1L }
        runCurrent()
        assertEquals(2, counted.calls)
    }

    @Test
    fun `clearing drops everything and loads observed results again`() = runTest {
        val client = client()
        var account = "a"
        val query = client.query<Long, String>("thread", staleTime = 10.minutes, normalize = { it }) { "$account $it" }
        val (states, _) = collect(query.observe(1))
        runCurrent()

        account = "b"
        client.clear()
        runCurrent()
        assertEquals("b 1", states.last().data)
    }

    @Test
    fun `clearing during a request loads again for the new account`() = runTest {
        val client = client()
        val gate = CompletableDeferred<Unit>()
        var account = "a"
        val query = client.query<Long, String>("thread") { key ->
            val asked = account
            if (asked == "a") gate.await()
            "$asked $key"
        }
        val (states, _) = collect(query.observe(1))
        runCurrent()

        account = "b"
        client.clear()
        gate.complete(Unit)
        runCurrent()
        assertEquals("b 1", states.last().data)
    }

    @Test
    fun `a pager's items follow their entities`() = runTest {
        val client = client()
        val pagerScope = CoroutineScope(coroutineContext + Job())
        val pager = client.pager(
            scope = pagerScope,
            initialKey = 1,
            itemKey = Thread::id,
            normalize = { Threads(it) },
        ) { Page(listOf(Thread(1), Thread(2))) }
        runCurrent()

        client.update(Threads, 2L) { it.copy(liked = true) }
        runCurrent()
        assertEquals(listOf(false, true), pager.state.value.items.map { it.liked })

        pagerScope.cancel()
        runCurrent()
        assertNull(client.get(Threads, 1))
    }
}
