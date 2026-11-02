package com.huanchengfly.tieba.post.ui.page.main.explore

import com.huanchengfly.tieba.post.api.models.protos.Agree
import com.huanchengfly.tieba.post.api.models.protos.ThreadInfo
import com.huanchengfly.tieba.post.arch.MessageController
import com.huanchengfly.tieba.post.model.RecommendedThread
import com.huanchengfly.tieba.post.repository.BlockRuleSource
import com.huanchengfly.tieba.post.repository.BlockRules
import com.huanchengfly.tieba.post.repository.ExploreRepository
import com.huanchengfly.tieba.post.repository.toThreadSummary
import com.huanchengfly.tieba.post.testing.MainDispatcherRule
import com.huanchengfly.tieba.post.ui.page.main.explore.personalized.PersonalizedViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

class PersonalizedViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = mockk<ExploreRepository>()
    private val messages = mockk<MessageController>(relaxed = true)

    private fun thread(id: Long) = RecommendedThread(ThreadInfo(id = id, threadId = id, agree = Agree(hasAgree = 0)).toThreadSummary { _, _ -> })

    private fun createViewModel() = PersonalizedViewModel(
        repository = repository,
        messages = messages,
        blockRuleSource = object : BlockRuleSource {
            override val rules = MutableStateFlow(BlockRules { null })
        },
    )

    private val PersonalizedViewModel.ids get() = state.value.list.items.map { it.feed.thread.id }

    @Test
    fun `refreshing puts the new threads in front and marks where the old ones begin`() = runTest {
        coEvery { repository.recommended(true, 1) } returnsMany listOf(
            listOf(thread(1), thread(2)),
            listOf(thread(3), thread(1)),
        )
        coEvery { repository.recommended(false, 2) } returns listOf(thread(4))
        val viewModel = createViewModel()
        runCurrent()
        coVerify(exactly = 0) { repository.recommended(any(), any()) }

        viewModel.open()
        runCurrent()
        assertEquals(0, viewModel.state.value.newCount)
        viewModel.loadMore()
        runCurrent()
        viewModel.refresh()
        runCurrent()

        assertEquals(listOf(3L, 1L, 2L, 4L), viewModel.ids)
        assertEquals(1, viewModel.state.value.newCount)
        assertEquals(1, viewModel.state.value.lastRefresh?.count)
        assertEquals(2, viewModel.state.value.lastRefresh?.id)
    }

    @Test
    fun `a disliked thread stays hidden even if telling the server fails`() = runTest {
        coEvery { repository.recommended(true, 1) } returns listOf(thread(1), thread(2))
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
        coEvery { repository.recommended(true, 1) } returns listOf(thread(1), thread(2))
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
        coEvery { repository.recommended(true, 1) } returns listOf(thread(1))
        coEvery { repository.agreeThread(any(), true) } throws IOException()
        val viewModel = createViewModel()
        viewModel.open()
        runCurrent()

        viewModel.agree(thread(1).thread)
        runCurrent()

        assertEquals(false, viewModel.state.value.list.items.single().feed.thread.agree.agreed)
        verify { messages.showError(any(), any()) }
    }
}
