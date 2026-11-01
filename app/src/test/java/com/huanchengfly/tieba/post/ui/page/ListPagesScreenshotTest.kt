package com.huanchengfly.tieba.post.ui.page

import com.huanchengfly.tieba.post.arch.LoadStatus
import com.huanchengfly.tieba.post.testing.LegacyAppRule
import com.huanchengfly.tieba.post.testing.captureScreenshot
import com.huanchengfly.tieba.post.ui.page.forum.detail.ForumDetailPreviewData
import com.huanchengfly.tieba.post.ui.page.forum.detail.ForumDetailScreen
import com.huanchengfly.tieba.post.ui.page.forum.detail.ForumDetailState
import com.huanchengfly.tieba.post.ui.page.history.HistoryPreviewData
import com.huanchengfly.tieba.post.ui.page.history.HistoryPreviewScreen
import com.huanchengfly.tieba.post.ui.page.hottopic.list.HotTopicListPreviewData
import com.huanchengfly.tieba.post.ui.page.hottopic.list.HotTopicListScreen
import com.huanchengfly.tieba.post.ui.page.settings.block.blocklist.BlockListPreviewData
import com.huanchengfly.tieba.post.ui.page.settings.block.blocklist.BlockListScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The smaller pages: history, the hot topics ranking, a forum's info and the block list. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h851dp-xxhdpi")
class ListPagesScreenshotTest {
    @get:Rule
    val legacyApp = LegacyAppRule()

    @Test
    fun history() {
        captureScreenshot { HistoryPreviewScreen(HistoryPreviewData.loaded) }
    }

    @Test
    fun hotTopics() {
        captureScreenshot { HotTopicListScreen(state = HotTopicListPreviewData.loaded, onBack = {}, onRefresh = {}) }
    }

    @Test
    fun forumDetail() {
        captureScreenshot {
            ForumDetailScreen(state = ForumDetailState(ForumDetailPreviewData.intro, LoadStatus.Idle), onBack = {}, onReload = {})
        }
    }

    @Test
    fun blockList() {
        captureScreenshot { BlockListScreen(state = BlockListPreviewData.loaded, onBack = {}, onAdd = { _, _ -> }, onRemove = {}) }
    }
}
