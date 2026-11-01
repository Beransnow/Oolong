package com.huanchengfly.tieba.post.ui.page.user.post

import androidx.compose.runtime.Immutable
import com.huanchengfly.tieba.post.api.models.protos.PostInfoList
import com.huanchengfly.tieba.post.api.models.protos.abstractText
import com.huanchengfly.tieba.post.arch.ImmutableHolder
import com.huanchengfly.tieba.post.arch.wrapImmutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** A thread or the replies a user posted in one, as their profile lists it. */
@Immutable
data class PostListItemData(
    val data: ImmutableHolder<PostInfoList>,
    val isThread: Boolean = data.get { is_thread } == 1,
    val contents: ImmutableList<PostContentData> = persistentListOf(),
)

@Immutable
data class PostContentData(
    val contentText: String,
    val createTime: Long,
    val postId: Long,
    val isSubPost: Boolean,
)

fun PostInfoList.toItemData() = PostListItemData(
    data = wrapImmutable(),
    contents = content.map {
        PostContentData(
            contentText = it.post_content.abstractText,
            createTime = it.create_time,
            postId = it.post_id,
            isSubPost = it.post_type == 1L,
        )
    }.toImmutableList(),
)
