package com.huanchengfly.tieba.post.repository

import com.huanchengfly.tieba.core.network.agree.AgreeRemoteDataSource
import com.huanchengfly.tieba.core.network.agree.AgreeTarget
import com.huanchengfly.tieba.core.network.explore.ExploreRemoteDataSource
import com.huanchengfly.tieba.post.api.models.protos.ThreadInfo
import com.huanchengfly.tieba.post.api.models.protos.personalized.ThreadPersonalized
import com.huanchengfly.tieba.post.arch.IoDispatcher
import com.huanchengfly.tieba.post.arch.Page
import com.huanchengfly.tieba.post.model.DislikeOption
import com.huanchengfly.tieba.post.model.HotCategory
import com.huanchengfly.tieba.post.model.HotThreads
import com.huanchengfly.tieba.post.model.HotTopic
import com.huanchengfly.tieba.post.model.RankedTopic
import com.huanchengfly.tieba.post.model.RecommendedThread
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Remembers when the followed users' feed was last loaded from the top, which the server wants back. */
interface ConcernFeedStore {
    var lastRequestUnix: Long
}

/** The explore tab's feeds: recommended threads, the followed users' threads and the hot threads. */
class ExploreRepository @Inject constructor(
    private val remote: ExploreRemoteDataSource,
    private val agreeRemote: AgreeRemoteDataSource,
    private val concernStore: ConcernFeedStore,
    private val videoFilter: VideoThreadFilter,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    /**
     * A page of recommended threads, without live streams and, if the user hides them, videos.
     *
     * @param refresh whether the user pulled to refresh, which asks for new threads rather than the next page
     */
    suspend fun recommended(refresh: Boolean, page: Int): List<RecommendedThread> = withContext(io) {
        val response = remote.personalized(refresh, page)
        val personalized = response.thread_personalized.associateBy { it.tid }
        filter(response.thread_list).map { thread ->
            RecommendedThread(thread, personalized[thread.id]?.dislikeOptions().orEmpty().toImmutableList())
        }
    }

    /** A page of the threads the users the user follows posted; the key is the server's page tag, empty for the first. */
    suspend fun followedUsersThreads(pageTag: String): Page<String, ThreadInfo> = withContext(io) {
        val first = pageTag.isEmpty()
        val response = remote.userLike(pageTag, concernStore.lastRequestUnix)
        if (first) concernStore.lastRequestUnix = response.requestUnix
        val threads = response.threadInfo.filter { it.recommendType == 1 }.mapNotNull { it.threadList }
        Page(threads, next = response.pageTag.takeIf { response.hasMore == 1 && it.isNotEmpty() })
    }

    /** The hot threads of the category [code], [HotCategory.ALL] for all of them. */
    suspend fun hotThreads(code: String = HotCategory.ALL): HotThreads = withContext(io) {
        val response = remote.hotThreads(code)
        HotThreads(
            topics = response.topicList.map { topic ->
                HotTopic(topic.topicId, topic.topicName, topicTag(topic.tag))
            },
            categories = response.hotThreadTabInfo.map { HotCategory(it.tabCode, it.tabName) },
            threads = response.threadInfo,
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
    suspend fun dislike(thread: ThreadInfo, reasons: List<DislikeOption>, clickTime: Long) = withContext(io) {
        remote.dislike(
            threadId = thread.threadId,
            forumId = thread.forumInfo?.id ?: thread.forumId,
            reasonIds = reasons.map { it.id },
            extras = reasons.map { it.extra },
            clickTime = clickTime,
        )
    }

    suspend fun agreeThread(thread: ThreadInfo, agree: Boolean) = withContext(io) {
        agreeRemote.opAgree(AgreeTarget.Thread(thread.threadId, thread.firstPostId), thread.forumId, undo = !agree)
    }

    private fun filter(threads: List<ThreadInfo>): List<ThreadInfo> {
        val hideVideos = videoFilter.hidesVideos()
        return threads.filter { it.ala_info == null && !(hideVideos && it.videoInfo != null) }
    }
}

private fun topicTag(tag: Int) = when (tag) {
    1 -> HotTopic.Tag.New
    2 -> HotTopic.Tag.Hot
    else -> HotTopic.Tag.None
}

private fun ThreadPersonalized.dislikeOptions() = dislikeResource.map {
    DislikeOption(id = it.dislikeId, text = it.dislikeReason, extra = it.extra)
}
