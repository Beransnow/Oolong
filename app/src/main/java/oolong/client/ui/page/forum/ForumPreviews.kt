package oolong.client.ui.page.forum

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import oolong.client.api.models.protos.Abstract
import oolong.client.api.models.protos.Agree
import oolong.client.api.models.protos.SimpleForum
import oolong.client.api.models.protos.ThreadInfo
import oolong.client.api.models.protos.User
import oolong.client.arch.LoadStatus
import oolong.client.arch.PagerState
import oolong.client.model.ForumDetail
import oolong.client.model.ForumTab
import oolong.client.model.GoodClass
import oolong.client.repository.toThreadSummary
import oolong.client.ui.common.theme.compose.TiebaLiteTheme
import java.io.IOException

/** Sample forum page states, shared by the previews below and the screenshot tests. */
internal object ForumPreviewData {
    private val forum = ForumDetail(
        id = 52,
        name = "测试",
        avatar = "",
        slogan = "测试用的吧",
        memberCount = 1_234,
        liked = true,
        level = 7,
        levelName = "七级",
        score = 300,
        levelUpScore = 500,
        signedIn = true,
        signedDays = 12,
        todaySignCount = 3_456,
        ruleTitle = "测试吧吧规",
        goodClasses = persistentListOf(GoodClass(0, "全部"), GoodClass(3, "攻略"), GoodClass(4, "资讯")),
        tabs = (ForumTab.Fixed + listOf(ForumTab.Custom(99, "吧友互助", 15), ForumTab.Custom(98, "前瞻消息", 15)))
            .toImmutableList(),
    )

    private fun thread(id: Long, title: String, top: Boolean = false) = ForumThreadItem(
        ThreadInfo(
            id = id,
            threadId = id,
            title = title,
            isTop = if (top) 1 else 0,
            author = User(id = id, name = "user$id", nameShow = "用户 $id"),
            lastTimeInt = 1_700_000_000,
            createTime = 1_700_000_000,
            replyNum = id.toInt() * 3,
            agreeNum = id.toInt(),
            agree = Agree(agreeNum = id, hasAgree = if (id == 3L) 1 else 0),
            _abstract = listOf(Abstract(type = 0, text = "第 $id 个帖子的摘要")),
            forumInfo = SimpleForum(id = 52, name = "测试"),
        ).toThreadSummary { _, _ -> }
    )

    private val threads = persistentListOf(
        thread(1, "置顶的公告", top = true),
        thread(2, "第一个帖子"),
        thread(3, "第二个帖子"),
        thread(4, "第三个帖子"),
    )

    val loaded = ForumState(
        forumName = "测试",
        forum = forum,
        lists = persistentMapOf(ForumTab.Latest to PagerState(items = threads, hasMore = true)),
        loggedIn = true,
    )

    val goodClasses = loaded.copy(
        selectedTab = ForumTab.Good,
        goodClassId = 3,
        lists = persistentMapOf(ForumTab.Good to PagerState(items = threads.drop(1).toImmutableList())),
    )

    val notFollowed = loaded.copy(forum = forum.copy(liked = false, signedIn = false))

    val loading = ForumState(forumName = "测试", lists = persistentMapOf(ForumTab.Latest to PagerState(refresh = LoadStatus.Loading)))

    val error = ForumState(
        forumName = "测试",
        lists = persistentMapOf(ForumTab.Latest to PagerState(refresh = LoadStatus.Error(IOException("网络不可用")))),
    )
}

@Preview("ForumScreen", showBackground = true)
@Composable
private fun PreviewForumScreen() {
    TiebaLiteTheme { ForumScreen(state = ForumPreviewData.loaded, onEvent = {}) }
}

@Preview("ForumScreen Good", showBackground = true)
@Composable
private fun PreviewForumScreenGood() {
    TiebaLiteTheme { ForumScreen(state = ForumPreviewData.goodClasses, onEvent = {}) }
}

@Preview("ForumScreen Loading", showBackground = true)
@Composable
private fun PreviewForumScreenLoading() {
    TiebaLiteTheme { ForumScreen(state = ForumPreviewData.loading, onEvent = {}) }
}
