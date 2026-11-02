package com.huanchengfly.tieba.post.repository

import com.huanchengfly.tieba.post.api.models.protos.Media
import com.huanchengfly.tieba.post.api.models.protos.OriginThreadInfo
import com.huanchengfly.tieba.post.api.models.protos.PbContent
import com.huanchengfly.tieba.post.api.models.protos.ThreadInfo
import com.huanchengfly.tieba.post.api.models.protos.VideoInfo
import com.huanchengfly.tieba.post.model.Agree
import com.huanchengfly.tieba.post.model.ForumSummary
import com.huanchengfly.tieba.post.model.SharedThread
import com.huanchengfly.tieba.post.model.ThreadImage
import com.huanchengfly.tieba.post.model.ThreadSummary
import com.huanchengfly.tieba.post.model.ThreadVideo
import com.huanchengfly.tieba.post.model.User
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

/** What the thread page shows of [this] until its first page loads. */
fun ThreadSummary.toThreadPreview() = ThreadPreview(title = title, author = author, content = firstPost, time = createTime)

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

internal fun Media.toThreadImage() = ThreadImage(
    small = srcPic,
    large = bigPic,
    original = originPic,
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
