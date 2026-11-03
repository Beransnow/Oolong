package oolong.client.ui.page.main.notifications.list

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.collections.immutable.persistentListOf
import oolong.client.arch.PagerState
import oolong.client.model.BlockRule
import oolong.client.model.Notification
import oolong.client.model.NotificationType
import oolong.client.model.User
import oolong.client.repository.BlockVerdict
import oolong.client.ui.common.theme.compose.TiebaLiteTheme

/** Sample notifications, shared by the preview below and the screenshot tests. */
internal object NotificationsPreviewData {
    val replies = PagerState(
        items = persistentListOf(
            NotificationItem(
                Notification(
                    threadId = 1, postId = 11, inPost = true, threadTitle = "帖子标题", content = "楼中楼里的回复",
                    quoteContent = "我在楼中楼说的话", quotePostId = 10, author = User(1, "a", "回复的人", ""), time = 1_700_000_000,
                )
            ),
            NotificationItem(
                Notification(
                    threadId = 2, postId = 21, inPost = false, threadTitle = "我发的帖子", content = "回复了你的帖子",
                    author = User(2, "b", "", ""), time = 1_700_000_000,
                )
            ),
            NotificationItem(
                Notification(threadId = 3, postId = 31, inPost = false, threadTitle = "", content = "广告", author = User(3, "c", "", "")),
                block = BlockVerdict(BlockRule(condition = BlockRule.Condition.Text(BlockRule.Pattern.Words(listOf("广告")))), hidden = false),
            ),
        ),
    )
}

@Preview("NotificationList", showBackground = true)
@Composable
private fun PreviewNotificationList() {
    TiebaLiteTheme {
        NotificationList(
            type = NotificationType.ReplyMe,
            list = NotificationsPreviewData.replies,
            onRefresh = {}, onLoadMore = {}, onOpen = {}, onOpenQuote = {}, onOpenUser = {},
        )
    }
}
