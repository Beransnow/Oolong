package oolong.client.ui.main.explore

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import oolong.client.arch.LoadStatus
import oolong.client.arch.MessageController
import oolong.client.data.block.BlockRuleSource
import oolong.client.data.block.BlockRules
import oolong.client.data.explore.ExploreRepository
import oolong.client.data.forum.ForumRepository
import oolong.client.data.thread.toThreadSummary
import oolong.client.model.ForumMeasure
import oolong.client.model.ForumRanking
import oolong.client.model.ForumSummary
import oolong.client.model.RankReason
import oolong.client.model.RankedForum
import oolong.client.model.RankedThread
import oolong.client.model.ThreadMeasure
import oolong.client.testing.MainDispatcherRule
import oolong.client.ui.main.explore.hot.HotViewModel
import oolong.core.network.protos.ThreadInfo
import org.junit.Rule
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

class HotViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = mockk<ExploreRepository>()
    private val forums = mockk<ForumRepository>(relaxed = true)

    private fun ranked(vararg ids: Long) = ids.mapIndexed { index, id ->
        RankedThread(index + 1, ThreadInfo(id = id, threadId = id).toThreadSummary { _, _ -> }, RankReason.Readers(id))
    }

    private val forum = RankedForum(1, ForumSummary(5, "f", ""), members = 10, liked = false, reason = RankReason.NewFollowers(3))

    private fun TestScope.createViewModel() = HotViewModel(
        repository = repository,
        forumRepository = forums,
        messages = mockk<MessageController>(relaxed = true),
        blockRuleSource = object : BlockRuleSource {
            override val rules = MutableStateFlow(BlockRules { null })
        },
    )

    private val HotViewModel.ids get() = state.value.threads.map { it.ranked.thread.id }

    private fun stubPage() {
        coEvery { repository.topics() } returns emptyList()
        coEvery { repository.threadRanking(ThreadMeasure.MostRead) } returns ranked(1, 2)
        coEvery { repository.forumRanking(ForumMeasure.MostFollowed) } returns ForumRanking("", persistentListOf(forum))
    }

    @Test
    fun `loads once when opened, and each measure once until refreshed`() = runTest {
        stubPage()
        coEvery { repository.threadRanking(ThreadMeasure.MostLiked) } returns ranked(3)
        val viewModel = createViewModel()

        viewModel.open()
        viewModel.open()
        runCurrent()
        viewModel.selectThreadMeasure(ThreadMeasure.MostLiked)
        runCurrent()
        assertEquals(listOf(3L), viewModel.ids)
        viewModel.selectThreadMeasure(ThreadMeasure.MostRead)
        viewModel.selectThreadMeasure(ThreadMeasure.MostLiked)
        runCurrent()

        assertEquals(LoadStatus.Idle, viewModel.state.value.load)
        coVerify(exactly = 1) { repository.threadRanking(ThreadMeasure.MostRead) }
        coVerify(exactly = 1) { repository.threadRanking(ThreadMeasure.MostLiked) }
    }

    @Test
    fun `a measure that fails to load leaves the last one picked`() = runTest {
        stubPage()
        coEvery { repository.threadRanking(ThreadMeasure.MostLiked) } throws IOException()
        val viewModel = createViewModel()
        viewModel.open()
        runCurrent()

        viewModel.selectThreadMeasure(ThreadMeasure.MostLiked)
        runCurrent()

        assertEquals(ThreadMeasure.MostRead, viewModel.state.value.threadMeasure)
        assertEquals(listOf(1L, 2L), viewModel.ids)
    }

    @Test
    fun `the page shows what loaded and fails only when nothing did`() = runTest {
        coEvery { repository.topics() } throws IOException()
        coEvery { repository.threadRanking(ThreadMeasure.MostRead) } returns ranked(1)
        coEvery { repository.forumRanking(ForumMeasure.MostFollowed) } throws IOException()
        val viewModel = createViewModel()

        viewModel.open()
        runCurrent()

        assertEquals(LoadStatus.Idle, viewModel.state.value.load)
        assertEquals(listOf(1L), viewModel.ids)
    }

    @Test
    fun `a forum followed shows followed, and not if following fails`() = runTest {
        stubPage()
        val viewModel = createViewModel()
        viewModel.open()
        runCurrent()

        viewModel.follow(forum)
        runCurrent()
        assertEquals(true, viewModel.state.value.forums!!.forums.single().liked)

        coEvery { forums.like(5, "f") } throws IOException()
        viewModel.refresh()
        runCurrent()
        viewModel.follow(forum)
        runCurrent()
        assertEquals(false, viewModel.state.value.forums!!.forums.single().liked)
    }
}
