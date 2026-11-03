package oolong.client.ui.page.thread

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
import oolong.client.model.ThreadDetail
import oolong.client.model.ThreadSummary
import oolong.client.model.User
import oolong.client.model.Viewer
import oolong.client.repository.ThreadQuery
import oolong.client.repository.ThreadSort
import oolong.client.ui.common.theme.compose.TiebaLiteTheme
import java.io.IOException

/** Sample thread page states, shared by the previews below and the screenshot tests. */
internal object ThreadPreviewData {
    val lz = User(id = 10, name = "louzhu", nameShow = "楼主本人", portrait = "", level = 12)
    private val me = User(id = 99, name = "me", nameShow = "我", portrait = "")

    private fun text(value: String) =
        persistentListOf<PostContent>(PostContent.Text(persistentListOf(TextSpan.Plain(value))))

    fun post(floor: Int, author: User = User(floor.toLong(), "user$floor", "用户 $floor", "", level = 5)) = Post(
        id = floor.toLong(),
        floor = floor,
        title = "",
        time = 1_700_000_000,
        author = author,
        content = text("第 $floor 楼的回复内容"),
        agree = Agree(count = floor.toLong(), agreed = floor == 3),
        subPosts = if (floor == 2) persistentListOf(SubPost(201, lz, text("楼主回复了这一楼"), time = 0)) else persistentListOf(),
        subPostCount = if (floor == 2) 3 else 0,
    )

    private val summary = ThreadSummary(
        id = 9_000,
        forum = ForumSummary(id = 52, name = "测试", avatar = ""),
        title = "帖子标题",
        author = lz,
        createTime = 1_700_000_000,
        listTime = 1_700_000_000,
        abstract = "",
        firstPostId = 1,
        firstPost = text("列表里带过来的一楼摘要"),
        replyCount = 120,
        agree = Agree(count = 2_345, agreed = true),
    )

    private val thread = ThreadDetail(summary = summary, storeMarkPostId = 3)

    val loaded = ThreadState(
        query = ThreadQuery(threadId = 9_000),
        thread = thread,
        firstPost = post(1, lz).copy(title = "帖子标题", content = text("这是一楼的正文。")),
        posts = PagerState(items = (2..4).map { PostItem(post(it)) }.toImmutableList(), hasMore = true),
        viewer = Viewer(me),
        currentPage = 1,
        totalPage = 8,
    )

    val loadingWithPreview = ThreadState(
        query = ThreadQuery(threadId = 9_000),
        preview = summary,
        posts = PagerState(refresh = LoadStatus.Loading),
    )

    val error = ThreadState(
        query = ThreadQuery(threadId = 9_000),
        posts = PagerState(refresh = LoadStatus.Error(IOException("网络不可用"))),
    )

    val newestFirstSeeLzAsGuest = loaded.copy(
        query = ThreadQuery(threadId = 9_000, sort = ThreadSort.NewestFirst, seeLz = true),
        posts = PagerState(items = persistentListOf(PostItem(post(9, lz)), PostItem(post(5, lz)))),
        viewer = Viewer(null),
    )
}

@Preview("ThreadScreen", showBackground = true)
@Composable
private fun PreviewThreadScreen() {
    TiebaLiteTheme { ThreadScreen(state = ThreadPreviewData.loaded, onEvent = {}) }
}

@Preview("ThreadScreen Loading", showBackground = true)
@Composable
private fun PreviewThreadScreenLoading() {
    TiebaLiteTheme { ThreadScreen(state = ThreadPreviewData.loadingWithPreview, onEvent = {}) }
}

@Preview("ThreadScreen Error", showBackground = true)
@Composable
private fun PreviewThreadScreenError() {
    TiebaLiteTheme { ThreadScreen(state = ThreadPreviewData.error, onEvent = {}) }
}
