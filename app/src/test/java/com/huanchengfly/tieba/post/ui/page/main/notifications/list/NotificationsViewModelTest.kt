package com.huanchengfly.tieba.post.ui.page.main.notifications.list

import com.huanchengfly.tieba.post.arch.Page
import com.huanchengfly.tieba.post.model.Notification
import com.huanchengfly.tieba.post.model.NotificationType
import com.huanchengfly.tieba.post.model.User
import com.huanchengfly.tieba.post.repository.BlockRuleSource
import com.huanchengfly.tieba.post.repository.BlockRules
import com.huanchengfly.tieba.post.repository.NotificationRepository
import com.huanchengfly.tieba.post.repository.blockRulesMatching
import com.huanchengfly.tieba.post.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = mockk<NotificationRepository>()
    private val blockRules = MutableStateFlow(BlockRules { null })

    private fun notification(postId: Long, content: String = "内容 $postId") = Notification(
        threadId = 1, postId = postId, inPost = false, threadTitle = "", content = content,
        author = User(postId, "u$postId", "", ""), time = postId,
    )

    private fun createViewModel() = NotificationsViewModel(
        repository = repository,
        blockRuleSource = object : BlockRuleSource {
            override val rules = blockRules
        },
    )

    @Test
    fun `a list loads the first time it is opened and pages on`() = runTest {
        coEvery { repository.page(NotificationType.ReplyMe, 1) } returns Page(listOf(notification(1)), next = 2)
        coEvery { repository.page(NotificationType.ReplyMe, 2) } returns Page(listOf(notification(2)))
        val viewModel = createViewModel()
        runCurrent()
        coVerify(exactly = 0) { repository.page(any(), any()) }

        viewModel.open(NotificationType.ReplyMe)
        viewModel.open(NotificationType.ReplyMe)
        runCurrent()
        viewModel.loadMore(NotificationType.ReplyMe)
        runCurrent()

        val list = viewModel.state.value.list(NotificationType.ReplyMe)
        assertEquals(listOf(1L, 2L), list.items.map { it.notification.postId })
        assertEquals(false, list.hasMore)
        coVerify(exactly = 1) { repository.page(NotificationType.ReplyMe, 1) }
    }

    @Test
    fun `blocked notifications follow the block rules`() = runTest {
        coEvery { repository.page(NotificationType.AtMe, 1) } returns
            Page(listOf(notification(1, "广告"), notification(2)))
        val viewModel = createViewModel()
        viewModel.open(NotificationType.AtMe)
        runCurrent()

        blockRules.value = blockRulesMatching { "广告" in it.content }
        runCurrent()

        assertEquals(listOf(true, false), viewModel.state.value.list(NotificationType.AtMe).items.map { it.blocked })
    }
}
