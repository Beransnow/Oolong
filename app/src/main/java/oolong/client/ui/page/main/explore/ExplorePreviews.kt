package oolong.client.ui.page.main.explore

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import oolong.client.api.models.protos.Abstract
import oolong.client.api.models.protos.Agree
import oolong.client.api.models.protos.SimpleForum
import oolong.client.api.models.protos.ThreadInfo
import oolong.client.api.models.protos.User
import oolong.client.arch.LoadStatus
import oolong.client.arch.PagerState
import oolong.client.model.DislikeOption
import oolong.client.model.ForumRanking
import oolong.client.model.ForumSummary
import oolong.client.model.HotTopic
import oolong.client.model.RankReason
import oolong.client.model.RankedForum
import oolong.client.model.RankedThread
import oolong.client.model.RankedTopic
import oolong.client.model.TopicRef
import oolong.client.repository.EmoticonRegistry
import oolong.client.repository.toThreadSummary
import oolong.client.ui.common.theme.compose.TiebaLiteTheme
import oolong.client.ui.page.main.explore.concern.ConcernScreen
import oolong.client.ui.page.main.explore.hot.HotScreen
import oolong.client.ui.page.main.explore.hot.HotState
import oolong.client.ui.page.main.explore.hot.HotThread
import oolong.client.ui.page.main.explore.personalized.PersonalizedScreen
import oolong.client.ui.page.main.explore.personalized.PersonalizedState
import oolong.client.ui.page.main.explore.personalized.RecommendedItem

/** Sample explore feeds, shared by the previews below and the screenshot tests. */
internal object ExplorePreviewData {
    private fun thread(id: Long, title: String, forum: String = "测试", hotNum: Int = 0) = ThreadInfo(
        id = id,
        threadId = id,
        title = title,
        author = User(id = id, name = "user$id", nameShow = "用户 $id"),
        lastTimeInt = 1_700_000_000,
        createTime = 1_700_000_000,
        replyNum = id.toInt() * 3,
        agreeNum = id.toInt(),
        agree = Agree(agreeNum = id, hasAgree = if (id == 2L) 1 else 0),
        _abstract = listOf(Abstract(type = 0, text = "第 $id 个帖子的摘要")),
        forumInfo = SimpleForum(id = 50 + id, name = forum, avatar = "forum.png"),
        hotNum = hotNum,
    )

    private val emoticons = EmoticonRegistry { _, _ -> }

    private val threads = listOf(
        thread(1, "第一个帖子", "数码", hotNum = 1_234_567),
        thread(2, "第二个帖子", "游戏", hotNum = 345_678),
        thread(3, "第三个帖子", "动漫", hotNum = 23_456),
        thread(4, "第四个帖子", "音乐", hotNum = 1_234),
    ).map { it.toThreadSummary(emoticons) }

    val concern = PagerState(items = threads.map { FeedThread(it) }.toImmutableList(), hasMore = true)

    private val dislikeOptions = persistentListOf(
        DislikeOption(1, "不感兴趣", ""),
        DislikeOption(2, "看过了", ""),
        DislikeOption(7, "屏蔽吧：数码", "fid"),
    )

    val personalized = PersonalizedState(
        list = PagerState(
            items = threads.map { RecommendedItem(FeedThread(it), dislikeOptions) }.toImmutableList(),
            hasMore = true,
        ),
        disliked = persistentSetOf(3L),
        seenFrom = threads[2].id,
    )

    val hot = HotState(
        topics = persistentListOf(
            RankedTopic(1, "第一个话题", "", "", 2_748_069, HotTopic.Tag.Hot),
            RankedTopic(2, "第二个话题", "", "", 1_687_770, HotTopic.Tag.Hot),
            RankedTopic(3, "第三个话题有一个很长很长的名字", "", "", 1_510_626, HotTopic.Tag.Hot),
            RankedTopic(4, "第四个话题", "", "", 1_264_150, HotTopic.Tag.New),
            RankedTopic(5, "第五个话题", "", "", 1_159_000),
        ),
        threads = threads.mapIndexed { index, thread ->
            HotThread(
                RankedThread(
                    rank = index + 1,
                    thread = thread,
                    reason = RankReason.Readers(26_000L - index * 3_000),
                    topic = TopicRef(1, "第一个话题").takeIf { index == 1 },
                )
            )
        }.toImmutableList(),
        forums = ForumRanking(
            rule = "近一天关注量上涨最快的吧",
            forums = listOf("孙笑川", "抗压背锅", "deepseek").mapIndexed { index, name ->
                RankedForum(
                    rank = index + 1,
                    forum = ForumSummary(100L + index, name, ""),
                    members = 7_937_010L / (index + 1),
                    liked = index == 1,
                    reason = RankReason.NewFollowers(2992L - index * 800),
                )
            }.toImmutableList(),
        ),
        load = LoadStatus.Idle,
    )
}

@Preview("ConcernScreen", showBackground = true)
@Composable
private fun PreviewConcernScreen() {
    TiebaLiteTheme { ConcernScreen(list = ExplorePreviewData.concern, events = FeedThreadEvents(), onRefresh = {}, onLoadMore = {}) }
}

@Preview("PersonalizedScreen", showBackground = true)
@Composable
private fun PreviewPersonalizedScreen() {
    TiebaLiteTheme {
        PersonalizedScreen(
            state = ExplorePreviewData.personalized,
            events = FeedThreadEvents(),
            onRefresh = {},
            onLoadMore = {},
            onDislike = { _, _, _ -> },
        )
    }
}

@Preview("HotScreen", showBackground = true)
@Composable
private fun PreviewHotScreen() {
    TiebaLiteTheme {
        HotScreen(
            state = ExplorePreviewData.hot,
            events = FeedThreadEvents(),
            onRefresh = {},
            onSelectThreadMeasure = {},
            onSelectForumMeasure = {},
            onOpenTopics = {},
            onOpenTopic = {},
            onOpenForum = {},
            onFollowForum = {},
        )
    }
}
