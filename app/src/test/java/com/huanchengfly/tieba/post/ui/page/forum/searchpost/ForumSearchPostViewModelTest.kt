package com.huanchengfly.tieba.post.ui.page.forum.searchpost

import com.huanchengfly.tieba.post.arch.MessageController
import com.huanchengfly.tieba.post.arch.Page
import com.huanchengfly.tieba.post.model.ForumSearchOrder
import com.huanchengfly.tieba.post.repository.SearchRepository
import com.huanchengfly.tieba.post.testing.MainDispatcherRule
import com.huanchengfly.tieba.post.ui.navigation.ForumSearchPostKey
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
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
