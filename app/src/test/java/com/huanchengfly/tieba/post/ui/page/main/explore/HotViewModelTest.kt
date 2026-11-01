package com.huanchengfly.tieba.post.ui.page.main.explore

import com.huanchengfly.tieba.post.api.models.protos.ThreadInfo
import com.huanchengfly.tieba.post.arch.LoadStatus
import com.huanchengfly.tieba.post.arch.MessageController
import com.huanchengfly.tieba.post.model.HotCategory
import com.huanchengfly.tieba.post.model.HotThreads
import com.huanchengfly.tieba.post.repository.BlockRuleSource
import com.huanchengfly.tieba.post.repository.BlockRules
import com.huanchengfly.tieba.post.repository.ExploreRepository
import com.huanchengfly.tieba.post.testing.MainDispatcherRule
import com.huanchengfly.tieba.post.ui.page.main.explore.hot.HotViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

class HotViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = mockk<ExploreRepository>()

    private fun hot(vararg ids: Long) = HotThreads(
        topics = emptyList(),
        categories = listOf(HotCategory("game", "游戏")),
        threads = ids.map { ThreadInfo(id = it, threadId = it) },
    )

    private fun createViewModel() = HotViewModel(
        repository = repository,
        messages = mockk<MessageController>(relaxed = true),
        blockRuleSource = object : BlockRuleSource {
            override val rules = MutableStateFlow(BlockRules { _, _ -> false })
        },
    )

    private val HotViewModel.ids get() = state.value.threads.map { it.thread.get { threadId } }

    @Test
    fun `loads once when opened and switches categories`() = runTest {
        coEvery { repository.hotThreads(HotCategory.ALL) } returns hot(1, 2)
        coEvery { repository.hotThreads("game") } returns hot(3)
        val viewModel = createViewModel()

        viewModel.open()
        viewModel.open()
        runCurrent()
        viewModel.selectCategory("game")
        runCurrent()

        assertEquals("game", viewModel.state.value.category)
        assertEquals(listOf(3L), viewModel.ids)
        assertEquals(LoadStatus.Idle, viewModel.state.value.load)
        coVerify(exactly = 1) { repository.hotThreads(HotCategory.ALL) }
    }

    @Test
    fun `a category that fails to load leaves the last one selected`() = runTest {
        coEvery { repository.hotThreads(HotCategory.ALL) } returns hot(1, 2)
        coEvery { repository.hotThreads("game") } throws IOException()
        val viewModel = createViewModel()
        viewModel.open()
        runCurrent()

        viewModel.selectCategory("game")
        runCurrent()

        assertEquals(HotCategory.ALL, viewModel.state.value.category)
        assertEquals(listOf(1L, 2L), viewModel.ids)
        assertEquals(false, viewModel.state.value.switching)
    }
}
