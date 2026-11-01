package com.huanchengfly.tieba.post.ui.page.main.home

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
class HomeScreenScreenshotTest {
    @get:Rule
    val legacyApp = LegacyAppRule()

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun grid() {
        captureScreenshot { HomeScreen(state = HomePreviewData.loaded, onEvent = {}) }
    }

    @Test
    fun list() {
        captureScreenshot { HomeScreen(state = HomePreviewData.loaded, onEvent = {}, listSingle = true) }
    }

    @Test
    fun loading() {
        composeRule.captureScreenshotPaused { HomeScreen(state = HomePreviewData.loading, onEvent = {}) }
    }

    @Test
    fun guest() {
        composeRule.captureScreenshotPaused { HomeScreen(state = HomePreviewData.guest, onEvent = {}, canOpenExplore = true) }
    }

    @Test
    fun error() {
        captureScreenshot { HomeScreen(state = HomePreviewData.error, onEvent = {}) }
    }
}
