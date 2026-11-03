package oolong.client.ui.page.settings.block.blocklist

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.collections.immutable.persistentListOf
import oolong.client.model.BlockRule
import oolong.client.model.BlockRule.Condition
import oolong.client.model.BlockRule.Pattern
import oolong.client.model.ContentKind
import oolong.client.ui.common.theme.compose.TiebaLiteTheme

/** Sample rules, shared by the preview below and the screenshot tests. */
internal object BlockListPreviewData {
    private const val Now = 1_700_000_000_000L
    private const val Day = 24 * 60 * 60 * 1000L

    val loaded = BlockListState(
        rules = persistentListOf(
            BlockRule(id = 8, condition = Condition.Text(Pattern.Words(listOf("剧透"))), expiresAt = Now + 6 * Day, hits = 41, lastHitAt = Now - 2 * 60 * 60 * 1000, createdAt = Now - Day),
            BlockRule(id = 7, condition = Condition.User(7, "每日抽奖机器人"), hits = 128, lastHitAt = Now - 60 * 1000, createdAt = Now - 30 * Day),
            BlockRule(id = 6, condition = Condition.Forum("抽卡"), scope = BlockRule.Scope.Feeds, hits = 37, lastHitAt = Now - Day, createdAt = Now - 40 * Day),
            BlockRule(id = 5, condition = Condition.Text(Pattern.Regex("互关|互粉")), action = BlockRule.Action.Hide, hits = 9, lastHitAt = Now - 3 * Day, createdAt = Now - 50 * Day),
            BlockRule(
                id = 4,
                condition = Condition.AllOf(listOf(Condition.Forum("崩坏3"), Condition.Text(Pattern.Words(listOf("原神"))), Condition.Not(Condition.ThreadAuthor))),
                scope = BlockRule.Scope.Thread,
                createdAt = Now - 5 * Day,
            ),
            BlockRule(
                id = 9,
                condition = Condition.AnyOf(listOf(Condition.Only(ContentKind.Emoticon), Condition.ShorterThan(3))),
                hits = 211,
                lastHitAt = Now - 60 * 1000,
                createdAt = Now - 2 * Day,
            ),
            BlockRule(id = 3, condition = Condition.User(9, "老朋友小周"), action = BlockRule.Action.Allow, hits = 3, lastHitAt = Now - 7 * Day, createdAt = Now - 60 * Day),
            BlockRule(id = 2, condition = Condition.Text(Pattern.Words(listOf("618", "好价"))), enabled = false, createdAt = Now - 140 * Day),
            BlockRule(id = 1, condition = Condition.Text(Pattern.Words(listOf("双十一预售"))), createdAt = Now - 96 * Day),
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
