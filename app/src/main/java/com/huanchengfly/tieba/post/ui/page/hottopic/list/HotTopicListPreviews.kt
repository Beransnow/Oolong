package com.huanchengfly.tieba.post.ui.page.hottopic.list

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.huanchengfly.tieba.post.arch.LoadStatus
import com.huanchengfly.tieba.post.model.HotTopic
import com.huanchengfly.tieba.post.model.RankedTopic
import com.huanchengfly.tieba.post.ui.common.theme.compose.TiebaLiteTheme
import kotlinx.collections.immutable.toImmutableList

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
