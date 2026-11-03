package oolong.client.ui.page.subposts

import androidx.compose.ui.test.junit4.createComposeRule
import oolong.client.testing.LegacyAppRule
import oolong.client.testing.captureScreenshot
import oolong.client.testing.captureScreenshotPaused
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "+w393dp-h851dp-xxhdpi")
class SubPostsScreenScreenshotTest {
    @get:Rule
    val legacyApp = LegacyAppRule()

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loaded() {
        captureScreenshot { SubPostsScreen(state = SubPostsPreviewData.loaded, onEvent = {}, replyAvatar = "") }
    }

    @Test
    fun loadedInSheet() {
        captureScreenshot { SubPostsScreen(state = SubPostsPreviewData.loaded, onEvent = {}, isSheet = true) }
    }

    @Test
    fun loading() {
        composeRule.captureScreenshotPaused { SubPostsScreen(state = SubPostsPreviewData.loading, onEvent = {}) }
    }

    @Test
    fun error() {
        captureScreenshot { SubPostsScreen(state = SubPostsPreviewData.error, onEvent = {}) }
    }
}
