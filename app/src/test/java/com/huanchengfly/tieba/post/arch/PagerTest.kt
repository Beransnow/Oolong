package com.huanchengfly.tieba.post.arch

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PagerTest {
    private data class Item(val id: Int, val text: String = "item $id")

    /** Pages of three items keyed by page number, from page 1 to [lastPage]. */
    private fun numberedPages(lastPage: Int = 3): suspend (Int) -> Page<Int, Item> = { page ->
        Page(
            items = (1..3).map { Item((page - 1) * 3 + it) },
            next = (page + 1).takeIf { it <= lastPage },
            prev = (page - 1).takeIf { it >= 1 },
        )
    }

    private fun CoroutineScope.pager(
        initialKey: Int = 1,
        refreshMode: RefreshMode = RefreshMode.Replace,
        load: suspend (Int) -> Page<Int, Item> = numberedPages(),
    ) = Pager(this, initialKey, Item::id, refreshMode, load)

    private val Pager<Int, Item>.ids get() = state.value.items.map { it.id }

    @Test
    fun `loads the first page when created`() = runTest {
        val pager = backgroundScope.pager()
        assertEquals(LoadStatus.Loading, pager.state.value.refresh)

        runCurrent()
        assertEquals(listOf(1, 2, 3), pager.ids)
        assertEquals(LoadStatus.Idle, pager.state.value.refresh)
        assertTrue(pager.state.value.hasMore)
        assertFalse(pager.state.value.hasPrevious)
    }

    @Test
    fun `loadMore appends pages until there is no next key`() = runTest {
        val pager = backgroundScope.pager(load = numberedPages(lastPage = 2))
        runCurrent()

        pager.loadMore()
        assertEquals(LoadStatus.Loading, pager.state.value.append)
        runCurrent()
        assertEquals(listOf(1, 2, 3, 4, 5, 6), pager.ids)
        assertEquals(LoadStatus.Idle, pager.state.value.append)
        assertFalse(pager.state.value.hasMore)

        pager.loadMore()
        assertEquals(LoadStatus.Idle, pager.state.value.append)
    }

    @Test
    fun `loadPrevious prepends pages until there is no previous key`() = runTest {
        val pager = backgroundScope.pager(initialKey = 2)
        runCurrent()
        assertTrue(pager.state.value.hasPrevious)

        pager.loadPrevious()
        runCurrent()
        assertEquals(listOf(1, 2, 3, 4, 5, 6), pager.ids)
        assertFalse(pager.state.value.hasPrevious)
    }

    @Test
    fun `items already in the list are not added again`() = runTest {
        val pager = backgroundScope.pager { page ->
            when (page) {
                1 -> Page(listOf(Item(1), Item(2)), next = 2)
                else -> Page(listOf(Item(2), Item(3), Item(3)))
            }
        }
        runCurrent()

        pager.loadMore()
        runCurrent()
        assertEquals(listOf(1, 2, 3), pager.ids)
    }

    @Test
    fun `a failed loadMore keeps the items and is retried with the same key`() = runTest {
        val requested = mutableListOf<Int>()
        var failNext = true
        val pager = backgroundScope.pager { page ->
            requested += page
            if (page == 2 && failNext) {
                failNext = false
                throw IOException("network")
            }
            numberedPages()(page)
        }
        runCurrent()

        pager.loadMore()
        runCurrent()
        assertIs<LoadStatus.Error>(pager.state.value.append)
        assertEquals(listOf(1, 2, 3), pager.ids)

        pager.loadMore()
        runCurrent()
        assertEquals(LoadStatus.Idle, pager.state.value.append)
        assertEquals(listOf(1, 2, 3, 4, 5, 6), pager.ids)
        assertEquals(listOf(1, 2, 2), requested)
    }

    @Test
    fun `a failed loadPrevious is retried with the same key`() = runTest {
        var failNext = true
        val pager = backgroundScope.pager(initialKey = 2) { page ->
            if (page == 1 && failNext) {
                failNext = false
                throw IOException("network")
            }
            numberedPages()(page)
        }
        runCurrent()

        pager.loadPrevious()
        runCurrent()
        assertIs<LoadStatus.Error>(pager.state.value.prepend)
        assertEquals(listOf(4, 5, 6), pager.ids)

        pager.loadPrevious()
        runCurrent()
        assertEquals(LoadStatus.Idle, pager.state.value.prepend)
        assertEquals(listOf(1, 2, 3, 4, 5, 6), pager.ids)
    }

    @Test
    fun `a failed refresh keeps the items already shown`() = runTest {
        var fail = false
        val pager = backgroundScope.pager { page ->
            if (fail) throw IOException("network") else numberedPages()(page)
        }
        runCurrent()

        fail = true
        pager.refresh()
        runCurrent()
        assertIs<LoadStatus.Error>(pager.state.value.refresh)
        assertEquals(listOf(1, 2, 3), pager.ids)
    }

    @Test
    fun `loadMore does nothing while a page is loading`() = runTest {
        val requested = mutableListOf<Int>()
        val gate = CompletableDeferred<Unit>()
        val pager = backgroundScope.pager { page ->
            requested += page
            if (page == 2) gate.await()
            numberedPages()(page)
        }
        runCurrent()

        pager.loadMore()
        runCurrent()
        pager.loadMore()
        runCurrent()
        assertEquals(listOf(1, 2), requested)

        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf(1, 2, 3, 4, 5, 6), pager.ids)
    }

    @Test
    fun `refresh cancels a loadMore in progress`() = runTest {
        var pageTwoCancelled = false
        val pager = backgroundScope.pager { page ->
            if (page == 2) {
                try {
                    awaitCancellation()
                } finally {
                    pageTwoCancelled = true
                }
            }
            numberedPages()(page)
        }
        runCurrent()
        pager.loadMore()
        runCurrent()

        pager.refresh()
        runCurrent()
        assertTrue(pageTwoCancelled)
        assertEquals(LoadStatus.Idle, pager.state.value.append)
        assertEquals(listOf(1, 2, 3), pager.ids)
    }

    @Test
    fun `loads at either end wait for a refresh in progress`() = runTest {
        val requested = mutableListOf<Int>()
        val gate = CompletableDeferred<Unit>()
        val pager = backgroundScope.pager(initialKey = 2) { page ->
            requested += page
            if (requested.size == 2) gate.await()
            numberedPages()(page)
        }
        runCurrent()

        pager.refresh(from = 2)
        runCurrent()
        pager.loadMore()
        pager.loadPrevious()
        runCurrent()
        assertEquals(listOf(2, 2), requested)

        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf(4, 5, 6), pager.ids)
    }

    @Test
    fun `a cancelled load that returns anyway does not change the list`() = runTest {
        var swallowCancellation = true
        val returnAfterRefresh = CompletableDeferred<Unit>()
        val pager = backgroundScope.pager { page ->
            if (page == 2 && swallowCancellation) {
                swallowCancellation = false
                // Like a data source that wraps its call in runCatching
                runCatching { awaitCancellation() }
                withContext(NonCancellable) { returnAfterRefresh.await() }
                return@pager Page(listOf(Item(99)), next = 99)
            }
            numberedPages()(page)
        }
        runCurrent()
        pager.loadMore()
        runCurrent()

        pager.refresh()
        runCurrent()
        returnAfterRefresh.complete(Unit)
        runCurrent()
        assertEquals(listOf(1, 2, 3), pager.ids)

        pager.loadMore()
        runCurrent()
        assertEquals(listOf(1, 2, 3, 4, 5, 6), pager.ids)
    }

    @Test
    fun `refresh from another key jumps to that page`() = runTest {
        val pager = backgroundScope.pager()
        runCurrent()

        pager.refresh(from = 3)
        runCurrent()
        assertEquals(listOf(7, 8, 9), pager.ids)
        assertFalse(pager.state.value.hasMore)
        assertTrue(pager.state.value.hasPrevious)
    }

    @Test
    fun `loadMore from an explicit key loads past the end`() = runTest {
        val pager = backgroundScope.pager(load = numberedPages(lastPage = 1))
        runCurrent()
        assertFalse(pager.state.value.hasMore)

        pager.loadMore(from = 2)
        runCurrent()
        assertEquals(listOf(1, 2, 3, 4, 5, 6), pager.ids)
    }

    @Test
    fun `update changes the loaded items`() = runTest {
        val pager = backgroundScope.pager()
        runCurrent()

        pager.update { items -> items.filterNot { it.id == 2 } }
        assertEquals(listOf(1, 3), pager.ids)
    }

    @Test
    fun `a prepending refresh puts new items in front and replaces changed ones`() = runTest {
        var round = 0
        val pager = backgroundScope.pager(refreshMode = RefreshMode.Prepend) {
            round++
            if (round == 1) Page(listOf(Item(1), Item(2)), next = 2)
            else Page(listOf(Item(3), Item(1, "item 1 updated")), next = 2)
        }
        runCurrent()

        pager.refresh()
        runCurrent()
        assertEquals(listOf(3, 1, 2), pager.ids)
        assertEquals("item 1 updated", pager.state.value.items[1].text)
    }

    @Test
    fun `cancellation is not reported as an error`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val pager = scope.pager { awaitCancellation() }
        runCurrent()

        scope.cancel()
        runCurrent()
        assertEquals(LoadStatus.Loading, pager.state.value.refresh)
    }
}
