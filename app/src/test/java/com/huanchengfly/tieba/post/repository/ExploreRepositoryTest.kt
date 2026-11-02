package com.huanchengfly.tieba.post.repository

import com.huanchengfly.tieba.core.network.explore.ExploreRemoteDataSource
import com.huanchengfly.tieba.post.api.models.protos.AlaLiveInfo
import com.huanchengfly.tieba.post.api.models.protos.FrsTabInfo
import com.huanchengfly.tieba.post.api.models.protos.RecommendTopicList
import com.huanchengfly.tieba.post.api.models.protos.SimpleForum
import com.huanchengfly.tieba.post.api.models.protos.ThreadInfo
import com.huanchengfly.tieba.post.api.models.protos.VideoInfo
import com.huanchengfly.tieba.post.api.models.protos.hotThreadList.HotThreadListResponseData
import com.huanchengfly.tieba.post.api.models.protos.personalized.DislikeReason
import com.huanchengfly.tieba.post.api.models.protos.personalized.PersonalizedResponseData
import com.huanchengfly.tieba.post.api.models.protos.personalized.ThreadPersonalized
import com.huanchengfly.tieba.post.api.models.protos.topicList.NewTopicList
import com.huanchengfly.tieba.post.api.models.protos.topicList.TopicListResponseData
import com.huanchengfly.tieba.post.api.models.protos.userLike.ConcernData
import com.huanchengfly.tieba.post.api.models.protos.userLike.UserLikeResponseData
import com.huanchengfly.tieba.post.model.DislikeOption
import com.huanchengfly.tieba.post.model.HotCategory
import com.huanchengfly.tieba.post.model.HotTopic
import com.huanchengfly.tieba.post.model.RankedTopic
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExploreRepositoryTest {
    private val remote = mockk<ExploreRemoteDataSource>(relaxed = true)
    private val store = object : ConcernFeedStore {
        override var lastRequestUnix = 100L
    }
    private var hideVideos = false
    private val repository = ExploreRepository(
        remote, store, { hideVideos }, Dispatchers.Unconfined, { _, _ -> },
    )

    @Test
    fun `recommended threads carry their dislike reasons and leave out live streams, hidden videos and adverts`() = runTest {
        hideVideos = true
        val forum = SimpleForum(id = 52)
        coEvery { remote.personalized(true, 1) } returns PersonalizedResponseData(
            thread_list = listOf(
                ThreadInfo(id = 1, threadId = 1, forumInfo = forum),
                ThreadInfo(id = 2, threadId = 2, forumInfo = forum, ala_info = AlaLiveInfo()),
                ThreadInfo(id = 3, threadId = 3, forumInfo = forum, videoInfo = VideoInfo()),
                ThreadInfo(id = 4, threadId = 4),
            ),
            thread_personalized = listOf(
                ThreadPersonalized(tid = 1, dislikeResource = listOf(DislikeReason(dislikeReason = "看过了", dislikeId = 2, extra = "x"))),
            ),
        )

        val threads = repository.recommended(refresh = true, page = 1)

        assertEquals(listOf(1L), threads.map { it.thread.id })
        assertEquals(listOf(DislikeOption(2, "看过了", "x")), threads.single().dislikeOptions)
    }

    @Test
    fun `the followed users' feed remembers when it was loaded from the top`() = runTest {
        coEvery { remote.userLike("", 100) } returns UserLikeResponseData(
            threadInfo = listOf(
                ConcernData(threadList = ThreadInfo(id = 1, threadId = 1), recommendType = 1),
                ConcernData(recommendType = 2),
            ),
            pageTag = "next",
            hasMore = 1,
            requestUnix = 200,
        )
        coEvery { remote.userLike("next", 200) } returns UserLikeResponseData(pageTag = "", hasMore = 0, requestUnix = 300)

        val first = repository.followedUsersThreads("")
        val second = repository.followedUsersThreads("next")

        assertEquals(listOf(1L), first.items.map { it.id })
        assertEquals("next", first.next)
        assertNull(second.next)
        assertEquals(200L, store.lastRequestUnix)
    }

    @Test
    fun `maps the hot topics and categories`() = runTest {
        coEvery { remote.hotThreads(HotCategory.ALL) } returns HotThreadListResponseData(
            topicList = listOf(RecommendTopicList(topicId = 1, topicName = "热", tag = 2), RecommendTopicList(topicId = 2, topicName = "新", tag = 1)),
            hotThreadTabInfo = listOf(FrsTabInfo(tabName = "游戏", tabCode = "game")),
            threadInfo = listOf(ThreadInfo(id = 9)),
        )

        val hot = repository.hotThreads()

        assertEquals(listOf(HotTopic(1, "热", HotTopic.Tag.Hot), HotTopic(2, "新", HotTopic.Tag.New)), hot.topics)
        assertEquals(listOf(HotCategory("game", "游戏")), hot.categories)
        assertEquals(listOf(9L), hot.threads.map { it.id })
    }

    @Test
    fun `maps the full topics ranking`() = runTest {
        coEvery { remote.topics() } returns TopicListResponseData(
            topic_list = listOf(NewTopicList(topic_id = 1, topic_name = "话题", topic_desc = "描述", discuss_num = 5, topic_image = "i", topic_tag = 2)),
        )

        assertEquals(listOf(RankedTopic(1, "话题", "描述", "i", 5, HotTopic.Tag.Hot)), repository.topics())
    }

    @Test
    fun `dislikes a thread with the reasons picked`() = runTest {
        val thread = ThreadInfo(id = 9, threadId = 9, forumInfo = SimpleForum(id = 52))

        repository.dislike(thread.toThreadSummary { _, _ -> }, listOf(DislikeOption(1, "a", "e1"), DislikeOption(7, "b", "e2")), clickTime = 5)

        coVerify { remote.dislike(9, 52, listOf(1, 7), listOf("e1", "e2"), 5) }
    }
}
