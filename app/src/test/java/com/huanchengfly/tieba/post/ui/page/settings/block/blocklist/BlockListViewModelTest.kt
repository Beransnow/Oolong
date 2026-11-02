package com.huanchengfly.tieba.post.ui.page.settings.block.blocklist

import com.huanchengfly.tieba.post.model.BlockRule
import com.huanchengfly.tieba.post.model.BlockRule.Condition
import com.huanchengfly.tieba.post.model.BlockRule.Pattern
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
import kotlin.test.assertTrue

class BlockListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val now = System.currentTimeMillis()
    private val keyword = BlockRule(id = 1, condition = Condition.Text(Pattern.Words(listOf("剧透"))), createdAt = now - 100 * DAY)
    private val user = BlockRule(id = 2, condition = Condition.User(7, "someone"), createdAt = now - DAY, action = BlockRule.Action.Allow)
    private val forum = BlockRule(id = 3, condition = Condition.Forum("抽卡"), createdAt = now - 200 * DAY, lastHitAt = now - DAY)
    private val repository = mockk<BlockListRepository>(relaxed = true) {
        every { blockRules } returns flowOf(listOf(keyword, user, forum))
    }

    @Test
    fun `lists the rules newest first and finds those long without a hit`() = runTest {
        val viewModel = BlockListViewModel(repository)
        runCurrent()

        val state = viewModel.state.value
        assertEquals(listOf(3L, 2L, 1L), state.rules.map { it.id })
        assertEquals(listOf(1L), state.stale.map { it.id })
        assertEquals(false, state.loading)
    }

    @Test
    fun `rules are narrowed by kind and by what they match`() {
        assertTrue(keyword.matches(RuleFilter.Keyword) && !keyword.matches(RuleFilter.User))
        assertTrue(forum.matches("抽") && !forum.matches("原神"))

        val joined = BlockRule(condition = Condition.AllOf(listOf(Condition.Forum("原神"), Condition.Not(Condition.LevelBelow(3)))))
        assertTrue(joined.matches(RuleFilter.Forum) && joined.matches(RuleFilter.Other) && !joined.matches(RuleFilter.User))
        assertTrue(joined.matches("原神"))
    }

    @Test
    fun `deleted rules can be put back`() = runTest {
        val viewModel = BlockListViewModel(repository)
        runCurrent()

        viewModel.delete(listOf(keyword))
        viewModel.restore(listOf(keyword))
        runCurrent()

        coVerify { repository.remove(listOf(1L)) }
        coVerify { repository.restore(listOf(keyword)) }
    }

    private companion object {
        const val DAY = 24 * 60 * 60 * 1000L
    }
}
