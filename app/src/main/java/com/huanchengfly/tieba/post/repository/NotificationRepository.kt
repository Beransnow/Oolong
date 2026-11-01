package com.huanchengfly.tieba.post.repository

import com.huanchengfly.tieba.core.network.message.FeedItem
import com.huanchengfly.tieba.core.network.message.MessageRemoteDataSource
import com.huanchengfly.tieba.post.arch.IoDispatcher
import com.huanchengfly.tieba.post.arch.Page
import com.huanchengfly.tieba.post.model.Notification
import com.huanchengfly.tieba.post.model.NotificationType
import com.huanchengfly.tieba.post.model.User
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** The replies to the user and the posts that mention them, a page at a time. */
class NotificationRepository @Inject constructor(
    private val remote: MessageRemoteDataSource,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    /** Page [page] (from 1) of [type]; the next page's number comes with it while there are more. */
    suspend fun page(type: NotificationType, page: Int): Page<Int, Notification> = withContext(io) {
        val response = when (type) {
            NotificationType.ReplyMe -> remote.replyMe(page)
            NotificationType.AtMe -> remote.atMe(page)
        }
        val items = when (type) {
            NotificationType.ReplyMe -> response.replyList
            NotificationType.AtMe -> response.atList
        }
        Page(items.mapNotNull { it.toNotification() }, next = (page + 1).takeIf { response.page?.hasMore == "1" })
    }
}

private fun FeedItem.toNotification(): Notification? {
    val threadId = threadId?.toLongOrNull() ?: return null
    val postId = postId?.toLongOrNull() ?: return null
    return Notification(
        threadId = threadId,
        postId = postId,
        inPost = isFloor == "1",
        threadTitle = title.orEmpty(),
        content = content.orEmpty(),
        quoteContent = quoteContent,
        quotePostId = quotePid?.toLongOrNull()?.takeIf { it != 0L },
        author = replyer?.let {
            User(
                id = it.id?.toLongOrNull() ?: 0,
                name = it.name.orEmpty(),
                nameShow = it.nameShow.orEmpty().takeIf { name -> name != it.name }.orEmpty(),
                portrait = it.portrait.orEmpty(),
            )
        },
        time = time?.toLongOrNull() ?: 0,
    )
}
