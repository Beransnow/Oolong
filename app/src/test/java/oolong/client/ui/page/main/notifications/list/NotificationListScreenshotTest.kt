package oolong.client.ui.page.main.notifications.list

import oolong.client.model.NotificationType
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
class NotificationListScreenshotTest {
    @get:Rule
    val legacyApp = LegacyAppRule()

    @Test
    fun replies() {
        captureScreenshot {
            NotificationList(
                type = NotificationType.ReplyMe,
                list = NotificationsPreviewData.replies,
                onRefresh = {}, onLoadMore = {}, onOpen = {}, onOpenQuote = {}, onOpenUser = {},
            )
        }
    }
}
