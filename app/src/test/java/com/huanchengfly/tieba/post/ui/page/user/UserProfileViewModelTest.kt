package com.huanchengfly.tieba.post.ui.page.user

import com.huanchengfly.tieba.core.network.identity.Session
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.api.models.protos.Agree
import com.huanchengfly.tieba.post.api.models.protos.PostInfoList
import com.huanchengfly.tieba.post.arch.LoadStatus
import com.huanchengfly.tieba.post.arch.MessageController
import com.huanchengfly.tieba.post.arch.Page
import com.huanchengfly.tieba.post.model.LikedForum
import com.huanchengfly.tieba.post.model.UserProfile
import com.huanchengfly.tieba.post.repository.UserPostsPage
import com.huanchengfly.tieba.post.repository.UserRepository
import com.huanchengfly.tieba.post.testing.MainDispatcherRule
import com.huanchengfly.tieba.post.ui.navigation.UserProfileKey
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule

class UserProfileViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = mockk<UserRepository>(relaxed = true)
    private val shown = mutableListOf<Int>()
    private val user = UserProfile(id = 7, name = "n", nameShow = "", portrait = "p")

    private fun createViewModel(uid: Long = 7) = UserProfileViewModel(
        key = UserProfileKey(uid),
        repository = repository,
        messages = object : MessageController {
            override fun show(message: Int, vararg args: Any) {
                shown += message
            }
        },
        sessionProvider = { Session(uid = "99", name = "me", bduss = "b", stoken = null, tbs = null, zid = null) },
    )

    @Test
    fun `loads the profile and only the lists that are opened`() = runTest {
        coEvery { repository.profile(7) } returns user
        coEvery { repository.posts(7, true, 1) } returns UserPostsPage(Page(listOf(PostInfoList(thread_id = 1, post_id = 2))), hidden = true)
        coEvery { repository.likedForums(7, 1) } returns Page(listOf(LikedForum(52, "吧", "", 1, "")))

        val viewModel = createViewModel()
        runCurrent()
        viewModel.open(UserTab.Threads)
        viewModel.open(UserTab.Forums)
        runCurrent()

        val state = viewModel.state.value
        assertEquals(user, state.profile)
        assertEquals(false, state.isSelf)
        assertTrue(state.loggedIn)
        assertEquals(listOf(1L), state.threads.items.map { it.data.get { thread_id } })
        assertTrue(state.postsHidden)
        assertEquals(listOf(52L), state.forums.items.map { it.id })
        coVerify(exactly = 0) { repository.posts(7, false, any()) }
    }

    @Test
    fun `a failed profile load keeps its error`() = runTest {
        coEvery { repository.profile(7) } throws IOException("network")

        val viewModel = createViewModel()
        runCurrent()

        assertIs<LoadStatus.Error>(viewModel.state.value.load)
    }

    @Test
    fun `following shows at once and is rolled back when it fails`() = runTest {
        coEvery { repository.profile(7) } returns user
        val response = CompletableDeferred<Unit>()
        coEvery { repository.follow(any()) } coAnswers {
            response.await()
            throw IOException("network")
        }
        val viewModel = createViewModel()
        runCurrent()

        viewModel.toggleFollow()
        runCurrent()
        assertEquals(true, viewModel.state.value.profile?.followed)
        assertTrue(viewModel.state.value.following)

        response.complete(Unit)
        runCurrent()
        assertEquals(false, viewModel.state.value.profile?.followed)
        assertEquals(false, viewModel.state.value.following)
        assertEquals(listOf(R.string.toast_like_failed), shown)
    }

    @Test
    fun `agreeing with a thread updates it in its list`() = runTest {
        coEvery { repository.profile(7) } returns user
        coEvery { repository.posts(7, true, 1) } returns
            UserPostsPage(Page(listOf(PostInfoList(thread_id = 1, post_id = 2, agree = Agree(hasAgree = 0)))))
        val viewModel = createViewModel()
        viewModel.open(UserTab.Threads)
        runCurrent()

        viewModel.agree(UserTab.Threads, threadId = 1, postId = 2)
        runCurrent()

        assertEquals(1, viewModel.state.value.threads.items.single().data.get { agree?.hasAgree })
        coVerify { repository.agreeThread(any(), true) }
    }

    @Test
    fun `the user's own profile is theirs`() = runTest {
        coEvery { repository.profile(99) } returns user.copy(id = 99)

        val viewModel = createViewModel(uid = 99)
        runCurrent()

        assertTrue(viewModel.state.value.isSelf)
    }
}
