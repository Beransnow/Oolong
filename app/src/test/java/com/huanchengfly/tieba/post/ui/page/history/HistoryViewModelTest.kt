package com.huanchengfly.tieba.post.ui.page.history

import com.huanchengfly.tieba.post.arch.MessageController
import com.huanchengfly.tieba.post.model.HistoryEntry
import com.huanchengfly.tieba.post.repository.HistoryRepository
import com.huanchengfly.tieba.post.testing.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

class HistoryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = mockk<HistoryRepository>(relaxed = true)

    private fun forum(id: Long, time: Long) = HistoryEntry.Forum(id, time, "", "f$id", "f$id")

    @Test
    fun `splits today's visits from earlier ones and shows more on demand`() = runTest {
        val now = System.currentTimeMillis()
        every { repository.threads(any()) } returns flowOf(emptyList())
        every { repository.forums(100) } returns flowOf((1L..100L).map { forum(it, if (it == 1L) now else 0) })
        every { repository.forums(200) } returns flowOf((1L..150L).map { forum(it, 0) })
        val viewModel = HistoryViewModel(repository, mockk<MessageController>(relaxed = true))
        runCurrent()

        val first = viewModel.state.value.forums
        assertEquals(listOf(1L), first.today.map { it.id })
        assertEquals(99, first.before.size)
        assertEquals(true, first.hasMore)

        viewModel.loadMore(HistoryTab.Forums)
        runCurrent()

        val second = viewModel.state.value.forums
        assertEquals(150, second.before.size)
        assertEquals(false, second.hasMore)
    }
}
