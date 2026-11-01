package com.huanchengfly.tieba.post.ui.page.settings.block.blocklist

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.huanchengfly.tieba.post.models.database.Block
import com.huanchengfly.tieba.post.ui.common.theme.compose.TiebaLiteTheme
import kotlinx.collections.immutable.persistentListOf

/** Sample rules, shared by the preview below and the screenshot tests. */
internal object BlockListPreviewData {
    val loaded = BlockListState(
        blackList = persistentListOf(
            Block(Block.CATEGORY_BLACK_LIST, Block.TYPE_KEYWORD, keywords = "[\"广告\",\"推广\"]", id = 1),
            Block(Block.CATEGORY_BLACK_LIST, Block.TYPE_USER, username = "用户名", uid = "7", id = 2),
        ),
        loading = false,
    )
}

@Preview("BlockListScreen", showBackground = true)
@Composable
private fun PreviewBlockListScreen() {
    TiebaLiteTheme { BlockListScreen(state = BlockListPreviewData.loaded, onBack = {}, onAdd = { _, _ -> }, onRemove = {}) }
}
