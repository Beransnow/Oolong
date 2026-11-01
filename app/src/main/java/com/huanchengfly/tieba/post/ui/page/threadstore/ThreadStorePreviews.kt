package com.huanchengfly.tieba.post.ui.page.threadstore

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.huanchengfly.tieba.post.arch.PagerState
import com.huanchengfly.tieba.post.model.StoredThread
import com.huanchengfly.tieba.post.model.User
import com.huanchengfly.tieba.post.ui.common.theme.compose.TiebaLiteTheme
import kotlinx.collections.immutable.persistentListOf

/** Sample thread store states, shared by the previews below and the screenshot tests. */
internal object ThreadStorePreviewData {
    private fun thread(id: Long, title: String, deleted: Boolean = false, newPosts: Int = 0) = StoredThread(
        threadId = id,
        title = title,
        author = User(id = id, name = "user$id", nameShow = "用户 $id", portrait = ""),
        deleted = deleted,
        markPostId = 0,
        maxPostId = 0,
        latestFloor = if (newPosts > 0) 30 else 0,
        newPostCount = newPosts,
    )

    val loaded = PagerState(
        items = persistentListOf(
            thread(1, "有更新的帖子", newPosts = 3),
            thread(2, "已经删除的帖子", deleted = true),
            thread(3, "普通的帖子"),
        ),
        hasMore = true,
    )
}

@Preview("ThreadStoreScreen", showBackground = true)
@Composable
private fun PreviewThreadStoreScreen() {
    TiebaLiteTheme { ThreadStorePreviewScreen(ThreadStorePreviewData.loaded) }
}

@Composable
internal fun ThreadStorePreviewScreen(list: PagerState<StoredThread>) {
    ThreadStoreScreen(list = list, onBack = {}, onRefresh = {}, onLoadMore = {}, onRemove = {}, onOpenUser = {}, onOpen = {})
}
