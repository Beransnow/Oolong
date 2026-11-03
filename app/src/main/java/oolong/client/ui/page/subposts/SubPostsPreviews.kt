package oolong.client.ui.page.subposts

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import oolong.client.arch.LoadStatus
import oolong.client.arch.PagerState
import oolong.client.model.Agree
import oolong.client.model.ForumSummary
import oolong.client.model.Post
import oolong.client.model.PostContent
import oolong.client.model.SubPost
import oolong.client.model.TextSpan
import oolong.client.model.User
import oolong.client.ui.common.theme.compose.TiebaLiteTheme
import java.io.IOException

/** Sample sub-post page states, shared by the previews below and the screenshot tests. */
internal object SubPostsPreviewData {
    private val lz = User(id = 10, name = "louzhu", nameShow = "楼主本人", portrait = "", level = 12)

    private fun text(value: String) =
        persistentListOf<PostContent>(PostContent.Text(persistentListOf(TextSpan.Plain(value))))

    private val post = Post(
        id = 77,
        floor = 5,
        title = "",
        time = 1_700_000_000,
        author = User(id = 20, name = "replier", nameShow = "回复的人", portrait = "", level = 4),
        content = text("这一楼的内容"),
        agree = Agree(count = 8, agreed = false),
        subPosts = persistentListOf(),
        subPostCount = 3,
    )

    val loaded = SubPostsState(
        threadId = 9_000,
        post = post,
        threadAuthorId = lz.id,
        forum = ForumSummary(id = 52, name = "测试", avatar = ""),
        subPosts = PagerState(
            items = listOf(
                SubPost(201, lz, text("楼主的楼中楼回复"), time = 1_700_000_100, agree = Agree(2, agreed = true)),
                SubPost(202, User(30, "other", "另一个人", "", level = 7), text("另一条回复"), time = 1_700_000_200),
            ).map { SubPostItem(it) }.toImmutableList(),
            hasMore = true,
        ),
        totalCount = 3,
        viewerId = 99,
    )

    val loading = SubPostsState(threadId = 9_000, subPosts = PagerState(refresh = LoadStatus.Loading))

    val error = SubPostsState(threadId = 9_000, subPosts = PagerState(refresh = LoadStatus.Error(IOException("网络不可用"))))
}

@Preview("SubPostsScreen", showBackground = true)
@Composable
private fun PreviewSubPostsScreen() {
    TiebaLiteTheme { SubPostsScreen(state = SubPostsPreviewData.loaded, onEvent = {}, replyAvatar = "") }
}

@Preview("SubPostsScreen Sheet", showBackground = true)
@Composable
private fun PreviewSubPostsScreenSheet() {
    TiebaLiteTheme { SubPostsScreen(state = SubPostsPreviewData.loaded, onEvent = {}, isSheet = true) }
}

@Preview("SubPostsScreen Loading", showBackground = true)
@Composable
private fun PreviewSubPostsScreenLoading() {
    TiebaLiteTheme { SubPostsScreen(state = SubPostsPreviewData.loading, onEvent = {}) }
}

@Preview("SubPostsScreen Error", showBackground = true)
@Composable
private fun PreviewSubPostsScreenError() {
    TiebaLiteTheme { SubPostsScreen(state = SubPostsPreviewData.error, onEvent = {}) }
}
