package com.huanchengfly.tieba.post.ui.page.user

import com.huanchengfly.tieba.core.network.agree.AgreeRemoteDataSource
import com.huanchengfly.tieba.core.network.identity.Session
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.api.models.protos.Agree
import com.huanchengfly.tieba.post.api.models.protos.PostInfoList
import com.huanchengfly.tieba.post.arch.LoadStatus
import com.huanchengfly.tieba.post.arch.MessageController
import com.huanchengfly.tieba.post.arch.Page
import com.huanchengfly.tieba.post.arch.query.QueryClient
import com.huanchengfly.tieba.post.model.LikedForum
import com.huanchengfly.tieba.post.model.UserProfile
import com.huanchengfly.tieba.post.model.UserThread
import com.huanchengfly.tieba.post.repository.BlackSettings
import com.huanchengfly.tieba.post.repository.UserPostsPage
import com.huanchengfly.tieba.post.repository.UserRepository
import com.huanchengfly.tieba.post.repository.toUserPost
import com.huanchengfly.tieba.post.testing.MainDispatcherRule
import com.huanchengfly.tieba.post.testing.queryClient
import com.huanchengfly.tieba.post.testing.threadActions
import com.huanchengfly.tieba.post.ui.navigation.UserProfileKey
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UserProfileViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = mockk<UserRepository>(relaxed = true)
    private val shown = mutableListOf<Int>()
    private val user = UserProfile(id = 7, name = "n", nameShow = "", portrait = "p")

    private val agreeRemote = mockk<AgreeRemoteDataSource>(relaxed = true)

    private fun TestScope.createViewModel(uid: Long = 7, client: QueryClient = queryClient()) = UserProfileViewModel(
        key = UserProfileKey(uid),
        repository = repository,
        client = client,
        threads = threadActions(client, agreeRemote),
        blockList = mockk(relaxed = true),
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
        coEvery { repository.posts(7, true, 1) } returns UserPostsPage(Page(listOf(PostInfoList(thread_id = 1, post_id = 2, is_thread = 1).toUserPost { _, _ -> })), hidden = true)
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
        assertEquals(listOf(1L), state.threads.items.map { (it as UserThread).thread.id })
        assertTrue(state.postsHidden)
        assertEquals(listOf(52L), state.forums.items.map { it.id })
        coVerify(exactly = 0) { repository.posts(7, false, any()) }
    }

    @Test
    fun `blacklisting reads what the user is barred from and saves the change`() = runTest {
        coEvery { repository.profile(7) } returns user
        coEvery { repository.blackSettings(7) } returns BlackSettings(follow = false, interact = false, chat = false)
        val viewModel = createViewModel()
        runCurrent()

        viewModel.loadBlack()
        runCurrent()
        assertEquals(BlackSettings(follow = false, interact = false, chat = false), viewModel.state.value.black)

        val barred = BlackSettings(follow = true, interact = true, chat = false)
        viewModel.setBlack(barred)
        runCurrent()
        coVerify { repository.setBlack(7, barred) }
        assertEquals(barred, viewModel.state.value.black)
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
            UserPostsPage(Page(listOf(PostInfoList(thread_id = 1, post_id = 2, is_thread = 1, agree = Agree(hasAgree = 0)).toUserPost { _, _ -> })))
        val viewModel = createViewModel()
        viewModel.open(UserTab.Threads)
        runCurrent()

        viewModel.agree(UserTab.Threads, threadId = 1, postId = 2)
        runCurrent()

        assertEquals(true, (viewModel.state.value.threads.items.single() as UserThread).thread.agree.agreed)
        coVerify { agreeRemote.opAgree(any(), any(), undo = false) }
    }

    @Test
    fun `the user's own profile is theirs`() = runTest {
        coEvery { repository.profile(99) } returns user.copy(id = 99)

        val viewModel = createViewModel(uid = 99)
        runCurrent()

        assertTrue(viewModel.state.value.isSelf)
    }
}
