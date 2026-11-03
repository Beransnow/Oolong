package oolong.client.ui.page.search

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import oolong.client.arch.LoadStatus
import oolong.client.arch.PagerState
import oolong.client.model.QuotedPost
import oolong.client.model.SearchAuthor
import oolong.client.model.SearchHistoryItem
import oolong.client.model.SearchMatches
import oolong.client.model.SearchedForum
import oolong.client.model.SearchedPost
import oolong.client.ui.common.theme.compose.TiebaLiteTheme
import oolong.client.ui.page.search.forum.SearchForumList
import oolong.client.ui.page.search.thread.SearchThreadResults

/** Sample search states, shared by the previews below and the screenshot tests. */
internal object SearchPreviewData {
    private fun author(id: Long) = SearchAuthor(id, "user$id", "用户 $id", "")

    val history = SearchState(
        history = persistentListOf("原神", "明日方舟", "minecraft", "安卓", "壁纸", "表情包", "手机推荐", "攻略")
            .mapIndexed { index, keyword -> SearchHistoryItem(index.toLong(), keyword) }
            .toImmutableList(),
    )

    val forums = MatchesState(
        matches = SearchMatches(
            exact = SearchedForum(1, "测试", "测试", "", "一个测试用的吧"),
            fuzzy = persistentListOf(
                SearchedForum(2, "测试一", "测试一", "", ""),
                SearchedForum(3, "测试二", "测试二", "", "第二个测试吧"),
            ),
        ),
        load = LoadStatus.Idle,
    )

    val threads = PagerState(
        items = persistentListOf(
            SearchedPost(
                threadId = 1, postId = 1, title = "测试帖子", content = "帖子里提到了测试", time = 1_700_000_000,
                author = author(1), forumName = "测试", forumAvatar = "", replyCount = 12, agreeCount = 3, shareCount = 1,
            ),
            SearchedPost(
                threadId = 2, postId = 20, title = "", content = "回复里也有测试", time = 1_700_000_000,
                author = author(2), forumName = "测试", forumAvatar = "", replyCount = 0, agreeCount = 0, shareCount = 0,
                thread = QuotedPost(2, 0, "被回复的帖子", "主题帖的内容", author(3)),
                quoted = QuotedPost(2, 19, "", "被引用的回复", author(4)),
            ),
        ),
        hasMore = true,
    )
}

@Preview("SearchScreen History", showBackground = true)
@Composable
private fun PreviewSearchHistory() {
    TiebaLiteTheme { SearchPreviewScreen(SearchPreviewData.history) }
}

@Preview("SearchForumList", showBackground = true)
@Composable
private fun PreviewSearchForumList() {
    TiebaLiteTheme { SearchForumList(state = SearchPreviewData.forums, onRefresh = {}, onOpen = {}) }
}

@Preview("SearchThreadResults", showBackground = true)
@Composable
private fun PreviewSearchThreadResults() {
    TiebaLiteTheme {
        SearchThreadResults(
            list = SearchPreviewData.threads,
            keyword = "测试",
            onRefresh = {},
            onLoadMore = {},
            onOpen = {},
            onOpenUser = {},
            onOpenForum = {},
        )
    }
}

@Composable
internal fun SearchPreviewScreen(state: SearchState, input: String = "") {
    SearchScreen(
        state = state,
        input = input,
        onInputChange = {},
        onSearch = {},
        onBack = {},
        onOpenTab = {},
        onRefreshTab = {},
        onLoadMoreThreads = {},
        onSetOrder = {},
        onForget = {},
        onClearHistory = {},
        onOpenForum = {},
        onOpenUser = {},
        onOpenThread = {},
    )
}
