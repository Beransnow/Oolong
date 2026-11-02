package com.huanchengfly.tieba.post.ui.page.main.notifications.list

import com.huanchengfly.tieba.post.model.NotificationType
import com.huanchengfly.tieba.post.testing.LegacyAppRule
import com.huanchengfly.tieba.post.testing.captureScreenshot
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
