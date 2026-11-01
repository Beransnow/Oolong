package com.huanchengfly.tieba.post.ui.page.forum

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.huanchengfly.tieba.post.testing.LegacyAppRule
import com.huanchengfly.tieba.post.testing.ScreenshotFrame
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

    /** Held part way up: the avatar and name on their way into the bar, the rest sliding under it. */
    @Test
    fun collapsing() {
        composeRule.setContent { ScreenshotFrame { ForumScreen(state = ForumPreviewData.loaded, onEvent = {}) } }
        composeRule.onRoot().performTouchInput {
            down(center)
            moveBy(Offset(0f, -viewConfiguration.touchSlop - 1f))
            moveBy(Offset(0f, -40.dp.toPx()))
        }
        composeRule.onRoot().captureRoboImage()
    }

    /** Collapsed: the avatar and name are the bar's title. */
    @Test
    fun collapsed() {
        composeRule.setContent { ScreenshotFrame { ForumScreen(state = ForumPreviewData.loaded, onEvent = {}) } }
        composeRule.onRoot().performTouchInput { swipeUp(startY = centerY, endY = top + 1f, durationMillis = 1_000) }
        composeRule.onRoot().captureRoboImage()
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
