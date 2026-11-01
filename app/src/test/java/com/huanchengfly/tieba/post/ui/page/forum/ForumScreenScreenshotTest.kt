package com.huanchengfly.tieba.post.ui.page.forum

import androidx.compose.ui.test.junit4.createComposeRule
import com.huanchengfly.tieba.post.testing.LegacyAppRule
import com.huanchengfly.tieba.post.testing.captureScreenshot
import com.huanchengfly.tieba.post.testing.captureScreenshotPaused
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h851dp-xxhdpi")
class ForumScreenScreenshotTest {
    @get:Rule
    val legacyApp = LegacyAppRule()

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loaded() {
        captureScreenshot { ForumScreen(state = ForumPreviewData.loaded, onEvent = {}) }
    }

    @Test
    fun goodClasses() {
        captureScreenshot { ForumScreen(state = ForumPreviewData.goodClasses, onEvent = {}) }
    }

    @Test
    fun notFollowed() {
        captureScreenshot { ForumScreen(state = ForumPreviewData.notFollowed, onEvent = {}) }
    }

    @Test
    fun loading() {
        composeRule.captureScreenshotPaused { ForumScreen(state = ForumPreviewData.loading, onEvent = {}) }
    }

    @Test
    fun error() {
        captureScreenshot { ForumScreen(state = ForumPreviewData.error, onEvent = {}) }
    }
}
