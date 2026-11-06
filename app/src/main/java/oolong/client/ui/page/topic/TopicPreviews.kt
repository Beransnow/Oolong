package oolong.client.ui.page.topic

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import oolong.client.arch.PagerState
import oolong.client.model.Agree
import oolong.client.model.ForumSummary
import oolong.client.model.HotTopic
import oolong.client.model.ThreadSummary
import oolong.client.model.Topic
import oolong.client.model.TopicForum
import oolong.client.model.User
import oolong.client.ui.common.theme.compose.TiebaLiteTheme
import oolong.client.ui.page.main.explore.FeedThread
import oolong.client.ui.page.main.explore.FeedThreadEvents

/** A sample topic, shared by the preview below and the screenshot tests. */
internal object TopicPreviewData {
    private fun thread(id: Long, title: String, abstract: String, forum: String) = ThreadSummary(
        id = id,
        forum = ForumSummary(id = 50 + id, name = forum, avatar = ""),
        title = title,
        author = User(id = id, name = "user$id", nameShow = "用户 $id", portrait = ""),
        createTime = 1_700_000_000,
        listTime = 1_700_000_000,
        abstract = abstract,
        firstPostId = id,
        replyCount = id.toInt() * 37,
        agree = Agree(count = id * 120, agreed = false),
    )

    val loaded = TopicState(
        topic = Topic(
            id = 1,
            name = "时之笛重制版开分96登顶最佳",
            description = "《塞尔达传说:时之笛》重制版的媒体评测正式解禁，几乎收获一致好评，在多家聚合平台上成为年度评分最高的游戏。",
            picture = "",
            discussCount = 1_510_626,
            rank = 3,
            tag = HotTopic.Tag.Hot,
            color = 0xFF9B8F37.toInt(),
        ),
        forums = persistentListOf(
            TopicForum(ForumSummary(1, "塞尔达传说", ""), 451_166),
            TopicForum(ForumSummary(2, "steam", ""), 4_869_420),
            TopicForum(ForumSummary(3, "ns", ""), 698_415),
        ),
        leadThreadId = 1,
        threads = PagerState(
            items = listOf(
                thread(1, "塞尔达传说时之笛MC评分96分", "如何评价？", "steam"),
                thread(2, "老任今年有点nb，时之笛96开分", "", "bilibili"),
                thread(3, "时之笛评分反转了", "太让人意外了", "塞尔达传说"),
            ).map { FeedThread(it) }.toImmutableList(),
            hasMore = true,
        ),
    )
}

@Preview("TopicScreen", showBackground = true)
@Composable
private fun PreviewTopicScreen() {
    TiebaLiteTheme {
        TopicScreen(state = TopicPreviewData.loaded, events = FeedThreadEvents(), onRefresh = {}, onLoadMore = {}, onOpenForum = {})
    }
}
