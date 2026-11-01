package com.huanchengfly.tieba.post.ui.page.settings.block.blocklist

import com.huanchengfly.tieba.post.arch.MessageController
import com.huanchengfly.tieba.post.models.database.Block
import com.huanchengfly.tieba.post.repository.BlockListRepository
import com.huanchengfly.tieba.post.testing.MainDispatcherRule
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

class BlockListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = mockk<BlockListRepository>(relaxed = true) {
        every { rules } returns flowOf(
            listOf(
                Block(Block.CATEGORY_BLACK_LIST, Block.TYPE_KEYWORD, keywords = "[\"a\"]", id = 1),
                Block(Block.CATEGORY_WHITE_LIST, Block.TYPE_USER, username = "u", id = 2),
            )
        )
    }

    @Test
    fun `lists each rule on its own list and adds keywords split by spaces`() = runTest {
        val viewModel = BlockListViewModel(repository, mockk<MessageController>(relaxed = true))
        runCurrent()

        viewModel.add(Block.CATEGORY_BLACK_LIST, "  ")
        runCurrent()
        viewModel.add(Block.CATEGORY_BLACK_LIST, "广告  推广")
        runCurrent()

        assertEquals(listOf(1L), viewModel.state.value.blackList.map { it.id })
        assertEquals(listOf(2L), viewModel.state.value.whiteList.map { it.id })
        assertEquals(false, viewModel.state.value.loading)
        coVerify(exactly = 1) { repository.addKeywords(any(), any()) }
        coVerify { repository.addKeywords(Block.CATEGORY_BLACK_LIST, listOf("广告", "推广")) }
    }
}
