package com.huanchengfly.tieba.post.ui.page.settings.block.blocklist

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.huanchengfly.tieba.post.model.BlockRule
import com.huanchengfly.tieba.post.model.BlockRule.Match
import com.huanchengfly.tieba.post.ui.common.theme.compose.TiebaLiteTheme
import kotlinx.collections.immutable.persistentListOf

/** Sample rules, shared by the preview below and the screenshot tests. */
internal object BlockListPreviewData {
    private const val Now = 1_700_000_000_000L
    private const val Day = 24 * 60 * 60 * 1000L

    val loaded = BlockListState(
        rules = persistentListOf(
            BlockRule(id = 8, match = Match.Keyword(listOf("剧透")), expiresAt = Now + 6 * Day, hits = 41, lastHitAt = Now - 2 * 60 * 60 * 1000, createdAt = Now - Day),
            BlockRule(id = 7, match = Match.User(7, "每日抽奖机器人"), hits = 128, lastHitAt = Now - 60 * 1000, createdAt = Now - 30 * Day),
            BlockRule(id = 6, match = Match.Forum("抽卡"), scope = BlockRule.Scope.Feeds, hits = 37, lastHitAt = Now - Day, createdAt = Now - 40 * Day),
            BlockRule(id = 5, match = Match.Keyword(listOf("互关|互粉"), regex = true), action = BlockRule.Action.Hide, hits = 9, lastHitAt = Now - 3 * Day, createdAt = Now - 50 * Day),
            BlockRule(id = 4, match = Match.Keyword(listOf("原神")), inForum = "崩坏3", scope = BlockRule.Scope.Thread, createdAt = Now - 5 * Day),
            BlockRule(id = 3, match = Match.User(9, "老朋友小周"), action = BlockRule.Action.Allow, hits = 3, lastHitAt = Now - 7 * Day, createdAt = Now - 60 * Day),
            BlockRule(id = 2, match = Match.Keyword(listOf("618", "好价")), enabled = false, createdAt = Now - 140 * Day),
            BlockRule(id = 1, match = Match.Keyword(listOf("双十一预售")), createdAt = Now - 96 * Day),
        ),
        loading = false,
        now = Now,
    )
}

@Preview("BlockListScreen", showBackground = true)
@Composable
private fun PreviewBlockListScreen() {
    TiebaLiteTheme { BlockListScreen(state = BlockListPreviewData.loaded, onBack = {}, onOpen = {}, onDelete = {}, onRestore = {}) }
}
