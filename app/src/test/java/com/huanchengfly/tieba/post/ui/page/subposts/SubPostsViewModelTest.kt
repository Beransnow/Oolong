package com.huanchengfly.tieba.post.ui.page.subposts

import com.huanchengfly.tieba.core.network.agree.AgreeRemoteDataSource
import com.huanchengfly.tieba.core.network.identity.Session
import com.huanchengfly.tieba.core.network.thread.ThreadRemoteDataSource
import com.huanchengfly.tieba.post.api.models.protos.Anti
import com.huanchengfly.tieba.post.api.models.protos.PbContent
import com.huanchengfly.tieba.post.api.models.protos.SimpleForum
import com.huanchengfly.tieba.post.api.models.protos.SubPostList
import com.huanchengfly.tieba.post.api.models.protos.ThreadInfo
import com.huanchengfly.tieba.post.api.models.protos.pbFloor.PbFloorResponseData
import com.huanchengfly.tieba.post.arch.MessageController
import com.huanchengfly.tieba.post.repository.BlockRuleSource
import com.huanchengfly.tieba.post.repository.BlockRules
import com.huanchengfly.tieba.post.repository.ThreadRepository
import com.huanchengfly.tieba.post.testing.MainDispatcherRule
import com.huanchengfly.tieba.post.ui.navigation.SubPostsKey
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.huanchengfly.tieba.post.api.models.protos.Agree as ProtoAgree
import com.huanchengfly.tieba.post.api.models.protos.Page as ProtoPage
import com.huanchengfly.tieba.post.api.models.protos.Post as ProtoPost
import com.huanchengfly.tieba.post.api.models.protos.User as ProtoUser

class SubPostsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val remote = mockk<ThreadRemoteDataSource>(relaxed = true)
    private val agreeRemote = mockk<AgreeRemoteDataSource>(relaxed = true)
    private val blockRules = MutableStateFlow(BlockRules { _, _ -> false })
    private val shown = mutableListOf<Int>()

    private val lz = ProtoUser(id = 10, name = "lz")
    private val me = ProtoUser(id = 99, name = "me")

    private fun reply(id: Long, author: ProtoUser = ProtoUser(id = 20, name = "replier"), text: String = "回复 $id") =
        SubPostList(id = id, author = author, content = listOf(PbContent(type = 0, text = text)), agree = ProtoAgree(diffAgreeNum = 1))

    private fun floor(
        replies: List<SubPostList>,
        page: ProtoPage = ProtoPage(current_page = 1, total_page = 2, total_count = 12),
        post: ProtoPost = ProtoPost(id = 77, floor = 5, author = me),
    ) = PbFloorResponseData(
        post = post,
        subpost_list = replies,
        page = page,
        thread = ThreadInfo(id = 9_000, author = lz),
        forum = SimpleForum(id = 52, name = "吧名"),
        anti = Anti(tbs = "page-tbs"),
    )

    private fun respond(vararg pages: PbFloorResponseData) {
        coEvery { remote.floor(any(), any(), any(), any(), any()) } returnsMany pages.toList()
    }

    private fun TestScope.createViewModel(postId: Long = 77, subPostId: Long = 0, loadFromSubPost: Boolean = false) =
        SubPostsViewModel(
            key = SubPostsKey(threadId = 9_000L, postId = postId, subPostId = subPostId, loadFromSubPost = loadFromSubPost),
            repository = ThreadRepository(remote, agreeRemote, mockk(), { _, _ -> }, mainDispatcherRule.dispatcher),
            blockRuleSource = object : BlockRuleSource {
                override val rules = blockRules
            },
            messages = object : MessageController {
                override fun show(message: Int, vararg args: Any) {
                    shown += message
                }
            },
            sessionProvider = { Session(uid = "99", name = "me", bduss = "b", stoken = null, tbs = null, zid = null) },
        )

    private fun CoroutineScope.collectEffects(viewModel: SubPostsViewModel) = mutableListOf<SubPostsEffect>().also { into ->
        launch(start = CoroutineStart.UNDISPATCHED) { viewModel.effect.collect { into += it } }
    }

    @Test
    fun `loads the post and its first page of replies`() = runTest {
        respond(floor(listOf(reply(201), reply(202))))

        val viewModel = createViewModel()
        runCurrent()

        val state = viewModel.state.value
        assertEquals(77L, state.post?.id)
        assertEquals(10L, state.threadAuthorId)
        assertEquals(listOf(201L, 202L), state.subPosts.items.map { it.subPost.id })
        assertEquals(12, state.totalCount)
        assertEquals(99L, state.viewerId)
        coVerify { remote.floor(9_000, 77, 0, 1, 0) }
    }

    @Test
    fun `opened from a reply it loads around it and then uses the ids the server returned`() = runTest {
        respond(
            floor(listOf(reply(301), reply(302)), ProtoPage(current_page = 3, total_page = 4)),
            floor(listOf(reply(303))),
        )
        val viewModel = createViewModel(postId = 302, subPostId = 302, loadFromSubPost = true)
        val effects = backgroundScope.collectEffects(viewModel)
        runCurrent()

        assertEquals(listOf<SubPostsEffect>(SubPostsEffect.ScrollToSubPost(302)), effects)
        viewModel.loadMore()
        runCurrent()

        coVerify { remote.floor(9_000, 302, 0, 1, 302) }
        coVerify { remote.floor(9_000, 77, 52, 4, 0) }
        assertTrue(viewModel.state.value.subPosts.hasPrevious)
    }

    @Test
    fun `liking a reply shows at once and is rolled back when it fails`() = runTest {
        respond(floor(listOf(reply(201))))
        val response = CompletableDeferred<Unit>()
        coEvery { agreeRemote.opAgree(any(), any(), any(), any()) } coAnswers {
            response.await()
            throw IOException("network")
        }
        val viewModel = createViewModel()
        runCurrent()

        viewModel.agreeSubPost(201)
        runCurrent()
        assertEquals(true, viewModel.state.value.subPosts.items.single().subPost.agree.agreed)

        response.complete(Unit)
        runCurrent()
        assertEquals(false, viewModel.state.value.subPosts.items.single().subPost.agree.agreed)
        assertEquals(1, shown.size)
    }

    @Test
    fun `deleting a reply removes it, deleting the post closes the page`() = runTest {
        respond(floor(listOf(reply(201, me), reply(202))))
        val viewModel = createViewModel()
        val effects = backgroundScope.collectEffects(viewModel)
        runCurrent()

        viewModel.delete(viewModel.state.value.subPosts.items[0].subPost)
        runCurrent()
        assertEquals(listOf(202L), viewModel.state.value.subPosts.items.map { it.subPost.id })
        coVerify { remote.deletePost(52, "吧名", 9_000, 201, ownPost = true, isSubPost = true, tbs = "page-tbs") }

        viewModel.delete(null)
        runCurrent()
        coVerify { remote.deletePost(52, "吧名", 9_000, 77, ownPost = true, isSubPost = false, tbs = "page-tbs") }
        assertEquals(listOf<SubPostsEffect>(SubPostsEffect.NavigateUp), effects)
    }

    @Test
    fun `a reply sent inside the post is loaded and scrolled to`() = runTest {
        respond(floor(listOf(reply(201))), floor(listOf(reply(201), reply(250))))
        val viewModel = createViewModel()
        val effects = backgroundScope.collectEffects(viewModel)
        runCurrent()

        viewModel.onReplySent(newSubPostId = 250)
        runCurrent()

        coVerify { remote.floor(9_000, 77, 52, 1, 250) }
        assertEquals(listOf(201L, 250L), viewModel.state.value.subPosts.items.map { it.subPost.id })
        assertEquals(listOf<SubPostsEffect>(SubPostsEffect.ScrollToSubPost(250)), effects)
    }

    @Test
    fun `blocked replies follow the block rules`() = runTest {
        respond(floor(listOf(reply(201, text = "广告"), reply(202))))
        val viewModel = createViewModel()
        runCurrent()

        blockRules.value = BlockRules { _, content -> "广告" in content }
        runCurrent()
        assertEquals(listOf(true, false), viewModel.state.value.subPosts.items.map { it.blocked })
    }
}
