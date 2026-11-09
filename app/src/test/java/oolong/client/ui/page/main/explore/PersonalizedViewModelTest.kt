package oolong.client.ui.page.main.explore

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import oolong.client.api.models.protos.Agree
import oolong.client.api.models.protos.ThreadInfo
import oolong.client.arch.MessageController
import oolong.client.arch.query.QueryClient
import oolong.client.model.RecommendedThread
import oolong.client.repository.BlockRuleSource
import oolong.client.repository.BlockRules
import oolong.client.repository.ExploreRepository
import oolong.client.repository.KeptFeed
import oolong.client.repository.toThreadSummary
import oolong.client.testing.MainDispatcherRule
import oolong.client.testing.queryClient
import oolong.client.testing.threadActions
import oolong.client.ui.page.main.explore.personalized.PersonalizedViewModel
import oolong.core.network.agree.AgreeRemoteDataSource
import org.junit.Rule
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

class PersonalizedViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = mockk<ExploreRepository> {
        coEvery { keptRecommended() } returns null
    }
    private val messages = mockk<MessageController>(relaxed = true)

    private fun thread(id: Long) = RecommendedThread(ThreadInfo(id = id, threadId = id, agree = Agree(hasAgree = 0)).toThreadSummary { _, _ -> })

    private val agreeRemote = mockk<AgreeRemoteDataSource>(relaxed = true)

    private fun TestScope.createViewModel(client: QueryClient = queryClient()) = PersonalizedViewModel(
        repository = repository,
        client = client,
        threads = threadActions(client, agreeRemote),
        messages = messages,
        blockRuleSource = object : BlockRuleSource {
            override val rules = MutableStateFlow(BlockRules { null })
        },
    )

    private val PersonalizedViewModel.ids get() = state.value.list.items.map { it.feed.thread.id }

    @Test
    fun `refreshing puts the new threads in front and marks where the old ones begin`() = runTest {
        coEvery { repository.recommended(true, 1, any()) } returnsMany listOf(
            listOf(thread(1), thread(2)),
            listOf(thread(3), thread(1)),
        )
        coEvery { repository.recommended(false, 2, any()) } returns listOf(thread(4))
        val viewModel = createViewModel()
        runCurrent()
        coVerify(exactly = 0) { repository.recommended(any(), any(), any()) }

        viewModel.open()
        runCurrent()
        assertEquals(null, viewModel.state.value.seenFrom)
        assertEquals(null, viewModel.state.value.lastRefresh)
        viewModel.loadMore()
        runCurrent()
        viewModel.refresh()
        runCurrent()

        assertEquals(listOf(3L, 1L, 2L, 4L), viewModel.ids)
        assertEquals(1L, viewModel.state.value.seenFrom)
        assertEquals(1, viewModel.state.value.lastRefresh?.count)

        viewModel.refreshShown(viewModel.state.value.lastRefresh!!.id)
        runCurrent()
        assertEquals(null, viewModel.state.value.lastRefresh)
    }

    @Test
    fun `a failed refresh of a shown list says so and keeps the list`() = runTest {
        coEvery { repository.recommended(true, 1, any()) } returns listOf(thread(1)) andThenThrows IllegalStateException()
        val viewModel = createViewModel()
        viewModel.open()
        runCurrent()
        viewModel.refresh()
        runCurrent()

        assertEquals(listOf(1L), viewModel.ids)
        assertEquals(true, viewModel.state.value.lastRefresh?.failed)
    }

    @Test
    fun `a disliked thread stays hidden even if telling the server fails`() = runTest {
        coEvery { repository.recommended(true, 1, any()) } returns listOf(thread(1), thread(2))
        coEvery { repository.dislike(any(), any(), any()) } throws IOException()
        val viewModel = createViewModel()
        viewModel.open()
        runCurrent()

        viewModel.dislike(thread(2).thread, emptyList(), clickTime = 5)
        runCurrent()
        assertEquals(setOf(2L), viewModel.state.value.undoable.keys)
        coVerify(exactly = 0) { repository.dislike(any(), any(), any()) }
        advanceUntilIdle()

        assertEquals(setOf(2L), viewModel.state.value.disliked)
        assertEquals(emptySet(), viewModel.state.value.undoable.keys)
        coVerify { repository.dislike(any(), any(), 5) }
    }

    @Test
    fun `a dislike taken back in time never reaches the server`() = runTest {
        coEvery { repository.recommended(true, 1, any()) } returns listOf(thread(1), thread(2))
        val viewModel = createViewModel()
        viewModel.open()
        runCurrent()

        viewModel.dislike(thread(2).thread, emptyList(), clickTime = 5)
        runCurrent()
        viewModel.undoDislike(2)
        advanceUntilIdle()

        assertEquals(emptySet(), viewModel.state.value.disliked)
        assertEquals(emptySet(), viewModel.state.value.undoable.keys)
        coVerify(exactly = 0) { repository.dislike(any(), any(), any()) }
    }

    @Test
    fun `a failed like is undone`() = runTest {
        coEvery { repository.recommended(true, 1, any()) } returns listOf(thread(1))
        coEvery { agreeRemote.opAgree(any(), any(), any(), any()) } throws IOException()
        val viewModel = createViewModel()
        viewModel.open()
        runCurrent()

        viewModel.agree(thread(1).thread)
        runCurrent()

        assertEquals(false, viewModel.state.value.list.items.single().feed.thread.agree.agreed)
        verify { messages.showError(any(), any()) }
    }

    @Test
    fun `a like made on another page shows in the list`() = runTest {
        coEvery { repository.recommended(true, 1, any()) } returns listOf(thread(1))
        val client = queryClient()
        val viewModel = createViewModel(client)
        viewModel.open()
        runCurrent()

        threadActions(client).agree(thread(1).thread, true)
        runCurrent()

        assertEquals(true, viewModel.state.value.list.items.single().feed.thread.agree.agreed)
    }

    @Test
    fun `without loading anew as the app opens, the list shows as last loaded, and a pull loads what is new`() = runTest {
        coEvery { repository.keptRecommended() } returns KeptFeed(listOf(thread(1), thread(2), thread(3)), next = 4, hidden = setOf(3L))
        coEvery { repository.recommended(true, 1, any()) } returns listOf(thread(5), thread(1))
        val viewModel = createViewModel()
        viewModel.open()
        runCurrent()

        assertEquals(listOf(1L, 2L, 3L), viewModel.ids)
        assertEquals(setOf(3L), viewModel.state.value.disliked)
        coVerify(exactly = 0) { repository.recommended(any(), any(), any()) }

        viewModel.refresh()
        runCurrent()
        assertEquals(listOf(5L, 1L, 2L, 3L), viewModel.ids)
        assertEquals(1, viewModel.state.value.lastRefresh?.count)
        coVerify { repository.recommended(true, 1, false) }
    }
}
