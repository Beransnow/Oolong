package oolong.client.testing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import oolong.client.ui.common.theme.compose.TiebaLiteTheme
import oolong.client.ui.navigation.NoOpNavigator
import oolong.client.ui.page.LocalNavigator

/**
 * Renders [content] in the app theme and compares it with the stored screenshot named after the
 * calling test (`recordRoborazziDebug` records, `verifyRoborazziDebug` compares).
 */
fun captureScreenshot(content: @Composable () -> Unit) {
    captureRoboImage { ScreenshotFrame(content) }
}

/**
 * Captures [content] like [captureScreenshot], with the composition clock paused: for content that
 * animates forever, such as a progress indicator, which otherwise never lets the capture happen.
 */
fun ComposeContentTestRule.captureScreenshotPaused(content: @Composable () -> Unit) {
    mainClock.autoAdvance = false
    setContent { ScreenshotFrame(content) }
    mainClock.advanceTimeBy(1_000)
    onRoot().captureRoboImage()
}

/** The app theme and what pages expect around them, as in the app. */
@Composable
fun ScreenshotFrame(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNavigator provides NoOpNavigator) {
        TiebaLiteTheme {
            Box(Modifier.background(MaterialTheme.colors.background)) {
                content()
            }
        }
    }
}
