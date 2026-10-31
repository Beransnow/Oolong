package com.huanchengfly.tieba.post.ui.page.forum.rule

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.huanchengfly.tieba.core.network.forum.ForumRemoteDataSource
import com.huanchengfly.tieba.post.arch.LoadState
import com.huanchengfly.tieba.post.repository.FORUM_ID
import com.huanchengfly.tieba.post.repository.ForumRepository
import com.huanchengfly.tieba.post.repository.forumRuleDetail
import com.huanchengfly.tieba.post.repository.forumRuleData
import com.huanchengfly.tieba.post.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ForumRuleDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val remote = mockk<ForumRemoteDataSource>()

    private fun createViewModel() = ForumRuleDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("forumId" to FORUM_ID)),
        forumRepository = ForumRepository(remote),
    )

    @Test
    fun `loads the rules of the forum from the navigation arguments`() = runTest {
        coEvery { remote.forumRuleDetail(FORUM_ID) } returns forumRuleData()

        createViewModel().ruleDetail.state.test {
            assertEquals(LoadState.Loading, awaitItem())
            assertEquals(LoadState.Success(forumRuleDetail()), awaitItem())
        }
    }

    @Test
    fun `retry reloads after an error`() = runTest {
        coEvery { remote.forumRuleDetail(FORUM_ID) } throws IOException("network") andThen forumRuleData()
        val viewModel = createViewModel()

        viewModel.ruleDetail.state.test {
            assertEquals(LoadState.Loading, awaitItem())
            assertIs<LoadState.Error>(awaitItem())

            viewModel.ruleDetail.retry()
            assertEquals(LoadState.Loading, awaitItem())
            assertEquals(LoadState.Success(forumRuleDetail()), awaitItem())
        }
    }
}
