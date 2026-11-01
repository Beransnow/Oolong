package com.huanchengfly.tieba.post.ui.page.search

import com.huanchengfly.tieba.post.testing.LegacyAppRule
import com.huanchengfly.tieba.post.testing.captureScreenshot
import com.huanchengfly.tieba.post.ui.page.search.forum.SearchForumList
import com.huanchengfly.tieba.post.ui.page.search.thread.SearchThreadResults
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h851dp-xxhdpi")
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
