package com.huanchengfly.tieba.post.ui.page.main.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.airbnb.lottie.LottieCompositionFactory
import com.huanchengfly.tieba.post.R
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
        // The animation loads off the main thread, so whether it is drawn in time would be a race;
        // with it cached, it is there at once
        LottieCompositionFactory.fromRawResSync(ApplicationProvider.getApplicationContext(), R.raw.lottie_astronaut)
        composeRule.captureScreenshotPaused { HomeScreen(state = HomePreviewData.guest, onEvent = {}, canOpenExplore = true) }
    }

    @Test
    fun error() {
        captureScreenshot { HomeScreen(state = HomePreviewData.error, onEvent = {}) }
    }
}
