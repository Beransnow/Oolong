package oolong.client.ui.page.likes

import oolong.client.testing.LegacyAppRule
import oolong.client.testing.captureScreenshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "+w393dp-h851dp-xxhdpi")
class LikesScreenshotTest {
    @get:Rule
    val legacyApp = LegacyAppRule()

    @Test
    fun threads() {
        captureScreenshot { LikesPreviewScreen(LikesPreviewData.state, LikesTab.Threads) }
    }

    @Test
    fun replies() {
        captureScreenshot { LikesPreviewScreen(LikesPreviewData.state, LikesTab.Replies) }
    }
}
