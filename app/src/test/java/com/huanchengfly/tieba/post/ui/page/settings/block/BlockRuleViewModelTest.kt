package com.huanchengfly.tieba.post.ui.page.settings.block

import com.huanchengfly.tieba.post.model.BlockRule
import com.huanchengfly.tieba.post.model.BlockRule.Match
import com.huanchengfly.tieba.post.repository.BlockListRepository
import com.huanchengfly.tieba.post.testing.MainDispatcherRule
import com.huanchengfly.tieba.post.ui.navigation.BlockRuleKey
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlockRuleViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val saved = BlockRule(id = 4, match = Match.Forum("抽卡"))
    private val repository = mockk<BlockListRepository>(relaxed = true) {
        every { blockRules } returns flowOf(listOf(saved))
    }

    @Test
    fun `a new rule is saved only once it matches something, with its words cleaned`() = runTest {
        val viewModel = BlockRuleViewModel(BlockRuleKey(), repository)
        runCurrent()
        assertFalse(viewModel.state.value.canSave)

        viewModel.edit { it.copy(match = Match.Keyword(listOf("剧透", "", "第三章")), inForum = " 星穹铁道吧 ") }
        assertTrue(viewModel.state.value.canSave)
        viewModel.save()
        runCurrent()

        coVerify { repository.add(BlockRule(match = Match.Keyword(listOf("剧透", "第三章")), inForum = "星穹铁道")) }
    }

    @Test
    fun `a broken pattern cannot be saved`() = runTest {
        val viewModel = BlockRuleViewModel(BlockRuleKey(), repository)
        runCurrent()

        viewModel.edit { it.copy(match = Match.Keyword(listOf("("), regex = true)) }

        assertFalse(viewModel.state.value.canSave)
    }

    @Test
    fun `a saved rule is changed in place, and switching kinds drops conditions`() = runTest {
        val viewModel = BlockRuleViewModel(BlockRuleKey(id = 4), repository)
        runCurrent()
        assertEquals(saved, viewModel.state.value.draft)

        viewModel.edit { it.copy(scope = BlockRule.Scope.Feeds) }
        viewModel.save()
        runCurrent()
        coVerify { repository.update(saved.copy(scope = BlockRule.Scope.Feeds)) }

        viewModel.edit { it.copy(inForum = "甲", titleOnly = true) }
        viewModel.setKind(Match.User(0, ""))
        assertEquals(null, viewModel.state.value.draft?.inForum)
        assertFalse(viewModel.state.value.draft!!.titleOnly)
    }
}
