package oolong.client.ui.page.hottopic.list

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.collections.immutable.toImmutableList
import oolong.client.arch.LoadStatus
import oolong.client.model.HotTopic
import oolong.client.model.RankedTopic
import oolong.client.ui.common.theme.compose.TiebaLiteTheme

/** A sample ranking, shared by the preview below and the screenshot tests. */
internal object HotTopicListPreviewData {
    val loaded = HotTopicListState(
        topics = (1L..5L).map {
            RankedTopic(
                id = it,
                name = "第 $it 个话题",
                description = "话题 $it 的介绍",
                image = "",
                discussCount = 100_000L / it,
                tag = when (it) {
                    1L -> HotTopic.Tag.Hot
                    2L -> HotTopic.Tag.New
                    else -> HotTopic.Tag.None
                },
            )
        }.toImmutableList(),
        load = LoadStatus.Idle,
    )
}

@Preview("HotTopicListScreen", showBackground = true)
@Composable
private fun PreviewHotTopicListScreen() {
    TiebaLiteTheme { HotTopicListScreen(state = HotTopicListPreviewData.loaded, onBack = {}, onRefresh = {}) }
}
