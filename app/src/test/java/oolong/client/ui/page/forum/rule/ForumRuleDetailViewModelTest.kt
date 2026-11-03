package oolong.client.ui.page.forum.rule

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import oolong.client.arch.LoadState
import oolong.client.repository.FORUM_ID
import oolong.client.repository.forumRepository
import oolong.client.repository.forumRuleData
import oolong.client.repository.forumRuleDetail
import oolong.client.testing.MainDispatcherRule
import oolong.client.ui.navigation.ForumRuleDetailKey
import oolong.core.network.forum.ForumRemoteDataSource
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
        key = ForumRuleDetailKey(FORUM_ID),
        forumRepository = forumRepository(remote),
    )

    @Test
    fun `loads the rules of the forum from its key`() = runTest {
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
