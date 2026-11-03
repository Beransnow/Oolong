package oolong.client.ui.page.search

import oolong.client.testing.LegacyAppRule
import oolong.client.testing.captureScreenshot
import oolong.client.ui.page.search.forum.SearchForumList
import oolong.client.ui.page.search.thread.SearchThreadResults
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "+w393dp-h851dp-xxhdpi")
class SearchScreenshotTest {
    @get:Rule
    val legacyApp = LegacyAppRule()

    @Test
    fun history() {
        captureScreenshot { SearchPreviewScreen(SearchPreviewData.history) }
    }

    @Test
    fun forums() {
        captureScreenshot { SearchForumList(state = SearchPreviewData.forums, onRefresh = {}, onOpen = {}) }
    }

    @Test
    fun threads() {
        captureScreenshot {
            SearchThreadResults(
                list = SearchPreviewData.threads,
                keyword = "测试",
                onRefresh = {},
                onLoadMore = {},
                onOpen = {},
                onOpenUser = {},
                onOpenForum = {},
            )
        }
    }
}
