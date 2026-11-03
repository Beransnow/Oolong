package oolong.client.ui.page.forum.searchpost

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import oolong.client.arch.MessageController
import oolong.client.arch.Page
import oolong.client.model.ForumSearchOrder
import oolong.client.repository.SearchRepository
import oolong.client.testing.MainDispatcherRule
import oolong.client.ui.navigation.ForumSearchPostKey
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

class ForumSearchPostViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = mockk<SearchRepository>(relaxed = true) {
        every { forumHistory } returns flowOf(emptyList())
        coEvery { threadsInForum(any(), any(), any(), any(), any(), any()) } returns Page(emptyList())
    }

    private fun createViewModel() = ForumSearchPostViewModel(
        ForumSearchPostKey(forumName = "测试", forumId = 52L),
        repository,
        mockk<MessageController>(relaxed = true),
    )

    @Test
    fun `searches the forum and searches again when the filter changes`() = runTest {
        val viewModel = createViewModel()

        viewModel.search("  ")
        viewModel.search(" k ")
        runCurrent()
        viewModel.setOnlyThreads(true)
        runCurrent()

        assertEquals("k", viewModel.state.value.keyword)
        coVerify(exactly = 1) { repository.remember("k", "测试") }
        coVerify { repository.threadsInForum("k", "测试", 52, ForumSearchOrder.Newest, false, 1) }
        coVerify { repository.threadsInForum("k", "测试", 52, ForumSearchOrder.Newest, true, 1) }
        coVerify(exactly = 0) { repository.threadsInForum("", any(), any(), any(), any(), any()) }
    }
}
