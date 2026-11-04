package oolong.client.ui.page.main.user

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import oolong.client.R
import oolong.client.arch.MessageController
import oolong.client.arch.Page
import oolong.client.model.HistoryEntry
import oolong.client.model.StoredThread
import oolong.client.model.UserProfile
import oolong.client.repository.HistoryRepository
import oolong.client.repository.ThreadStoreRepository
import oolong.client.repository.UpdateRepository
import oolong.client.repository.UserRepository
import oolong.client.testing.MainDispatcherRule
import oolong.core.network.identity.Session
import org.junit.Rule
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

class UserViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = mockk<UserRepository>()
    private val threadStore = mockk<ThreadStoreRepository> { coEvery { page(0) } returns Page(emptyList()) }
    private val visited = MutableStateFlow(emptyList<HistoryEntry.Thread>())
    private val history = mockk<HistoryRepository> { every { threads(1) } returns visited }
    private val updates = mockk<UpdateRepository> { every { available } returns flowOf(null) }
    private val stored = mutableListOf<UserProfile>()
    private val shown = mutableListOf<Int>()
    private var session: Session? = Session(uid = "99", name = "me", bduss = "b", stoken = null, tbs = null, zid = null)

    private fun createViewModel() = UserViewModel(
        repository = repository,
        cache = { stored += it },
        messages = object : MessageController {
            override fun show(message: Int, vararg args: Any) {
                shown += message
            }
        },
        sessionProvider = { session },
        threadStore = threadStore,
        history = history,
        updates = updates,
    )

    private fun stored(id: Long, newPosts: Int) = StoredThread(
        threadId = id,
        title = "t$id",
        author = null,
        deleted = false,
        markPostId = 0,
        maxPostId = 0,
        latestFloor = 0,
        newPostCount = newPosts,
    )

    @Test
    fun `refreshing keeps the reloaded profile with the account`() = runTest {
        val profile = UserProfile(id = 99, name = "me", nameShow = "", portrait = "p")
        coEvery { repository.profile(99) } returns profile
        val viewModel = createViewModel()

        viewModel.refresh()
        runCurrent()

        assertEquals(listOf(profile), stored)
        assertEquals(false, viewModel.state.value.isLoading)
    }

    @Test
    fun `a guest has nothing to refresh`() = runTest {
        session = null
        val viewModel = createViewModel()

        viewModel.refresh()
        runCurrent()

        coVerify(exactly = 0) { repository.profile(any()) }
    }

    @Test
    fun `a failed refresh says why`() = runTest {
        coEvery { repository.profile(99) } throws IOException("network")
        val viewModel = createViewModel()

        viewModel.refresh()
        runCurrent()

        assertEquals(listOf(R.string.toast_server_message), shown)
        assertEquals(false, viewModel.state.value.isLoading)
    }

    @Test
    fun `refreshing counts the stored threads with new replies`() = runTest {
        coEvery { repository.profile(99) } returns UserProfile(id = 99, name = "me", nameShow = "", portrait = "p")
        coEvery { threadStore.page(0) } returns Page(listOf(stored(1, 3), stored(2, 0), stored(3, 1)))
        val viewModel = createViewModel()

        viewModel.refresh()
        runCurrent()

        assertEquals(2, viewModel.state.value.storedWithReplies)
    }

    @Test
    fun `the thread visited last is shown as it changes`() = runTest {
        val viewModel = createViewModel()
        runCurrent()
        assertEquals(null, viewModel.state.value.lastThread)

        val thread = HistoryEntry.Thread(id = 1, time = 0, avatar = "", threadId = 7, title = "t", authorName = "a")
        visited.value = listOf(thread)
        runCurrent()

        assertEquals(thread, viewModel.state.value.lastThread)
    }
}
