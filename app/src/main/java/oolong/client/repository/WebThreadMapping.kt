package oolong.client.repository

import kotlinx.collections.immutable.toImmutableList
import oolong.client.model.Agree
import oolong.client.model.ForumSummary
import oolong.client.model.RankReason
import oolong.client.model.ThreadSummary
import oolong.client.model.User
import oolong.core.network.explore.WebContent
import oolong.core.network.explore.WebThread

/**
 * A thread as the web pages return it. Without its first floor, as a topic's threads come, the
 * floor is what the abstract holds, pictures included.
 */
fun WebThread.toThreadSummary(emoticons: EmoticonRegistry): ThreadSummary {
    val abstract = richAbstract.map(WebContent::toPbContent)
    return ThreadSummary(
        id = tid.takeIf { it != 0L } ?: id,
        forum = ForumSummary(
            id = forum?.id?.takeIf { it != 0L } ?: fid,
            name = fname.ifEmpty { forum?.name.orEmpty() },
            avatar = forum?.avatar.orEmpty(),
        ),
        title = title.takeIf { noTitle != 1 }.orEmpty(),
        author = author?.let { User(id = it.id, name = it.name, nameShow = it.nameShow, portrait = it.portrait) }
            ?: User(id = authorId, name = "", nameShow = "", portrait = ""),
        createTime = createTime,
        listTime = lastTime.takeIf { it != 0L } ?: createTime,
        abstract = abstract.abstractText(emoticons),
        firstPostId = firstPostId,
        firstPost = firstPost.ifEmpty { richAbstract }.map(WebContent::toPbContent).toPostContent(emoticons),
        images = media.map { it.toMedia().toThreadImage() }.toImmutableList(),
        replyCount = replyCount,
        shareCount = shareCount,
        agree = Agree(count = agreeCount, agreed = agree?.agreed == 1),
        isGood = good == 1,
        isTop = top == 1,
    )
}

/**
 * Why a thread or forum is on a ranking, from the server's line such as 「近一天2.6W吧友看过」 or
 * 「平均6.4min被解答」; a line it doesn't know stays as it is.
 */
internal fun rankReason(line: String): RankReason {
    val text = line.trim()
    fun count(pattern: Regex): Long? = pattern.find(text)?.let { parseCount(it.groupValues[1], it.groupValues[2]) }
    count(ReadersPattern)?.let { return RankReason.Readers(it) }
    count(LikesPattern)?.let { return RankReason.Likes(it) }
    count(FollowersPattern)?.let { return RankReason.NewFollowers(it) }
    count(ThreadsPattern)?.let { return RankReason.NewThreads(it) }
    count(ReadsPattern)?.let { return RankReason.Reads(it) }
    AnswerPattern.find(text)?.groupValues?.get(1)?.toFloatOrNull()?.let { return RankReason.AnswerMinutes(it) }
    return RankReason.Text(text)
}

private const val Number = """([\d.]+)\s*(KW|W|万|亿)?"""
private val ReadersPattern = Regex("""^(?:近一天)?$Number\s*吧友看过$""")
private val LikesPattern = Regex("""^(?:近一天)?获赞$Number\s*次$""")
private val FollowersPattern = Regex("""^吧关注\s*\+\s*$Number$""")
private val ThreadsPattern = Regex("""^发贴量\s*\+\s*$Number$""")
private val ReadsPattern = Regex("""^贴子阅读量\s*$Number$""")
private val AnswerPattern = Regex("""^平均\s*([\d.]+)\s*min\s*被解答$""")

private fun parseCount(number: String, unit: String): Long? {
    val value = number.toDoubleOrNull() ?: return null
    val scale = when (unit) {
        "W", "万" -> 1e4
        "KW" -> 1e7
        "亿" -> 1e8
        else -> 1.0
    }
    return Math.round(value * scale)
}
