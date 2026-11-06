package oolong.client.ui.page

import oolong.client.arch.LoadStatus
import oolong.client.testing.LegacyAppRule
import oolong.client.testing.captureScreenshot
import oolong.client.ui.page.forum.detail.ForumDetailPreviewData
import oolong.client.ui.page.forum.detail.ForumDetailScreen
import oolong.client.ui.page.forum.detail.ForumDetailState
import oolong.client.ui.page.history.HistoryPreviewData
import oolong.client.ui.page.history.HistoryPreviewScreen
import oolong.client.ui.page.hottopic.list.HotTopicListPreviewData
import oolong.client.ui.page.hottopic.list.HotTopicListScreen
import oolong.client.ui.page.main.explore.FeedThreadEvents
import oolong.client.ui.page.settings.block.blocklist.BlockListPreviewData
import oolong.client.ui.page.settings.block.blocklist.BlockListScreen
import oolong.client.ui.page.topic.TopicPreviewData
import oolong.client.ui.page.topic.TopicScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The smaller pages: history, the hot topics ranking and a topic, a forum's info and the block list. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "+w393dp-h851dp-xxhdpi")
class ListPagesScreenshotTest {
    @get:Rule
    val legacyApp = LegacyAppRule()

    @Test
    fun history() {
        captureScreenshot { HistoryPreviewScreen(HistoryPreviewData.loaded) }
    }

    @Test
    fun topic() {
        captureScreenshot {
            TopicScreen(state = TopicPreviewData.loaded, events = FeedThreadEvents(), onRefresh = {}, onLoadMore = {}, onOpenForum = {})
        }
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
    @Config(qualifiers = "+en")
    fun forumDetailEnglish() {
        captureScreenshot {
            ForumDetailScreen(state = ForumDetailState(ForumDetailPreviewData.intro, LoadStatus.Idle), onBack = {}, onReload = {})
        }
    }

    @Test
    fun blockList() {
        captureScreenshot { BlockListScreen(state = BlockListPreviewData.loaded, onBack = {}, onOpen = {}, onDelete = {}, onRestore = {}) }
    }

    @Test
    @Config(qualifiers = "+en")
    fun blockListEnglish() {
        captureScreenshot { BlockListScreen(state = BlockListPreviewData.loaded, onBack = {}, onOpen = {}, onDelete = {}, onRestore = {}) }
    }
}
