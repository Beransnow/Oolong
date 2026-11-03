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
import oolong.client.model.HotCategory
import oolong.client.model.HotTopic
import oolong.client.repository.EmoticonRegistry
import oolong.client.repository.toThreadSummary
import oolong.client.ui.common.theme.compose.TiebaLiteTheme
import oolong.client.ui.page.main.explore.concern.ConcernScreen
import oolong.client.ui.page.main.explore.hot.HotScreen
import oolong.client.ui.page.main.explore.hot.HotState
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
        newCount = 2,
    )

    val hot = HotState(
        topics = persistentListOf(
            HotTopic(1, "第一个话题", HotTopic.Tag.Hot),
            HotTopic(2, "第二个话题", HotTopic.Tag.New),
            HotTopic(3, "第三个话题"),
            HotTopic(4, "第四个话题"),
            HotTopic(5, "第五个话题"),
        ),
        categories = persistentListOf(HotCategory("game", "游戏"), HotCategory("ent", "娱乐"), HotCategory("sport", "体育")),
        threads = concern.items,
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
        HotScreen(state = ExplorePreviewData.hot, events = FeedThreadEvents(), onRefresh = {}, onSelectCategory = {}, onOpenTopics = {})
    }
}
