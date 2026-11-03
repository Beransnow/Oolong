package oolong.client.ui.page.main.home

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import oolong.client.R
import oolong.client.arch.LoadStatus
import oolong.client.arch.MessageController
import oolong.client.model.FollowedForum
import oolong.client.model.VisitedForum
import oolong.client.repository.HomeRepository
import oolong.client.testing.MainDispatcherRule
import oolong.core.network.identity.Session
import org.junit.Rule
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val pinned = MutableStateFlow(emptySet<Long>())
    private val visited = MutableStateFlow(emptyList<VisitedForum>())
    private val repository = mockk<HomeRepository>(relaxed = true) {
        every { pinnedForumIds } returns pinned
        every { visitedForums } returns visited
    }
    private val shown = mutableListOf<Int>()
    private var session: Session? = Session(uid = "99", name = "me", bduss = "b", stoken = null, tbs = null, zid = null)

    private fun forum(id: Long) = FollowedForum(id, "吧$id", "", level = 1, signedIn = false)

    private fun createViewModel() = HomeViewModel(
        repository = repository,
        messages = object : MessageController {
            override fun show(message: Int, vararg args: Any) {
                shown += message
            }
        },
        sessionProvider = { session },
    )

    @Test
    fun `lists the followed forums with the pinned ones and the visited ones`() = runTest {
        coEvery { repository.followedForums() } returns listOf(forum(1), forum(2), forum(3))
        pinned.value = setOf(3L)
        visited.value = listOf(VisitedForum("吧9", "吧9吧", ""))

        val viewModel = createViewModel()
        runCurrent()

        val state = viewModel.state.value
        assertEquals(listOf(1L, 2L, 3L), state.forums.map { it.id })
        assertEquals(listOf(3L), state.pinned.map { it.id })
        assertEquals(listOf("吧9"), state.visited.map { it.name })
        assertEquals(LoadStatus.Idle, state.load)
        assertTrue(state.loggedIn)
    }

    @Test
    fun `starts out loading rather than as a guest`() = runTest {
        coEvery { repository.followedForums() } returns listOf(forum(1))

        val viewModel = createViewModel()

        assertEquals(LoadStatus.Loading, viewModel.state.value.load)
    }

    @Test
    fun `a guest has nothing to load`() = runTest {
        session = null

        val viewModel = createViewModel()
        runCurrent()

        assertEquals(false, viewModel.state.value.loggedIn)
        assertEquals(LoadStatus.Idle, viewModel.state.value.load)
        coVerify(exactly = 0) { repository.followedForums() }
    }

    @Test
    fun `a failed load keeps its error`() = runTest {
        coEvery { repository.followedForums() } throws IOException("network")

        val viewModel = createViewModel()
        runCurrent()

        assertIs<LoadStatus.Error>(viewModel.state.value.load)
    }

    @Test
    fun `unfollowing removes the forum, and a failure says so`() = runTest {
        coEvery { repository.followedForums() } returns listOf(forum(1), forum(2))
        coEvery { repository.unfollow(forum(2)) } throws IOException("network")
        val viewModel = createViewModel()
        runCurrent()

        viewModel.unfollow(forum(1))
        viewModel.unfollow(forum(2))
        runCurrent()

        assertEquals(listOf(2L), viewModel.state.value.forums.map { it.id })
        assertEquals(listOf(R.string.toast_unlike_failed), shown)
    }

    @Test
    fun `pinning goes through the repository`() = runTest {
        coEvery { repository.followedForums() } returns listOf(forum(1))
        val viewModel = createViewModel()
        runCurrent()

        viewModel.pin(forum(1))
        runCurrent()
        viewModel.unpin(forum(1))
        runCurrent()

        coVerify { repository.pin(1) }
        coVerify { repository.unpin(1) }
    }
}
