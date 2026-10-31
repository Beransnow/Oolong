package com.huanchengfly.tieba.post.model

import androidx.compose.runtime.Immutable
import com.huanchengfly.tieba.post.api.models.protos.OriginThreadInfo
import com.huanchengfly.tieba.post.arch.ImmutableHolder
import kotlinx.collections.immutable.ImmutableList

@Immutable
data class User(
    val id: Long,
    /** Account name, may be empty. */
    val name: String,
    /** Display name, empty when it is the account name. */
    val nameShow: String,
    val portrait: String,
    val level: Int = 0,
    val bawuType: BawuType? = null,
    /** Where the user posted from, as shown under their name. */
    val ipLocation: String = "",
) {
    val displayName: String get() = nameShow.ifEmpty { name }
}

enum class BawuType { Manager, Assist }

@Immutable
data class Agree(val count: Long, val agreed: Boolean) {
    fun toggled(): Agree = if (agreed) Agree(count - 1, false) else Agree(count + 1, true)
}

@Immutable
data class ForumSummary(val id: Long, val name: String, val avatar: String)

@Immutable
data class ThreadDetail(
    val id: Long,
    val title: String,
    val author: User,
    val forum: ForumSummary,
    val replyCount: Int,
    val agree: Agree,
    val firstPostId: Long,
    /** The post the thread store marks as read up to, or null when the thread isn't stored. */
    val storeMarkPostId: Long?,
    /** The shared thread, rendered by the feed card until feeds are migrated. */
    val origin: ImmutableHolder<OriginThreadInfo>? = null,
) {
    val isStored: Boolean get() = storeMarkPostId != null
}

@Immutable
data class Post(
    val id: Long,
    val floor: Int,
    /** Only the first floor has a title worth showing; empty otherwise. */
    val title: String,
    /** Seconds since the epoch. */
    val time: Long,
    val author: User,
    val content: ImmutableList<PostContent>,
    val agree: Agree,
    /** The first few replies inside the post. */
    val subPosts: ImmutableList<SubPost>,
    val subPostCount: Int,
)

@Immutable
data class SubPost(
    val id: Long,
    val author: User,
    val content: ImmutableList<PostContent>,
    val time: Long,
    val agree: Agree = Agree(0, agreed = false),
)

/** The logged-in user as the thread page sees them, or a guest. */
@Immutable
data class Viewer(val user: User?) {
    val isLoggedIn: Boolean get() = user != null
}
