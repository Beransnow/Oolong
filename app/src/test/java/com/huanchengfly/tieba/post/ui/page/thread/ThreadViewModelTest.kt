package com.huanchengfly.tieba.post.ui.page.thread

import androidx.lifecycle.ViewModelStore
import com.huanchengfly.tieba.core.network.agree.AgreeRemoteDataSource
import com.huanchengfly.tieba.core.network.post.PostRemoteDataSource
import com.huanchengfly.tieba.core.network.thread.ThreadRemoteDataSource
import com.huanchengfly.tieba.post.api.models.protos.Anti
import com.huanchengfly.tieba.post.api.models.protos.PbContent
import com.huanchengfly.tieba.post.api.models.protos.PollInfo
import com.huanchengfly.tieba.post.api.models.protos.PollOption
import com.huanchengfly.tieba.post.api.models.protos.SimpleForum
import com.huanchengfly.tieba.post.api.models.protos.ThreadInfo
import com.huanchengfly.tieba.post.api.models.protos.pbPage.PbPageResponseData
import com.huanchengfly.tieba.post.arch.LoadStatus
import com.huanchengfly.tieba.post.arch.MessageController
import com.huanchengfly.tieba.post.model.Post
import com.huanchengfly.tieba.post.model.ThreadDetail
import com.huanchengfly.tieba.post.repository.BlockRuleSource
import com.huanchengfly.tieba.post.repository.BlockRules
import com.huanchengfly.tieba.post.repository.ThreadHistory
import com.huanchengfly.tieba.post.repository.ThreadRepository
import com.huanchengfly.tieba.post.repository.ThreadSort
import com.huanchengfly.tieba.post.repository.toThreadSummary
import com.huanchengfly.tieba.post.testing.MainDispatcherRule
import com.huanchengfly.tieba.post.ui.navigation.ThreadKey
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.huanchengfly.tieba.post.api.models.protos.Agree as ProtoAgree
import com.huanchengfly.tieba.post.api.models.protos.Page as ProtoPage
import com.huanchengfly.tieba.post.api.models.protos.Post as ProtoPost
import com.huanchengfly.tieba.post.api.models.protos.User as ProtoUser

class ThreadViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val remote = mockk<ThreadRemoteDataSource>(relaxed = true)
    private val agreeRemote = mockk<AgreeRemoteDataSource>(relaxed = true)
    private val postRemote = mockk<PostRemoteDataSource>(relaxed = true)
    private val blockRules = MutableStateFlow(BlockRules { _, _ -> false })
    private val saved = mutableListOf<Pair<ThreadDetail, Post?>>()
    private val shown = mutableListOf<Int>()

    private val lz = ProtoUser(id = 10, name = "lz", nameShow = "楼主")
    private val me = ProtoUser(id = 99, name = "me", is_login = 1)

    private fun post(id: Long, author: ProtoUser = ProtoUser(id = 20, name = "replier"), text: String = "回复 $id") =
        ProtoPost(
            id = id,
            floor = id.toInt(),
            author = author,
            author_id = author.id,
            content = listOf(PbContent(type = 0, text = text)),
            agree = ProtoAgree(diffAgreeNum = 5, hasAgree = 0),
        )

    private fun data(posts: List<ProtoPost>, page: ProtoPage = ProtoPage(current_page = 1, total_page = 3, new_total_page = 3, has_more = 1)) =
        PbPageResponseData(
            thread = ThreadInfo(id = 9_000, title = "标题", author = lz, firstPostId = 1),
            forum = SimpleForum(id = 52, name = "吧名"),
            page = page,
            anti = Anti(tbs = "page-tbs"),
            user = me,
            post_list = listOf(post(1, lz)) + posts,
        )

    private fun respond(vararg pages: PbPageResponseData) {
        coEvery { remote.page(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returnsMany pages.toList()
    }

    private fun TestScope.createViewModel(
        postId: Long = 0,
        sortType: Int = 0,
        threadInfo: ThreadInfo? = null,
        appScope: CoroutineScope = backgroundScope,
    ): ThreadViewModel {
        val dispatcher = mainDispatcherRule.dispatcher
        threadInfo?.let { ThreadPreviewCache.put(it.toThreadSummary { _, _ -> }) }
        return ThreadViewModel(
            args = ThreadKey(threadId = 9_000L, postId = postId, sortType = sortType),
            repository = ThreadRepository(remote, agreeRemote, postRemote, { _, _ -> }, dispatcher),
            blockRuleSource = object : BlockRuleSource {
                override val rules = blockRules
            },
            history = ThreadHistory { thread, _, post -> saved += thread to post },
            messages = object : MessageController {
                override fun show(message: Int, vararg args: Any) {
                    shown += message
                }
            },
            applicationScope = appScope,
        )
    }

    @Test
    fun `loads the thread from the navigation arguments`() = runTest {
        respond(data(listOf(post(2), post(3))))

        val viewModel = createViewModel()
        runCurrent()

        val state = viewModel.state.value
        assertEquals("标题", state.thread?.title)
        assertEquals(1L, state.firstPost?.id)
        assertEquals(listOf(2L, 3L), state.posts.items.map { it.post.id })
        assertEquals(99L, state.viewer.user?.id)
        assertEquals(1 to 3, state.currentPage to state.totalPage)
        coVerify { remote.page(9_000, 0, 0, 0, false, false, false, 0, 0, "", 0) }
    }

    @Test
    fun `an error can be retried from the same place`() = runTest {
        coEvery { remote.page(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } throws
            IOException("network") andThen data(listOf(post(31)))

        val viewModel = createViewModel(postId = 31)
        runCurrent()
        assertIs<LoadStatus.Error>(viewModel.state.value.posts.refresh)

        viewModel.retry()
        runCurrent()
        assertEquals(listOf(31L), viewModel.state.value.posts.items.map { it.post.id })
        coVerify(exactly = 2) { remote.page(9_000, 0, 31, any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `blocked posts follow the block rules as they change`() = runTest {
        respond(data(listOf(post(2, text = "广告"), post(3))))
        val viewModel = createViewModel()
        runCurrent()
        assertFalse(viewModel.state.value.posts.items[0].blocked)

        blockRules.value = BlockRules { _, content -> "广告" in content }
        runCurrent()
        assertEquals(listOf(true, false), viewModel.state.value.posts.items.map { it.blocked })
    }

    @Test
    fun `agreeing shows at once and is rolled back when it fails`() = runTest {
        respond(data(listOf(post(2))))
        val response = CompletableDeferred<Unit>()
        coEvery { agreeRemote.opAgree(any(), any(), any(), any()) } coAnswers {
            response.await()
            throw IOException("network")
        }
        val viewModel = createViewModel()
        runCurrent()

        viewModel.agreePost(2)
        runCurrent()
        val optimistic = viewModel.state.value.posts.items.single().post.agree
        assertEquals(6L to true, optimistic.count to optimistic.agreed)

        response.complete(Unit)
        runCurrent()
        val rolledBack = viewModel.state.value.posts.items.single().post.agree
        assertEquals(5L to false, rolledBack.count to rolledBack.agreed)
        assertEquals(1, shown.size)
    }

    @Test
    fun `a vote shows at once and is rolled back when it fails`() = runTest {
        val poll = PollInfo(options = listOf(PollOption(id = 1, num = 3, text = "甲"), PollOption(id = 2, num = 5, text = "乙")), total_num = 8)
        respond(data(listOf(post(2))).let { it.copy(thread = it.thread!!.copy(poll_info = poll)) })
        val response = CompletableDeferred<Unit>()
        coEvery { postRemote.addPollPost(any(), any(), any()) } coAnswers {
            response.await()
            throw IOException("network")
        }
        val viewModel = createViewModel()
        runCurrent()

        viewModel.vote(setOf(2))
        runCurrent()
        val voted = viewModel.state.value.thread?.poll!!
        assertEquals(listOf(3L, 6L), voted.options.map { it.votes })
        assertEquals(9L to setOf(2), voted.voters to voted.chosen)

        response.complete(Unit)
        runCurrent()
        val rolledBack = viewModel.state.value.thread?.poll!!
        assertEquals(listOf(3L, 5L), rolledBack.options.map { it.votes })
        assertEquals(false, rolledBack.hasVoted)
        assertEquals(1, shown.size)
    }

    @Test
    fun `seeing only the author's posts reloads from the start`() = runTest {
        respond(data(listOf(post(2))), data(listOf(post(5, lz))))
        val viewModel = createViewModel()
        runCurrent()

        viewModel.setSeeLz(true)
        runCurrent()

        assertTrue(viewModel.state.value.query.seeLz)
        assertEquals(listOf(5L), viewModel.state.value.posts.items.map { it.post.id })
        coVerify { remote.page(9_000, 0, 0, 0, true, false, false, 0, 0, "", 0) }
    }

    @Test
    fun `newest first loads newer posts when pulled down`() = runTest {
        respond(
            data(listOf(post(70), post(69)), ProtoPage(current_page = 2, total_page = 3, new_total_page = 3, has_more = 1)),
            data(listOf(post(71))),
        )
        val viewModel = createViewModel(sortType = ThreadSort.NewestFirst.value)
        runCurrent()
        coVerify { remote.page(9_000, 0, 0, 1, false, false, false, 0, 0, "", 0) }

        viewModel.loadPrevious()
        runCurrent()

        coVerify { remote.page(9_000, 3, 70, 1, false, true, false, 0, 0, "", 0) }
        assertEquals(listOf(71L, 70L, 69L), viewModel.state.value.posts.items.map { it.post.id })
    }

    @Test
    fun `newer posts are asked for after the highest page seen`() = runTest {
        respond(
            data(listOf(post(1977), post(1976)), ProtoPage(current_page = 1, total_page = 123, has_more = 1)),
            data(listOf(post(1963), post(1962)), ProtoPage(current_page = 121, total_page = 123, has_more = 1)),
            data(emptyList()),
        )
        val viewModel = createViewModel(sortType = ThreadSort.NewestFirst.value)
        runCurrent()
        viewModel.loadMore()
        runCurrent()

        viewModel.loadPrevious()
        runCurrent()

        coVerify { remote.page(9_000, 122, 1977, 1, false, true, false, 0, 0, "", 0) }
    }

    @Test
    fun `opening at a post scrolls to it once loaded`() = runTest {
        respond(data(listOf(post(31))))
        val viewModel = createViewModel(postId = 31)
        val effects = mutableListOf<ThreadEffect>()
        backgroundScope.launchCollect(viewModel, effects)

        runCurrent()
        assertEquals(listOf<ThreadEffect>(ThreadEffect.ScrollToPost(31)), effects)
    }

    @Test
    fun `a reply at the end of the thread is appended and scrolled to`() = runTest {
        respond(
            data(listOf(post(2), post(3)), ProtoPage(current_page = 1, total_page = 1, has_more = 0)),
            data(listOf(post(3), post(4))),
        )
        val viewModel = createViewModel()
        val effects = mutableListOf<ThreadEffect>()
        backgroundScope.launchCollect(viewModel, effects)
        runCurrent()

        viewModel.onReplySent(newPostId = 4)
        runCurrent()

        assertEquals(listOf(2L, 3L, 4L), viewModel.state.value.posts.items.map { it.post.id })
        assertEquals(listOf<ThreadEffect>(ThreadEffect.ScrollToPost(4)), effects)
        coVerify { remote.page(9_000, 0, 4, 0, false, false, false, 0, 0, "", 0) }
    }

    @Test
    fun `a reply far from the loaded pages jumps to it`() = runTest {
        respond(data(listOf(post(2), post(3))), data(listOf(post(400))))
        val viewModel = createViewModel()
        val effects = mutableListOf<ThreadEffect>()
        backgroundScope.launchCollect(viewModel, effects)
        runCurrent()

        viewModel.onReplySent(newPostId = 400)
        runCurrent()

        assertEquals(listOf(400L), viewModel.state.value.posts.items.map { it.post.id })
        assertEquals(listOf<ThreadEffect>(ThreadEffect.ScrollToPost(400)), effects)
    }

    @Test
    fun `a reply inside a post updates that post where it is`() = runTest {
        val replied = post(3).copy(sub_post_number = 1)
        respond(data(listOf(post(2), post(3))), data(listOf(replied)))
        val viewModel = createViewModel()
        runCurrent()

        viewModel.onReplySent(newPostId = 301, inPost = 3)
        runCurrent()

        assertEquals(listOf(2L, 3L), viewModel.state.value.posts.items.map { it.post.id })
        assertEquals(1, viewModel.state.value.posts.items[1].post.subPostCount)
    }

    @Test
    fun `a reply sent while a jump loads only scrolls to the reply`() = runTest {
        val jumpPage = CompletableDeferred<Unit>()
        var calls = 0
        coEvery { remote.page(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } coAnswers {
            when (++calls) {
                1 -> data(listOf(post(2), post(3)))
                2 -> {
                    jumpPage.await()
                    data(listOf(post(31)))
                }
                else -> data(listOf(post(400)))
            }
        }
        val viewModel = createViewModel()
        val effects = mutableListOf<ThreadEffect>()
        backgroundScope.launchCollect(viewModel, effects)
        runCurrent()

        viewModel.jumpToPage(3)
        runCurrent()
        viewModel.onReplySent(newPostId = 400)
        runCurrent()

        assertEquals(listOf<ThreadEffect>(ThreadEffect.ScrollToPost(400)), effects)
    }

    @Test
    fun `leaving immersive reading shows all posts again`() = runTest {
        respond(data(listOf(post(2))), data(listOf(post(5, lz))), data(listOf(post(2))))
        val viewModel = createViewModel()
        runCurrent()

        viewModel.toggleImmersive()
        runCurrent()
        assertTrue(viewModel.state.value.immersive)
        assertTrue(viewModel.state.value.query.seeLz)

        viewModel.toggleImmersive()
        runCurrent()
        assertFalse(viewModel.state.value.immersive)
        assertFalse(viewModel.state.value.query.seeLz)
    }

    @Test
    fun `jumping to a page is only possible oldest first`() = runTest {
        respond(data(listOf(post(70))), data(listOf(post(31))))
        val viewModel = createViewModel(sortType = ThreadSort.NewestFirst.value)
        runCurrent()

        viewModel.jumpToPage(3)
        runCurrent()
        coVerify(exactly = 1) { remote.page(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `the thread passed along is shown until the thread loads`() = runTest {
        val gate = CompletableDeferred<Unit>()
        coEvery { remote.page(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } coAnswers {
            gate.await()
            data(listOf(post(2)))
        }
        val passed = ThreadInfo(
            threadId = 9_000,
            title = "列表里的标题",
            author = lz,
            firstPostContent = listOf(PbContent(type = 0, text = "摘要")),
        )
        val viewModel = createViewModel(threadInfo = passed)
        runCurrent()
        assertNull(ThreadPreviewCache.take(9_000), "the page takes the thread it was handed")

        assertEquals("列表里的标题", viewModel.state.value.preview?.title)
        assertEquals(10L, viewModel.state.value.preview?.author?.id)

        gate.complete(Unit)
        runCurrent()
        assertNull(viewModel.state.value.preview)
        assertEquals("标题", viewModel.state.value.thread?.title)
    }

    @Test
    fun `history remembers the last post read once scrolling settles`() = runTest {
        respond(data(listOf(post(2), post(3))))
        val viewModel = createViewModel()
        runCurrent()

        viewModel.onReadUpTo(2)
        viewModel.onReadUpTo(3)
        advanceTimeBy(500)
        runCurrent()
        assertTrue(saved.isEmpty())

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(3L, saved.single().second?.id)
    }

    @Test
    fun `deleting a post removes it from the list`() = runTest {
        respond(data(listOf(post(2, me), post(3))))
        val viewModel = createViewModel()
        runCurrent()

        viewModel.deletePost(viewModel.state.value.posts.items[0].post)
        runCurrent()

        assertEquals(listOf(3L), viewModel.state.value.posts.items.map { it.post.id })
        coVerify { remote.deletePost(52, "吧名", 9_000, 2, ownPost = true, isSubPost = false, tbs = "page-tbs") }
    }

    @Test
    fun `the store mark is moved on leaving even though the page closes`() = runTest {
        val thread = data(listOf(post(2), post(3))).let {
            it.copy(thread = it.thread!!.copy(collectStatus = 1, collectMarkPid = "2"))
        }
        respond(thread)
        val viewModel = createViewModel()
        val effects = mutableListOf<ThreadEffect>()
        backgroundScope.launchCollect(viewModel, effects)
        runCurrent()

        viewModel.onReadUpTo(3)
        val update = viewModel.storeMarkUpdate()
        assertEquals(3L, update?.id)
        viewModel.leaveUpdatingStoreMark(update!!)
        viewModel.clear()
        runCurrent()

        assertEquals(listOf<ThreadEffect>(ThreadEffect.NavigateUp), effects)
        coVerify { remote.addStore(9_000, 3) }
    }

    @Test
    fun `no store mark update is offered when the mark is already there`() = runTest {
        val thread = data(listOf(post(2))).let {
            it.copy(thread = it.thread!!.copy(collectStatus = 1, collectMarkPid = "2"))
        }
        respond(thread)
        val viewModel = createViewModel()
        runCurrent()

        viewModel.onReadUpTo(2)
        assertNull(viewModel.storeMarkUpdate())
    }
}

private fun CoroutineScope.launchCollect(viewModel: ThreadViewModel, into: MutableList<ThreadEffect>) {
    launch(start = CoroutineStart.UNDISPATCHED) {
        viewModel.effect.collect { into += it }
    }
}

/** Clears the view model the way leaving the page does. */
private fun ThreadViewModel.clear() {
    ViewModelStore().apply { put("thread", this@clear) }.clear()
}
