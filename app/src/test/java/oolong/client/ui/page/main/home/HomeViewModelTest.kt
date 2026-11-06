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
import oolong.client.arch.query.QueryState
import oolong.client.model.FollowedForum
import oolong.client.model.VisitedForum
import oolong.client.repository.HomeRepository
import oolong.client.testing.MainDispatcherRule
import org.junit.Rule
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val pinned = MutableStateFlow(emptySet<Long>())
    private val visited = MutableStateFlow(emptyList<VisitedForum>())
    private val account = MutableStateFlow("99")
    private val followed = MutableStateFlow(QueryState<List<FollowedForum>>(status = LoadStatus.Loading))
    private val repository = mockk<HomeRepository>(relaxed = true) {
        every { pinnedForumIds } returns pinned
        every { visitedForums } returns visited
        every { this@mockk.account } returns this@HomeViewModelTest.account
        every { followedForums(any()) } returns followed
    }
    private val shown = mutableListOf<Int>()

    private fun forum(id: Long) = FollowedForum(id, "吧$id", "", level = 1, signedIn = false)

    private fun loaded(vararg ids: Long) {
        followed.value = QueryState(data = ids.map(::forum))
    }

    private fun createViewModel() = HomeViewModel(
        repository = repository,
        messages = object : MessageController {
            override fun show(message: Int, vararg args: Any) {
                shown += message
            }
        },
    )

    @Test
    fun `lists the followed forums with the pinned ones and the visited ones`() = runTest {
        loaded(1, 2, 3)
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
        val viewModel = createViewModel()
        runCurrent()

        assertEquals(LoadStatus.Loading, viewModel.state.value.load)
    }

    @Test
    fun `a guest has nothing to load`() = runTest {
        account.value = ""

        val viewModel = createViewModel()
        runCurrent()

        assertEquals(false, viewModel.state.value.loggedIn)
        assertEquals(LoadStatus.Idle, viewModel.state.value.load)
        coVerify(exactly = 0) { repository.followedForums(any()) }
    }

    @Test
    fun `a failed first load keeps its error, and a quiet reload behind loaded forums shows nothing`() = runTest {
        followed.value = QueryState(status = LoadStatus.Error(IOException("network")))
        val viewModel = createViewModel()
        runCurrent()
        assertIs<LoadStatus.Error>(viewModel.state.value.load)

        followed.value = QueryState(data = listOf(forum(1)), status = LoadStatus.Loading)
        runCurrent()

        assertEquals(LoadStatus.Idle, viewModel.state.value.load)
        assertFalse(viewModel.state.value.refreshing)
    }

    @Test
    fun `refreshing on request shows it, and says so when it fails`() = runTest {
        loaded(1)
        coEvery { repository.reloadFollowedForums("99") } throws IOException("network")
        val viewModel = createViewModel()
        runCurrent()

        viewModel.refresh()
        runCurrent()

        coVerify { repository.reloadFollowedForums("99") }
        assertFalse(viewModel.state.value.refreshing)
        assertEquals(listOf(R.string.toast_load_failed), shown)
    }

    @Test
    fun `follows the account that is logged in`() = runTest {
        loaded(1)
        val viewModel = createViewModel()
        runCurrent()

        account.value = "100"
        runCurrent()

        coVerify { repository.followedForums("100") }
    }

    @Test
    fun `unfollowing removes the forum at once, and a failure brings it back and says so`() = runTest {
        loaded(1, 2)
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
        loaded(1)
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
