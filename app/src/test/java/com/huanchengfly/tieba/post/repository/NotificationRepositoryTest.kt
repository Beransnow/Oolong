package com.huanchengfly.tieba.post.repository

import com.huanchengfly.tieba.core.network.message.FeedItem
import com.huanchengfly.tieba.core.network.message.FeedResponse
import com.huanchengfly.tieba.core.network.message.MessageRemoteDataSource
import com.huanchengfly.tieba.post.model.Notification
import com.huanchengfly.tieba.post.model.NotificationType
import com.huanchengfly.tieba.post.model.User
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NotificationRepositoryTest {
    private val remote = mockk<MessageRemoteDataSource>()
    private val repository = NotificationRepository(remote, Dispatchers.Unconfined)

    @Test
    fun `maps a page of replies and says whether more follow`() = runTest {
        coEvery { remote.replyMe(2) } returns FeedResponse(
            replyList = listOf(
                FeedItem(
                    isFloor = "1", title = "标题", content = "回复", quoteContent = "原文",
                    replyer = FeedItem.User("7", "name", "昵称", "p"), threadId = "9000", postId = "201",
                    quotePid = "200", time = "1700000000",
                ),
                FeedItem(threadId = null, postId = "1"),
            ),
            page = FeedResponse.Page(hasMore = "1"),
        )

        val page = repository.page(NotificationType.ReplyMe, 2)

        assertEquals(
            listOf(
                Notification(
                    threadId = 9000, postId = 201, inPost = true, threadTitle = "标题", content = "回复",
                    quoteContent = "原文", quotePostId = 200, author = User(7, "name", "昵称", "p"), time = 1_700_000_000,
                ),
            ),
            page.items,
        )
        assertEquals(3, page.next)
    }

    @Test
    fun `mentions come from their own list and end when there are no more`() = runTest {
        coEvery { remote.atMe(1) } returns FeedResponse(
            atList = listOf(FeedItem(threadId = "9", postId = "10", replyer = FeedItem.User("7", "same", "same", ""))),
            page = FeedResponse.Page(hasMore = "0"),
        )

        val page = repository.page(NotificationType.AtMe, 1)

        assertEquals(listOf(10L), page.items.map { it.postId })
        assertEquals("", page.items.single().author?.nameShow)
        assertNull(page.next)
    }
}
