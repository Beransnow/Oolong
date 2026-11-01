package com.huanchengfly.tieba.post.model

import androidx.compose.runtime.Immutable

enum class NotificationType { ReplyMe, AtMe }

/** A reply to the user, or a post that mentions them. */
@Immutable
data class Notification(
    val threadId: Long,
    /** The reply or mention itself. */
    val postId: Long,
    /** Whether it is a reply inside a post rather than a post in the thread. */
    val inPost: Boolean,
    val threadTitle: String,
    val content: String,
    /** What it replies to, when that is inside a post. */
    val quoteContent: String? = null,
    /** The post or the reply inside a post it answers. */
    val quotePostId: Long? = null,
    val author: User? = null,
    /** Unix time in seconds. */
    val time: Long = 0,
)
