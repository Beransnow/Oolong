package com.huanchengfly.tieba.post.ui.page.thread

import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.huanchengfly.tieba.post.testing.LegacyAppRule
import com.huanchengfly.tieba.post.testing.ScreenshotFrame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "+w393dp-h851dp-xxhdpi")
class ThreadScreenTest {
    @get:Rule
    val legacyApp = LegacyAppRule()

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `a thread too short to scroll keeps its top bar when swiped`() {
        composeRule.setContent { ScreenshotFrame { ThreadScreen(state = ThreadPreviewData.loaded, onEvent = {}) } }
        val forumAvatar = composeRule.onNodeWithContentDescription("测试")
        val top = forumAvatar.getBoundsInRoot().top

        composeRule.onRoot().performTouchInput { swipeUp(startY = bottom * 0.8f, endY = bottom * 0.3f) }
        composeRule.waitForIdle()

        assertEquals(top, forumAvatar.getBoundsInRoot().top)
    }
}
