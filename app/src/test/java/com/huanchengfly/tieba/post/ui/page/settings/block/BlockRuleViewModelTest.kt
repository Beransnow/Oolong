package com.huanchengfly.tieba.post.ui.page.settings.block

import com.huanchengfly.tieba.post.model.BlockRule
import com.huanchengfly.tieba.post.model.BlockRule.Condition
import com.huanchengfly.tieba.post.model.BlockRule.Pattern
import com.huanchengfly.tieba.post.model.ContentKind
import com.huanchengfly.tieba.post.model.FlatCondition
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

    private val saved = BlockRule(id = 4, condition = Condition.Forum("抽卡"))
    private val repository = mockk<BlockListRepository>(relaxed = true) {
        every { blockRules } returns flowOf(listOf(saved))
    }

    private fun BlockRuleViewModel.test(index: Int) = state.value.conditions!!.tests[index]

    @Test
    fun `a new rule is saved only once every test tests something, cleaned`() = runTest {
        val viewModel = BlockRuleViewModel(BlockRuleKey(), repository)
        runCurrent()
        assertFalse(viewModel.state.value.canSave)

        viewModel.setTest(0, FlatCondition.Test(viewModel.test(0).condition.withTypedValue("剧透  第三章")))
        assertTrue(viewModel.state.value.canSave)
        viewModel.addTest(ConditionField.Forum)
        assertFalse(viewModel.state.value.canSave)
        viewModel.setTest(1, FlatCondition.Test(viewModel.test(1).condition.withTypedValue(" 星穹铁道吧 ")))
        viewModel.save()
        runCurrent()

        coVerify {
            repository.add(
                BlockRule(
                    condition = Condition.AllOf(
                        listOf(Condition.Text(Pattern.Words(listOf("剧透", "第三章"))), Condition.Forum("星穹铁道")),
                    ),
                ),
            )
        }
    }

    @Test
    fun `a broken pattern cannot be saved`() = runTest {
        val viewModel = BlockRuleViewModel(BlockRuleKey(), repository)
        runCurrent()

        viewModel.setTest(0, FlatCondition.Test(conditionOf(ConditionField.TitleOrBody, ConditionOp.Matches, "(")))

        assertFalse(viewModel.state.value.canSave)
    }

    @Test
    fun `tests are joined, turned around and removed`() = runTest {
        val viewModel = BlockRuleViewModel(BlockRuleKey(id = 4), repository)
        runCurrent()
        assertEquals(saved, viewModel.state.value.draft)

        viewModel.addTest(ConditionField.ThreadAuthor)
        viewModel.setTest(1, viewModel.test(1).copy(negated = true))
        viewModel.setJoin(all = false)
        assertEquals(
            Condition.AnyOf(listOf(Condition.Forum("抽卡"), Condition.Not(Condition.ThreadAuthor))),
            viewModel.state.value.draft?.condition,
        )

        viewModel.removeTest(0)
        assertEquals(Condition.Not(Condition.ThreadAuthor), viewModel.state.value.draft?.condition)

        viewModel.edit { it.copy(scope = BlockRule.Scope.Feeds) }
        viewModel.save()
        runCurrent()
        coVerify { repository.update(saved.copy(condition = Condition.Not(Condition.ThreadAuthor), scope = BlockRule.Scope.Feeds)) }
    }

    @Test
    fun `changing how a row judges keeps its value`() {
        val words = conditionOf(ConditionField.Author, ConditionOp.Contains, "抽奖")

        assertEquals(Condition.AuthorName(Pattern.Regex("抽奖")), words.withOp(ConditionOp.Matches))
        assertEquals(Condition.User(0, "抽奖"), words.withOp(ConditionOp.Is))
        assertEquals(Condition.Only(ContentKind.Link), Condition.Has(ContentKind.Link).withOp(ConditionOp.Only))
    }
}
