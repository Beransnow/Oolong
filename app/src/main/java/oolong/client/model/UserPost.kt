package oolong.client.model

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList

/** Something a user posted, as their profile lists it: a thread they started, or their replies in one. */
@Immutable
sealed interface UserPost {
    /** Tells the posts of a list apart. */
    val key: String
}

/** A thread the user started, shown as lists show threads. */
@Immutable
data class UserThread(val thread: ThreadSummary) : UserPost {
    override val key: String get() = "${thread.id}_${thread.firstPostId}"
}

/** The user's replies in someone's thread, under that thread's title. */
@Immutable
data class UserReplies(
    val threadId: Long,
    val postId: Long,
    val threadTitle: String,
    val author: User,
    val replies: ImmutableList<UserReply>,
) : UserPost {
    override val key: String get() = "${threadId}_$postId"
}

@Immutable
data class UserReply(
    val text: String,
    /** Seconds since the epoch. */
    val time: Long,
    val postId: Long,
    /** Whether it is a reply inside a floor rather than a floor of its own. */
    val isSubPost: Boolean,
)
