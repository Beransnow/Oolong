package oolong.client.ui.page.history

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import oolong.client.arch.MessageController
import oolong.client.model.HistoryEntry
import oolong.client.repository.HistoryRepository
import oolong.client.testing.MainDispatcherRule
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

class HistoryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = mockk<HistoryRepository>(relaxed = true)

    private fun forum(id: Long, time: Long) = HistoryEntry.Forum(id, time, "", "f$id", "f$id")

    @Test
    fun `groups visits by day and shows more on demand`() = runTest {
        val now = System.currentTimeMillis()
        every { repository.threads(any()) } returns flowOf(emptyList())
        every { repository.forums(100) } returns flowOf((1L..100L).map { forum(it, if (it == 1L) now else 0) })
        every { repository.forums(200) } returns flowOf((1L..150L).map { forum(it, 0) })
        val viewModel = HistoryViewModel(repository, mockk<MessageController>(relaxed = true))
        runCurrent()

        val first = viewModel.state.value.forums
        assertEquals(listOf(HistoryDay.Today, HistoryDay.Earlier), first.sections.map { it.day })
        assertEquals(listOf(1L), first.sections[0].entries.map { it.id })
        assertEquals(99, first.sections[1].entries.size)
        assertEquals(true, first.hasMore)

        viewModel.loadMore(HistoryTab.Forums)
        runCurrent()

        val second = viewModel.state.value.forums
        assertEquals(150, second.sections.single().entries.size)
        assertEquals(false, second.hasMore)
    }

    @Test
    fun `a visit before midnight is yesterday's`() {
        val zone = java.util.TimeZone.getTimeZone("Asia/Shanghai")
        // 2025-11-06 00:30 and 2025-11-05 23:30 in Shanghai, then two days before
        val now = 1_762_360_200_000L
        val sections = sectionsOf(listOf(forum(1, now), forum(2, now - 3_600_000), forum(3, now - 2 * 86_400_000)), now, zone)

        assertEquals(listOf(HistoryDay.Today, HistoryDay.Yesterday, HistoryDay.Earlier), sections.map { it.day })
    }
}
