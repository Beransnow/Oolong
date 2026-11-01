package com.huanchengfly.tieba.post.ui.page.history

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.huanchengfly.tieba.post.model.HistoryEntry
import com.huanchengfly.tieba.post.ui.common.theme.compose.TiebaLiteTheme
import kotlinx.collections.immutable.persistentListOf

/** Sample history, shared by the preview below and the screenshot tests. */
internal object HistoryPreviewData {
    private fun thread(id: Long, title: String, time: Long) =
        HistoryEntry.Thread(id = id, time = time, avatar = "", threadId = id, title = title, authorName = "用户 $id")

    val loaded = HistoryState(
        threads = HistoryList(
            today = persistentListOf(thread(1, "今天看过的帖子", 1_700_000_000_000)),
            before = persistentListOf(thread(2, "以前看过的帖子", 1_690_000_000_000), thread(3, "另一个帖子", 1_680_000_000_000)),
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
