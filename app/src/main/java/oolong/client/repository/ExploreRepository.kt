package oolong.client.repository

import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import oolong.client.api.models.protos.ThreadInfo
import oolong.client.api.models.protos.personalized.ThreadPersonalized
import oolong.client.arch.IoDispatcher
import oolong.client.arch.Page
import oolong.client.model.DislikeOption
import oolong.client.model.ForumMeasure
import oolong.client.model.ForumRanking
import oolong.client.model.ForumSummary
import oolong.client.model.HotTopic
import oolong.client.model.RankedForum
import oolong.client.model.RankedThread
import oolong.client.model.RankedTopic
import oolong.client.model.RecommendedThread
import oolong.client.model.ThreadMeasure
import oolong.client.model.ThreadSummary
import oolong.client.model.Topic
import oolong.client.model.TopicForum
import oolong.client.model.TopicPage
import oolong.client.model.TopicRef
import oolong.core.network.explore.ExploreRemoteDataSource
import javax.inject.Inject

/** Remembers when the followed users' feed was last loaded from the top, which the server wants back. */
interface ConcernFeedStore {
    var lastRequestUnix: Long
}

/** The explore tab's feeds: recommended threads, the followed users' threads and the hot threads. */
class ExploreRepository @Inject constructor(
    private val remote: ExploreRemoteDataSource,
    private val concernStore: ConcernFeedStore,
    private val videoFilter: VideoThreadFilter,
    @IoDispatcher private val io: CoroutineDispatcher,
    private val emoticons: EmoticonRegistry,
) {
    /**
     * A page of recommended threads, without adverts, live streams and, if the user hides them, videos.
     *
     * @param refresh whether the user pulled to refresh, which asks for new threads rather than the next page
     */
    suspend fun recommended(refresh: Boolean, page: Int): List<RecommendedThread> = withContext(io) {
        val response = remote.personalized(refresh, page)
        val personalized = response.thread_personalized.associateBy { it.tid }
        filter(response.thread_list).map { thread ->
            RecommendedThread(thread.toThreadSummary(emoticons), personalized[thread.id]?.dislikeOptions().orEmpty().toImmutableList())
        }
    }

    /** A page of the threads the users the user follows posted; the key is the server's page tag, empty for the first. */
    suspend fun followedUsersThreads(pageTag: String): Page<String, ThreadSummary> = withContext(io) {
        val first = pageTag.isEmpty()
        val response = remote.userLike(pageTag, concernStore.lastRequestUnix)
        if (first) concernStore.lastRequestUnix = response.requestUnix
        val threads = response.threadInfo.filter { it.recommendType == 1 }.mapNotNull { it.threadList?.toThreadSummary(emoticons) }
        Page(threads, next = response.pageTag.takeIf { response.hasMore == 1 && it.isNotEmpty() })
    }

    /** The threads of the last day ranked by [measure]. */
    suspend fun threadRanking(measure: ThreadMeasure): List<RankedThread> = withContext(io) {
        val tab = remote.threadRanking(measure.code) ?: return@withContext emptyList()
        tab.threads.mapIndexed { index, thread ->
            RankedThread(
                rank = index + 1,
                thread = thread.toThreadSummary(emoticons),
                reason = rankReason(tab.labels.getOrNull(index).orEmpty()),
                topic = thread.reasons.firstOrNull { it.topicId != 0L && it.text.isNotBlank() }?.let { TopicRef(it.topicId, it.text) },
            )
        }
    }

    /** The forums of the last day ranked by [measure]. */
    suspend fun forumRanking(measure: ForumMeasure): ForumRanking = withContext(io) {
        val tab = remote.forumRanking(measure.code) ?: return@withContext ForumRanking("")
        ForumRanking(
            rule = tab.rule.replace("24h内", "近一天"),
            forums = tab.forums.mapIndexed { index, forum ->
                RankedForum(
                    rank = index + 1,
                    forum = ForumSummary(forum.id, forum.name, forum.avatar),
                    members = forum.members,
                    liked = forum.liked == 1,
                    reason = rankReason(forum.score),
                )
            }.toImmutableList(),
        )
    }

    /**
     * Page [page] (from 1) of a topic, found by [name] when [id] is 0.
     *
     * @param lastThreadId the id of the last thread of the page before, 0 for the first
     */
    suspend fun topic(id: Long, name: String, page: Int, lastThreadId: Long): TopicPage = withContext(io) {
        val response = remote.topic(id, name, page, lastThreadId)
        val cards = response.areas.flatMap { it.cards }
        TopicPage(
            topic = response.topic?.let {
                Topic(
                    id = it.id,
                    name = it.name,
                    description = it.description,
                    picture = it.picture,
                    discussCount = it.discussCount,
                    rank = it.rank,
                    tag = topicTag(it.tag),
                    color = response.themeColor?.day?.common?.let(::argb),
                )
            },
            forums = cards.filter { it.type == RelatedForumsCard }.flatMap { it.data?.forums.orEmpty() }.map {
                TopicForum(ForumSummary(it.id, it.name, it.avatar), it.members)
            },
            threads = cards.mapNotNull { card ->
                val thread = card.data?.thread?.takeIf { card.type in ThreadCards } ?: return@mapNotNull null
                thread.toThreadSummary(emoticons) to (card.type == LeadThreadCard)
            },
            hasMore = response.hasMore == 1,
        )
    }

    /** The full hot topics ranking. */
    suspend fun topics(): List<RankedTopic> = withContext(io) {
        remote.topics().topic_list.map {
            RankedTopic(
                id = it.topic_id,
                name = it.topic_name,
                description = it.topic_desc,
                image = it.topic_image,
                discussCount = it.discuss_num,
                tag = topicTag(it.topic_tag),
            )
        }
    }

    /**
     * Tells the server the user doesn't want threads like [thread] for the [reasons] they picked.
     *
     * @param clickTime when the user opened the menu, in Unix milliseconds
     */
    suspend fun dislike(thread: ThreadSummary, reasons: List<DislikeOption>, clickTime: Long) = withContext(io) {
        remote.dislike(
            threadId = thread.id,
            forumId = thread.forum.id,
            reasonIds = reasons.map { it.id },
            extras = reasons.map { it.extra },
            clickTime = clickTime,
        )
    }

    /**
     * Drops live streams, videos if hidden, and adverts: those from outside any forum, which carry
     * no forum, and promoted threads, which the server marks with their own thread type.
     */
    private fun filter(threads: List<ThreadInfo>): List<ThreadInfo> {
        val hideVideos = videoFilter.hidesVideos()
        return threads.filter {
            it.ala_info == null && it.forumInfo != null && it.thread_type != PromotedThreadType && !(hideVideos && it.videoInfo != null)
        }
    }
}

private const val PromotedThreadType = 80
private const val LeadThreadCard = "common_thread"
private const val RelatedForumsCard = "relate_forum"
private val ThreadCards = setOf(LeadThreadCard, "relate_thread")

/** `RRGGBB`, or `AARRGGBB`, without `#`, as an opaque ARGB colour; null when it isn't one. */
private fun argb(hex: String): Int? {
    val value = hex.removePrefix("#").toLongOrNull(16) ?: return null
    return when (hex.removePrefix("#").length) {
        6 -> (0xFF000000 or value).toInt()
        8 -> (value or 0xFF000000).toInt()
        else -> null
    }
}

private fun topicTag(tag: Int) = when (tag) {
    1 -> HotTopic.Tag.New
    2 -> HotTopic.Tag.Hot
    else -> HotTopic.Tag.None
}

private fun ThreadPersonalized.dislikeOptions() = dislikeResource.map {
    DislikeOption(
        id = it.dislikeId,
        text = it.dislikeReason,
        extra = it.extra,
        schema = it.schema.takeIf(String::isNotBlank),
        toast = it.toast?.content?.joinToString("") { part -> part.text }?.takeIf(String::isNotBlank),
    )
}
