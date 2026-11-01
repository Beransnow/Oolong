package com.huanchengfly.tieba.post.ui.page.forum

import androidx.lifecycle.SavedStateHandle
import com.huanchengfly.tieba.core.network.agree.AgreeRemoteDataSource
import com.huanchengfly.tieba.core.network.forum.ForumRemoteDataSource
import com.huanchengfly.tieba.core.network.forum.LikeForumResponse
import com.huanchengfly.tieba.core.network.forum.LikeRemoteDataSource
import com.huanchengfly.tieba.core.network.identity.Session
import com.huanchengfly.tieba.core.network.sign.SignRemoteDataSource
import com.huanchengfly.tieba.core.network.sign.SignResponse
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.api.models.protos.Agree
import com.huanchengfly.tieba.post.api.models.protos.Anti
import com.huanchengfly.tieba.post.api.models.protos.FrsTabInfo
import com.huanchengfly.tieba.post.api.models.protos.Page
import com.huanchengfly.tieba.post.api.models.protos.ThreadInfo
import com.huanchengfly.tieba.post.api.models.protos.frsPage.ForumInfo
import com.huanchengfly.tieba.post.api.models.protos.frsPage.FrsPageResponseData
import com.huanchengfly.tieba.post.api.models.protos.frsPage.NavTabInfo
import com.huanchengfly.tieba.post.api.models.protos.generalTabList.GeneralTabListResponseData
import com.huanchengfly.tieba.post.arch.MessageController
import com.huanchengfly.tieba.post.model.ForumDetail
import com.huanchengfly.tieba.post.model.ForumSort
import com.huanchengfly.tieba.post.model.ForumTab
import com.huanchengfly.tieba.post.repository.BlockRuleSource
import com.huanchengfly.tieba.post.repository.BlockRules
import com.huanchengfly.tieba.post.repository.ForumSortStore
import com.huanchengfly.tieba.post.repository.forumRepository
import com.huanchengfly.tieba.post.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ForumViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val remote = mockk<ForumRemoteDataSource>()
    private val likeRemote = mockk<LikeRemoteDataSource>()
    private val signRemote = mockk<SignRemoteDataSource>()
    private val agreeRemote = mockk<AgreeRemoteDataSource>(relaxed = true)
    private val blockRules = MutableStateFlow(BlockRules { _, _ -> false })
    private val saved = mutableListOf<ForumDetail>()
    private val shown = mutableListOf<Int>()
    private val sorts = mutableMapOf<String, ForumSort>()

    private val custom = FrsTabInfo(tabId = 99, tabType = 15, tabName = "吧友互助", isGeneralTab = 1)

    private fun thread(id: Long, title: String = "帖子 $id", agreed: Boolean = false) =
        ThreadInfo(id = id, title = title, forumId = FORUM_ID, firstPostId = id * 10, agree = Agree(hasAgree = if (agreed) 1 else 0))

    private fun frsPage(threads: List<ThreadInfo> = listOf(thread(1)), liked: Boolean = true, signedIn: Boolean = false) =
        FrsPageResponseData(
            forum = ForumInfo(
                id = FORUM_ID,
                name = "吧名",
                is_like = if (liked) 1 else 0,
                sign_in_info = com.huanchengfly.tieba.post.api.models.protos.frsPage.SignInfo(
                    user_info = com.huanchengfly.tieba.post.api.models.protos.frsPage.SignUser(is_sign_in = if (signedIn) 1 else 0),
                ),
            ),
            page = Page(has_more = 0),
            thread_list = threads,
            anti = Anti(tbs = "page-tbs"),
            nav_tab_info = NavTabInfo(tab = listOf(custom)),
        )

    private fun respond(vararg pages: FrsPageResponseData) {
        coEvery { remote.page(any(), any(), any(), any(), any(), any()) } returnsMany pages.toList()
    }

    private fun TestScope.createViewModel(storedSort: ForumSort = ForumSort.ReplyTime) = ForumViewModel(
        savedState = SavedStateHandle(mapOf("forumName" to "吧名")),
        repository = forumRepository(remote, likeRemote, signRemote, agreeRemote = agreeRemote),
        blockRuleSource = object : BlockRuleSource {
            override val rules = blockRules
        },
        history = { saved += it },
        sortStore = object : ForumSortStore {
            override suspend fun sort(forumName: String) = sorts[forumName] ?: storedSort

            override suspend fun setSort(forumName: String, sort: ForumSort) {
                sorts[forumName] = sort
            }
        },
        messages = object : MessageController {
            override fun show(message: Int, vararg args: Any) {
                shown += message
            }
        },
        sessionProvider = { Session(uid = "99", name = "me", bduss = "b", stoken = null, tbs = null, zid = null) },
    )

    @Test
    fun `loads the forum with its latest threads in the stored order`() = runTest {
        respond(frsPage())

        val viewModel = createViewModel(storedSort = ForumSort.PostTime)
        runCurrent()

        val state = viewModel.state.value
        assertEquals("吧名", state.forum?.name)
        assertEquals(ForumSort.PostTime, state.sort)
        assertEquals(listOf(1L), state.list(ForumTab.Latest).items.map { it.thread.get { id } })
        assertEquals(
            listOf(ForumTab.Good, ForumTab.Hot, ForumTab.Latest, ForumTab.Custom(99, "吧友互助", 15)),
            state.tabs,
        )
        assertTrue(state.loggedIn)
        assertEquals(listOf("吧名"), saved.map { it.name })
        coVerify { remote.page("吧名", 1, 1, 3, false, 0) }
    }

    @Test
    fun `a tab loads the first time it is shown`() = runTest {
        respond(frsPage(), frsPage(threads = listOf(thread(2))))
        coEvery { remote.generalTabList(FORUM_ID, 99, "吧友互助", 15, 1, 0) } returns
            GeneralTabListResponseData(general_list = listOf(thread(3)))
        val viewModel = createViewModel()
        runCurrent()

        viewModel.selectTab(ForumTab.Hot)
        runCurrent()
        viewModel.selectTab(ForumTab.Custom(99, "吧友互助", 15))
        runCurrent()

        val state = viewModel.state.value
        assertEquals(listOf(2L), state.list(ForumTab.Hot).items.map { it.thread.get { id } })
        assertEquals(listOf(3L), state.list(ForumTab.Custom(99, "吧友互助", 15)).items.map { it.thread.get { id } })
        coVerify { remote.page("吧名", 1, 1, 4, false, 0) }
        assertEquals(listOf("吧名"), saved.map { it.name })
    }

    @Test
    fun `changing the order reloads the latest threads and remembers it`() = runTest {
        respond(frsPage(), frsPage(threads = listOf(thread(5))))
        val viewModel = createViewModel()
        runCurrent()

        viewModel.setSort(ForumSort.Smart)
        runCurrent()

        assertEquals(listOf(5L), viewModel.state.value.list(ForumTab.Latest).items.map { it.thread.get { id } })
        assertEquals(ForumSort.Smart, sorts["吧名"])
        coVerify { remote.page("吧名", 1, 1, 1, false, 0) }
    }

    @Test
    fun `choosing a class reloads the good threads of it`() = runTest {
        respond(frsPage())
        val viewModel = createViewModel()
        runCurrent()
        viewModel.selectTab(ForumTab.Good)
        runCurrent()

        viewModel.setGoodClass(3)
        runCurrent()

        assertEquals(3, viewModel.state.value.goodClassId)
        coVerify { remote.page("吧名", 1, 1, -1, true, 0) }
        coVerify { remote.page("吧名", 1, 1, -1, true, 3) }
    }

    @Test
    fun `the button follows the forum, then signs in to it`() = runTest {
        respond(frsPage(liked = false))
        coEvery { likeRemote.like(FORUM_ID, "吧名", false, "page-tbs") } returns
            LikeForumResponse(info = LikeForumResponse.Info(memberSum = "11"))
        coEvery { signRemote.sign(FORUM_ID, "吧名", "page-tbs") } returns
            SignResponse(userInfo = SignResponse.UserInfo(isSignIn = "1", contSignNum = "1", signBonusPoint = "8", userSignRank = "3"))
        val viewModel = createViewModel()
        runCurrent()

        viewModel.followOrSignIn()
        runCurrent()
        assertTrue(viewModel.state.value.forum!!.liked)

        viewModel.followOrSignIn()
        runCurrent()
        assertTrue(viewModel.state.value.forum!!.signedIn)
        assertEquals(listOf(R.string.toast_like_success, R.string.toast_sign_success), shown)
    }

    @Test
    fun `agreeing shows at once and is rolled back when it fails`() = runTest {
        respond(frsPage())
        val response = CompletableDeferred<Unit>()
        coEvery { agreeRemote.opAgree(any(), any(), any(), any()) } coAnswers {
            response.await()
            throw IOException("network")
        }
        val viewModel = createViewModel()
        runCurrent()

        viewModel.agree(ForumTab.Latest, 1)
        runCurrent()
        assertEquals(1, viewModel.state.value.list(ForumTab.Latest).items.single().thread.get { agree?.hasAgree })

        response.complete(Unit)
        runCurrent()
        assertEquals(0, viewModel.state.value.list(ForumTab.Latest).items.single().thread.get { agree?.hasAgree })
        assertEquals(listOf(R.string.toast_agree_failed), shown)
    }

    @Test
    fun `blocked threads follow the block rules`() = runTest {
        respond(frsPage(threads = listOf(thread(1, title = "广告"), thread(2))))
        val viewModel = createViewModel()
        runCurrent()

        blockRules.value = BlockRules { _, content -> "广告" in content }
        runCurrent()

        assertEquals(listOf(true, false), viewModel.state.value.list(ForumTab.Latest).items.map { it.blocked })
    }

    private companion object {
        const val FORUM_ID = 52L
    }
}
