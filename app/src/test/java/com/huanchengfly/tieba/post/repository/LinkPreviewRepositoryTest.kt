package com.huanchengfly.tieba.post.repository

import com.huanchengfly.tieba.core.network.forum.ForumRemoteDataSource
import com.huanchengfly.tieba.core.network.thread.ThreadRemoteDataSource
import com.huanchengfly.tieba.post.api.models.protos.SimpleForum
import com.huanchengfly.tieba.post.api.models.protos.ThreadInfo
import com.huanchengfly.tieba.post.api.models.protos.User
import com.huanchengfly.tieba.post.api.models.protos.frsPage.ForumInfo
import com.huanchengfly.tieba.post.api.models.protos.frsPage.FrsPageResponseData
import com.huanchengfly.tieba.post.api.models.protos.pbPage.PbPageResponseData
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LinkPreviewRepositoryTest {
    private val threadRemote = mockk<ThreadRemoteDataSource>()
    private val forumRemote = mockk<ForumRemoteDataSource>()
    private val repository = LinkPreviewRepository(threadRemote, forumRemote, Dispatchers.Unconfined)

    @Test
    fun `previews a thread with its author from the user list`() = runTest {
        coEvery { threadRemote.page(9, page = 1) } returns PbPageResponseData(
            thread = ThreadInfo(title = "标题", replyNum = 12, author = User(id = 7)),
            forum = SimpleForum(name = "测试"),
            user_list = listOf(User(id = 7, portrait = "p")),
        )

        assertEquals(ThreadLinkPreview("标题", "测试", 12, "p"), repository.thread(9))
    }

    @Test
    fun `previews a forum`() = runTest {
        coEvery { forumRemote.page("测试", 1, 1, null) } returns FrsPageResponseData(forum = ForumInfo(slogan = "标语", avatar = "a"))

        assertEquals(ForumLinkPreview("标语", "a"), repository.forum("测试"))
    }
}
