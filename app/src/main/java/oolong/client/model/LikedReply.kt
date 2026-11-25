package oolong.client.model

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList

/** A reply the user liked, as their likes list it, with the thread it was made in. */
@Immutable
data class LikedReply(
    val postId: Long,
    /** The post it is a reply inside; null for a post of its own. */
    val floorId: Long?,
    val author: User,
    /** When it was made, in seconds since the epoch. */
    val time: Long,
    val content: ImmutableList<PostContent>,
    /** Its likes: liked, until the user takes that back from the list. */
    val agree: Agree,
    val threadId: Long,
    /** The thread's title, or what its first floor starts with when it has none. */
    val threadTitle: String,
    val forum: ForumSummary,
)
