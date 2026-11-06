package oolong.client.model

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** What a ranking of the day's threads goes by. */
enum class ThreadMeasure(val code: String) {
    MostRead("thread_uv_num"),
    MostLiked("thread_agree_num"),
}

/** What a ranking of the day's forums goes by. */
enum class ForumMeasure(val code: String) {
    MostFollowed("forum_like_num"),
    MostPosted("forum_post_num"),
    FastestAnswered("forum_thread_qa_avg_time"),
    MostRead("forum_thread_view"),
}

/** Why a thread or forum is on a ranking of the day, read from the server's own words where it can be. */
@Immutable
sealed interface RankReason {
    data class Readers(val count: Long) : RankReason

    data class Likes(val count: Long) : RankReason

    data class NewFollowers(val count: Long) : RankReason

    data class NewThreads(val count: Long) : RankReason

    /** How long its questions wait for an answer, on average. */
    data class AnswerMinutes(val minutes: Float) : RankReason

    /** How many times its threads were read. */
    data class Reads(val count: Long) : RankReason

    /** The server's words, when they read as none of the others. */
    data class Text(val text: String) : RankReason
}

/** A topic a thread is talked about under, which its page opens by name when the id is 0. */
@Immutable
data class TopicRef(val id: Long, val name: String)

/** A thread on a ranking, at its place counted before any are hidden. */
@Immutable
data class RankedThread(val rank: Int, val thread: ThreadSummary, val reason: RankReason, val topic: TopicRef? = null)

/** A forum on a ranking. */
@Immutable
data class RankedForum(
    val rank: Int,
    val forum: ForumSummary,
    val members: Long,
    val liked: Boolean,
    val reason: RankReason,
)

/** A ranking of forums, with what it goes by in a sentence. */
@Immutable
data class ForumRanking(val rule: String, val forums: ImmutableList<RankedForum> = persistentListOf())

/** A hot topic as its own page shows it. */
@Immutable
data class Topic(
    val id: Long,
    val name: String,
    val description: String,
    val picture: String,
    val discussCount: Long,
    /** Its place on the hot topics ranking, 0 when off it. */
    val rank: Int,
    val tag: HotTopic.Tag,
    /** The topic's own colour as ARGB, null when it has none. */
    val color: Int?,
)

/** A forum a topic is talked about in. */
@Immutable
data class TopicForum(val forum: ForumSummary, val members: Long)

/** A page of a topic: the topic and its forums come with the first. */
data class TopicPage(
    val topic: Topic?,
    val forums: List<TopicForum>,
    /** The threads, the one picked to lead first, with whether each is that one. */
    val threads: List<Pair<ThreadSummary, Boolean>>,
    val hasMore: Boolean,
)
