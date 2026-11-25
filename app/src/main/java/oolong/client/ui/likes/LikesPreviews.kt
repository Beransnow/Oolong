package oolong.client.ui.likes

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.collections.immutable.persistentListOf
import oolong.client.arch.PagerState
import oolong.client.model.Agree
import oolong.client.model.ForumSummary
import oolong.client.model.LikedReply
import oolong.client.model.PostContent
import oolong.client.model.TextSpan
import oolong.client.model.ThreadSummary
import oolong.client.model.User
import oolong.client.ui.designsystem.theme.TiebaLiteTheme

/** Sample likes, shared by the previews below and the screenshot tests. */
internal object LikesPreviewData {
    private val forum = ForumSummary(id = 1, name = "示例", avatar = "")

    private fun thread(id: Long, title: String, abstract: String, likes: Long) = ThreadSummary(
        id = id,
        forum = forum,
        title = title,
        author = User(id = id, name = "user$id", nameShow = "用户 $id", portrait = ""),
        createTime = 1_700_000_000,
        lastReplyTime = 1_700_000_000,
        abstract = abstract,
        firstPostId = id * 10,
        replyCount = 12,
        agree = Agree(count = likes, agreed = true),
    )

    private fun reply(id: Long, text: String, title: String, floorId: Long? = null, agreed: Boolean = true) = LikedReply(
        postId = id,
        floorId = floorId,
        author = User(id = id, name = "user$id", nameShow = "用户 $id", portrait = ""),
        time = 1_700_000_000,
        content = persistentListOf(PostContent.Text(persistentListOf(TextSpan.Plain(text)))),
        agree = Agree(count = if (agreed) 525 else 524, agreed = agreed),
        threadId = id * 10,
        threadTitle = title,
        forum = forum,
    )

    val state = LikesState(
        threads = PagerState(
            items = persistentListOf(
                thread(1, "赞过的帖子", "帖子的摘要，列表里显示开头的几行。", likes = 9092),
                thread(2, "", "没有标题的帖子，只显示正文。", likes = 3),
            ),
            loaded = true,
        ),
        replies = PagerState(
            items = persistentListOf(
                reply(1, "赞过的一条回复。", "回复所在帖子的标题"),
                reply(2, "回复 某人 :楼中楼里的回复，写得长一些，看看换行以后和下面标题块之间的距离。", "标题很长的帖子，超过两行以后在末尾省略，不把这一条撑得太高，再多写几个字凑够两行", floorId = 20),
                reply(3, "已经取消点赞的回复，还留在列表里。", "另一个帖子", agreed = false),
            ),
            hasMore = true,
            loaded = true,
        ),
    )
}

@Preview("LikesScreen threads", showBackground = true)
@Composable
private fun PreviewLikesThreads() {
    TiebaLiteTheme { LikesPreviewScreen(LikesPreviewData.state, LikesTab.Threads) }
}

@Preview("LikesScreen replies", showBackground = true)
@Composable
private fun PreviewLikesReplies() {
    TiebaLiteTheme { LikesPreviewScreen(LikesPreviewData.state, LikesTab.Replies) }
}

@Composable
internal fun LikesPreviewScreen(state: LikesState, tab: LikesTab) {
    LikesScreen(
        state = state, onBack = {}, onShow = {}, onRefresh = {}, onLoadMore = {}, onAgreeThread = {}, onAgreeReply = {},
        onOpenThread = {}, onOpenThreadReplies = {}, onOpenReply = {}, onOpenThreadOf = {}, onOpenUser = {}, onOpenForum = {},
        initialTab = tab,
    )
}
