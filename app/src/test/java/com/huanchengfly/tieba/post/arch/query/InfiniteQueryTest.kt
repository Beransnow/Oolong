package com.huanchengfly.tieba.post.arch.query

import com.huanchengfly.tieba.post.arch.LoadStatus
import com.huanchengfly.tieba.post.arch.Page
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class InfiniteQueryTest {
    private data class Item(val id: Int, val liked: Boolean = false)

    /** A response: the page of items and what else the page shows. */
    private data class Response(val items: Page<Int, Item>, val title: String)

    private val Items = EntityType<Int, Item>("item", id = { it.id })

    private var calls = 0
    private var version = 1

    /** Pages of two items, from page 1 to 3, titled with the version the server is at. */
    private val fetch: suspend (String, Int) -> Response = { _, page ->
        calls++
        Response(Page(listOf(Item(page * 10 + 1), Item(page * 10 + 2)), next = (page + 1).takeIf { it <= 3 }), "v$version")
    }

    private fun TestScope.query(client: QueryClient = QueryClient(backgroundScope, testScheduler.timeSource)) =
        client.infiniteQuery<String, Int, Response, Item>(
            name = "list",
            itemKey = Item::id,
            page = { it.items },
            staleTime = 1.minutes,
            gcTime = 5.minutes,
            normalize = { Items(it) },
            fetch = fetch,
        )

    private fun TestScope.page() = CoroutineScope(coroutineContext + Job())

    private val QueryPager<Int, Item, Response>.ids get() = state.value.items.map { it.id }

    @Test
    fun `everything showing a list shares it, and a fresh one doesn't load again`() = runTest {
        val query = query()
        val first = query.pager(page(), "k", 1)
        runCurrent()
        first.loadMore()
        runCurrent()

        val second = query.pager(page(), "k", 1)
        runCurrent()
        assertEquals(listOf(11, 12, 21, 22), second.ids)
        assertEquals(2, calls)
    }

    @Test
    fun `a stale list shows what it has while it refreshes`() = runTest {
        val query = query()
        val first = page()
        val list = query.pager(first, "k", 1)
        runCurrent()
        list.loadMore()
        runCurrent()
        first.cancel()

        advanceTimeBy(2.minutes)
        version = 2
        val shown = query.pager(page(), "k", 1)
        assertEquals(listOf(11, 12, 21, 22), shown.ids)
        assertEquals(LoadStatus.Loading, shown.state.value.refresh)

        runCurrent()
        assertEquals(listOf(11, 12), shown.ids)
        assertEquals("v2", shown.loads.value.latest?.response?.title)
    }

    @Test
    fun `loads tell the refresh and the latest page apart`() = runTest {
        val list = query().pager(page(), "k", 1)
        runCurrent()
        assertEquals(Loads(Loaded(1, fetch("k", 1), true), Loaded(1, fetch("k", 1), true)), list.loads.value)

        list.loadMore()
        runCurrent()
        assertEquals(1, list.loads.value.refreshed?.cursor)
        assertEquals(Loaded(2, fetch("k", 2), false), list.loads.value.latest)
    }

    @Test
    fun `a list nothing shows is dropped after its gcTime`() = runTest {
        val query = query()
        val shown = page()
        query.pager(shown, "k", 1)
        runCurrent()
        shown.cancel()

        advanceTimeBy(6.minutes)
        query.pager(page(), "k", 1)
        runCurrent()
        assertEquals(2, calls)
    }

    @Test
    fun `a list prefetched opens without loading again`() = runTest {
        val query = query()
        query.prefetch("k", 1)
        runCurrent()

        val list = query.pager(page(), "k", 1)
        runCurrent()
        assertEquals(listOf(11, 12), list.ids)
        assertEquals(1, calls)
    }

    @Test
    fun `lists from different first cursors are kept apart`() = runTest {
        val query = query()
        query.pager(page(), "k", 1)
        val fromTwo = query.pager(page(), "k", 2)
        runCurrent()

        assertEquals(listOf(21, 22), fromTwo.ids)
    }

    @Test
    fun `a list's items follow their entities`() = runTest {
        val client = QueryClient(backgroundScope, testScheduler.timeSource)
        val list = query(client).pager(page(), "k", 1)
        runCurrent()

        client.update(Items, 12) { it.copy(liked = true) }
        runCurrent()
        assertEquals(listOf(false, true), list.state.value.items.map { it.liked })
    }

    @Test
    fun `invalidating leaves a shown list as it is until it is shown again`() = runTest {
        val query = query()
        val shown = page()
        query.pager(shown, "k", 1)
        runCurrent()

        query.invalidate { it == "k" }
        runCurrent()
        assertEquals(1, calls)

        shown.cancel()
        query.pager(page(), "k", 1)
        runCurrent()
        assertEquals(2, calls)
    }

    @Test
    fun `invalidating drops a list nothing shows`() = runTest {
        val query = query()
        query.prefetch("k", 1)
        runCurrent()

        query.invalidate()
        advanceTimeBy(1.seconds)
        query.pager(page(), "k", 1)
        runCurrent()
        assertEquals(2, calls)
    }

    @Test
    fun `clearing loads shown lists again`() = runTest {
        val client = QueryClient(backgroundScope, testScheduler.timeSource)
        val list = query(client).pager(page(), "k", 1)
        runCurrent()

        version = 2
        client.clear()
        runCurrent()
        assertEquals("v2", list.loads.value.latest?.response?.title)
        assertNull(client.get(Items, 99))
    }
}
