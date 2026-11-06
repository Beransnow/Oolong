package oolong.client.ui.page.history

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.collections.immutable.persistentListOf
import oolong.client.model.HistoryEntry
import oolong.client.ui.common.theme.compose.TiebaLiteTheme

/** Sample history, shared by the preview below and the screenshot tests. */
internal object HistoryPreviewData {
    private fun thread(id: Long, title: String, time: Long) =
        HistoryEntry.Thread(
            id = id,
            time = time,
            avatar = "",
            threadId = id,
            title = title,
            authorName = "用户 $id",
            floor = if (id == 1L) 12 else 0,
            forumName = "测试",
        )

    val loaded = HistoryState(
        threads = HistoryList(
            sections = persistentListOf(
                HistorySection(HistoryDay.Today, persistentListOf(thread(1, "今天看过的帖子", 1_700_000_000_000))),
                HistorySection(
                    HistoryDay.Earlier,
                    persistentListOf(thread(2, "以前看过的帖子", 1_690_000_000_000), thread(3, "另一个帖子", 1_680_000_000_000)),
                ),
            ),
        ),
    )
}

@Preview("HistoryScreen", showBackground = true)
@Composable
private fun PreviewHistoryScreen() {
    TiebaLiteTheme { HistoryPreviewScreen(HistoryPreviewData.loaded) }
}

@Composable
internal fun HistoryPreviewScreen(state: HistoryState) {
    HistoryScreen(state = state, onBack = {}, onClear = {}, onLoadMore = {}, onDelete = {}, onOpen = {})
}
