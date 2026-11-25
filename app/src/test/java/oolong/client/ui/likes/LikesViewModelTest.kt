package oolong.client.ui.likes

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import oolong.client.arch.MessageController
import oolong.client.arch.Page
import oolong.client.data.thread.ThreadActions
import oolong.client.data.user.UserRepository
import oolong.client.model.Agree
import oolong.client.model.ForumSummary
import oolong.client.model.LikedReply
import oolong.client.model.User
import oolong.client.testing.MainDispatcherRule
import oolong.client.testing.queryClient
import org.junit.Rule
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

class LikesViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = mockk<UserRepository>()
    private val threadActions = mockk<ThreadActions>(relaxed = true)
    private val messages = mockk<MessageController>(relaxed = true)

    private fun reply(id: Long, floorId: Long? = null) = LikedReply(
        postId = id, floorId = floorId, author = User(1, "a", "", ""), time = 0, content = persistentListOf(),
        agree = Agree(count = 5, agreed = true), threadId = 9, threadTitle = "t", forum = ForumSummary(3, "f", ""),
    )

    private fun TestScope.viewModel() = LikesViewModel(repository, queryClient(), threadActions, messages)

    @Test
    fun `a list loads only once its tab shows`() = runTest {
        coEvery { repository.likedReplies(1) } returns Page(listOf(reply(1)))
        val viewModel = viewModel()
        runCurrent()
        coVerify(exactly = 0) { repository.likedReplies(any()) }

        viewModel.open(LikesTab.Replies)
        viewModel.open(LikesTab.Replies)
        runCurrent()

        coVerify(exactly = 1) { repository.likedReplies(1) }
        assertEquals(listOf(1L), viewModel.state.value.replies.items.map { it.postId })
    }

    @Test
    fun `a like taken back stays on the list unliked, as a post or as a reply inside one`() = runTest {
        coEvery { repository.likedReplies(1) } returns Page(listOf(reply(1), reply(2, floorId = 20)))
        val viewModel = viewModel()
        viewModel.open(LikesTab.Replies)
        runCurrent()

        viewModel.state.value.replies.items.forEach { viewModel.agree(it) }
        runCurrent()

        assertEquals(listOf(Agree(4, false), Agree(4, false)), viewModel.state.value.replies.items.map { it.agree })
        coVerify { threadActions.agreePost(9, 3, 1, false) }
        coVerify { threadActions.agreeSubPost(9, 3, 2, false) }
    }

    @Test
    fun `a like that could not be taken back shows again`() = runTest {
        coEvery { repository.likedReplies(1) } returns Page(listOf(reply(1)))
        coEvery { threadActions.agreePost(any(), any(), any(), any()) } throws IOException()
        val viewModel = viewModel()
        viewModel.open(LikesTab.Replies)
        runCurrent()

        viewModel.agree(viewModel.state.value.replies.items.single())
        runCurrent()

        assertEquals(Agree(5, true), viewModel.state.value.replies.items.single().agree)
        verify { messages.showError(any(), any()) }
    }
}
