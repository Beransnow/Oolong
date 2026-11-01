package com.huanchengfly.tieba.post.repository

import android.content.Context
import com.huanchengfly.tieba.core.network.post.PostRemoteDataSource
import com.huanchengfly.tieba.post.api.models.protos.ReplyExp
import com.huanchengfly.tieba.post.api.models.protos.addPost.AddPostRequestData
import com.huanchengfly.tieba.post.api.models.protos.addPost.AddPostResponseData
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PostRepositoryTest {
    private val remote = mockk<PostRemoteDataSource>()
    private val repository = PostRepository(remote, mockk(), { "昵称" }, mockk<Context>(), Dispatchers.Unconfined)
    private val sent = slot<AddPostRequestData>()

    init {
        coEvery { remote.addPost(capture(sent), any()) } returns AddPostResponseData(tid = "9", pid = "100", exp = ReplyExp(inc = "2"))
    }

    @Test
    fun `replies to a thread`() = runTest {
        val reply = repository.reply(ReplyTarget(52, "测试", 9), "内容", tbs = "t")

        assertEquals(PostedReply(9, 100, "2"), reply)
        with(sent.captured) {
            assertEquals("内容", content)
            assertEquals("52", fid)
            assertEquals("测试", kw)
            assertEquals("9", tid)
            assertEquals("昵称", name_show)
            assertEquals("13", post_from)
            assertEquals("0", barrage_time)
            assertNull(quote_id)
            assertNull(reply_uid)
        }
    }

    @Test
    fun `replies inside a floor to someone`() = runTest {
        repository.reply(ReplyTarget(52, "测试", 9, postId = 50, subPostId = 60, replyUserId = 7), "内容", tbs = null)

        with(sent.captured) {
            assertEquals("50", quote_id)
            assertEquals("50", repostid)
            assertEquals("60", sub_post_id)
            assertEquals("7", reply_uid)
            assertNull(post_from)
            assertNull(barrage_time)
            assertNull(v_fid)
        }
    }

    @Test
    fun `replies to a floor`() = runTest {
        repository.reply(ReplyTarget(52, "测试", 9, postId = 50), "内容", tbs = null)

        assertEquals("0", sent.captured.post_from)
    }
}
