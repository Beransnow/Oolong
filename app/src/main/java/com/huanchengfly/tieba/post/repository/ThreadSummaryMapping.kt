package com.huanchengfly.tieba.post.repository

import com.huanchengfly.tieba.post.api.models.protos.Media
import com.huanchengfly.tieba.post.api.models.protos.OriginThreadInfo
import com.huanchengfly.tieba.post.api.models.protos.PbContent
import com.huanchengfly.tieba.post.api.models.protos.PostInfoList
import com.huanchengfly.tieba.post.api.models.protos.ThreadInfo
import com.huanchengfly.tieba.post.api.models.protos.VideoInfo
import com.huanchengfly.tieba.post.api.models.protos.abstractText
import com.huanchengfly.tieba.post.model.Agree
import com.huanchengfly.tieba.post.model.ForumSummary
import com.huanchengfly.tieba.post.model.SharedThread
import com.huanchengfly.tieba.post.model.ThreadImage
import com.huanchengfly.tieba.post.model.ThreadSummary
import com.huanchengfly.tieba.post.model.ThreadVideo
import com.huanchengfly.tieba.post.model.User
import com.huanchengfly.tieba.post.model.UserPost
import com.huanchengfly.tieba.post.model.UserReplies
import com.huanchengfly.tieba.post.model.UserReply
import com.huanchengfly.tieba.post.model.UserThread
import kotlinx.collections.immutable.toImmutableList

/**
 * A thread as a list returned it. Feeds name the forum only in its forum info and forums only by
 * name; either way the summary has both.
 */
fun ThreadInfo.toThreadSummary(emoticons: EmoticonRegistry): ThreadSummary = ThreadSummary(
    id = threadId.takeIf { it != 0L } ?: id,
    forum = ForumSummary(
        id = forumInfo?.id ?: forumId,
        name = forumName.ifEmpty { forumInfo?.name.orEmpty() },
        avatar = forumInfo?.avatar.orEmpty(),
    ),
    title = title.takeIf { isNoTitle != 1 }.orEmpty(),
    author = author?.toUser() ?: User(id = authorId, name = "", nameShow = "", portrait = ""),
    createTime = createTime.toLong(),
    listTime = lastTimeInt.toLong(),
    abstract = richAbstract.abstractText(emoticons),
    firstPostId = firstPostId,
    firstPost = firstPostContent.toPostContent(emoticons),
    images = media.map { it.toThreadImage() }.toImmutableList(),
    video = videoInfo?.toThreadVideo(),
    shared = origin_thread_info?.takeIf { is_share_thread == 1 }?.toSharedThread(emoticons),
    replyCount = replyNum,
    shareCount = shareNum,
    agree = Agree(count = agreeNum.toLong(), agreed = agree?.hasAgree == 1),
    isGood = isGood == 1,
    isTop = isTop == 1,
    tabName = tabName,
    hotNum = hotNum.toLong(),
)

/**
 * The abstract as one line of text: text pieces with runs of spaces collapsed, emoticons as their
 * `#(name)` codes.
 */
internal fun List<PbContent>.abstractText(emoticons: EmoticonRegistry): String = joinToString(separator = "") {
    when (it.type) {
        in TextAbstractTypes -> it.text.replace(Spaces, " ")
        EmoticonAbstractType -> {
            emoticons.register(it.text, it.c)
            "#(${it.c})"
        }

        else -> ""
    }
}

/**
 * `bigPic` is sized for the card, `srcPic` up to 1920 wide and `originPic` the upload itself. Shared
 * threads may leave some of them empty; each then falls back to the nearest one there is.
 */
internal fun Media.toThreadImage() = ThreadImage(
    small = bigPic.ifEmpty { srcPic }.ifEmpty { originPic },
    large = srcPic.ifEmpty { bigPic }.ifEmpty { originPic },
    original = originPic.ifEmpty { srcPic }.ifEmpty { bigPic },
    animated = dynamicPic,
    width = width,
    height = height,
    isLong = isLongPic == 1,
    postId = postId,
    originalSize = originSize,
    showOriginalButton = showOriginalBtn == 1,
)

internal fun VideoInfo.toThreadVideo() = ThreadVideo(
    url = videoUrl,
    cover = thumbnailUrl,
    coverWidth = thumbnailWidth,
    coverHeight = thumbnailHeight,
)

internal fun OriginThreadInfo.toSharedThread(emoticons: EmoticonRegistry) = SharedThread(
    id = tid.toLongOrNull() ?: 0L,
    forumId = fid,
    forumName = fname,
    content = content.toPostContent(emoticons),
    images = media.map { it.toThreadImage() }.toImmutableList(),
    video = video_info?.toThreadVideo(),
)

private val TextAbstractTypes = setOf(0, 40)
private const val EmoticonAbstractType = 2
private val Spaces = Regex(" {2,}")

/** A thread or replies on a user's profile, as their posts list returned them. */
fun PostInfoList.toUserPost(emoticons: EmoticonRegistry): UserPost {
    val author = User(id = user_id, name = user_name, nameShow = name_show, portrait = user_portrait)
    if (is_thread != 1) {
        return UserReplies(
            threadId = thread_id,
            postId = post_id,
            threadTitle = title,
            author = author,
            replies = content.map {
                UserReply(
                    text = it.post_content.abstractText,
                    time = it.create_time,
                    postId = it.post_id,
                    isSubPost = it.post_type == 1L,
                )
            }.toImmutableList(),
        )
    }
    return UserThread(
        ThreadSummary(
            id = thread_id,
            forum = ForumSummary(id = forum_id, name = forum_name, avatar = ""),
            title = title.takeIf { is_ntitle != 1 }.orEmpty(),
            author = author,
            createTime = create_time.toLong(),
            listTime = create_time.toLong(),
            abstract = rich_abstract.abstractText(emoticons),
            firstPostId = post_id,
            images = media.map { it.toThreadImage() }.toImmutableList(),
            video = video_info?.toThreadVideo(),
            shared = origin_thread_info?.takeIf { is_share_thread == 1 }?.toSharedThread(emoticons),
            replyCount = reply_num,
            shareCount = share_num.toLong(),
            agree = Agree(count = agree_num.toLong(), agreed = agree?.hasAgree == 1),
        ),
    )
}
