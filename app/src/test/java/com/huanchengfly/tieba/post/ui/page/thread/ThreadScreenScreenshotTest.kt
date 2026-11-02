package com.huanchengfly.tieba.post.ui.page.thread

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
@Config(qualifiers = "+w393dp-h851dp-xxhdpi")
class ThreadScreenScreenshotTest {
    @get:Rule
    val legacyApp = LegacyAppRule()

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loaded() {
        captureScreenshot { ThreadScreen(state = ThreadPreviewData.loaded, onEvent = {}) }
    }

    @Test
    @Config(qualifiers = "+en")
    fun loadedEnglish() {
        captureScreenshot { ThreadScreen(state = ThreadPreviewData.loaded, onEvent = {}) }
    }

    @Test
    fun loadingWithPreview() {
        composeRule.captureScreenshotPaused { ThreadScreen(state = ThreadPreviewData.loadingWithPreview, onEvent = {}) }
    }

    @Test
    fun error() {
        captureScreenshot { ThreadScreen(state = ThreadPreviewData.error, onEvent = {}) }
    }

    @Test
    fun newestFirstSeeLzAsGuest() {
        captureScreenshot { ThreadScreen(state = ThreadPreviewData.newestFirstSeeLzAsGuest, onEvent = {}) }
    }
}
