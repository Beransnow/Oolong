package oolong.client.ui.page.search

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import oolong.client.arch.LoadStatus
import oolong.client.arch.MessageController
import oolong.client.arch.Page
import oolong.client.model.SearchAuthor
import oolong.client.model.SearchMatches
import oolong.client.model.SearchOrder
import oolong.client.model.SearchedForum
import oolong.client.model.SearchedPost
import oolong.client.repository.BlockRuleSource
import oolong.client.repository.BlockRules
import oolong.client.repository.SearchRepository
import oolong.client.testing.MainDispatcherRule
import org.junit.Rule
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = mockk<SearchRepository>(relaxed = true) {
        every { history } returns flowOf(emptyList())
    }

    private fun forum(name: String) = SearchedForum(1, name, name, "", "")

    private fun post(id: Long) = SearchedPost(
        threadId = id, postId = 0, title = "t$id", content = "", time = 0, author = SearchAuthor(0, "", "", ""),
        forumName = "", forumAvatar = "", replyCount = 0, agreeCount = 0, shareCount = 0,
    )

    private fun createViewModel() = SearchViewModel(repository, mockk<MessageController>(relaxed = true), object : BlockRuleSource {
        override val rules = MutableStateFlow(BlockRules { null })
    })

    @Test
    fun `each tab loads once per keyword, when it is first shown`() = runTest {
        coEvery { repository.forums("a") } returns SearchMatches(forum("a"))
        coEvery { repository.forums("b") } returns SearchMatches(forum("b"))
        val viewModel = createViewModel()

        viewModel.search(" a ")
        viewModel.open(SearchTab.Forums)
        viewModel.open(SearchTab.Forums)
        runCurrent()
        assertEquals("a", viewModel.state.value.keyword)
        assertEquals("a", viewModel.state.value.forums.matches.exact?.name)

        viewModel.search("b")
        runCurrent()
        assertEquals(LoadStatus.Loading, viewModel.state.value.forums.load)
        viewModel.open(SearchTab.Forums)
        runCurrent()

        assertEquals("b", viewModel.state.value.forums.matches.exact?.name)
        coVerify(exactly = 1) { repository.forums("a") }
        coVerify(exactly = 0) { repository.users(any()) }
        coVerify { repository.remember("a") }
    }

    @Test
    fun `changing the order reloads the threads in it`() = runTest {
        coEvery { repository.threads("k", SearchOrder.Newest, 1) } returns Page(listOf(post(1)), next = 2)
        coEvery { repository.threads("k", SearchOrder.Relevant, 1) } returns Page(listOf(post(2)))
        val viewModel = createViewModel()
        viewModel.search("k")
        viewModel.open(SearchTab.Threads)
        runCurrent()

        viewModel.setOrder(SearchOrder.Relevant)
        runCurrent()

        assertEquals(SearchOrder.Relevant, viewModel.state.value.order)
        assertEquals(listOf(2L), viewModel.state.value.threads.items.map { it.threadId })
    }

    @Test
    fun `a failed reload keeps what was found`() = runTest {
        coEvery { repository.forums("k") } returns SearchMatches(forum("k")) andThenThrows IOException()
        val viewModel = createViewModel()
        viewModel.search("k")
        viewModel.open(SearchTab.Forums)
        runCurrent()

        viewModel.refresh(SearchTab.Forums)
        runCurrent()

        val forums = viewModel.state.value.forums
        assertEquals("k", forums.matches.exact?.name)
        assertEquals(true, forums.load is LoadStatus.Error)
    }

    @Test
    fun `suggestions come once the user pauses typing`() = runTest {
        coEvery { repository.suggestions("ab") } returns listOf("abc")
        val viewModel = createViewModel()

        viewModel.onInputChange("a")
        advanceTimeBy(100)
        viewModel.onInputChange("ab")
        advanceTimeBy(600)
        runCurrent()

        assertEquals(persistentListOf("abc"), viewModel.state.value.suggestions)
        coVerify(exactly = 0) { repository.suggestions("a") }
    }
}
