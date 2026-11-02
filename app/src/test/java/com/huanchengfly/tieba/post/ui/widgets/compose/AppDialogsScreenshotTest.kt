package com.huanchengfly.tieba.post.ui.widgets.compose

import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.testing.LegacyAppRule
import com.huanchengfly.tieba.post.testing.ScreenshotFrame
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "+w393dp-h851dp-xxhdpi")
@OptIn(ExperimentalRoborazziApi::class)
class AppDialogsScreenshotTest {
    @get:Rule
    val legacyApp = LegacyAppRule()

    @get:Rule
    val composeRule = createComposeRule()

    @After
    fun clear() {
        AppDialogs.shown.clear()
    }

    private fun capture(dialog: AppDialog) {
        AppDialogs.show(dialog)
        composeRule.setContent { ScreenshotFrame { AppDialogHost() } }
        composeRule.waitForIdle()
        captureScreenRoboImage()
    }

    @Test
    fun sitePermission() {
        capture(AppDialog.Permission("example.com_0", "“example.com”想要使用你的位置信息", R.drawable.ic_round_location_on) { _, _ -> })
    }

    @Test
    fun message() {
        capture(AppDialog.Message(message = "确定要离开此页面吗？", cancel = "取消"))
    }

    @Test
    fun permissionTip() {
        capture(AppDialog.PermissionTip("贴吧 Lite 正在请求“位置信息”权限", "授权后，将允许网页获取你的位置"))
    }
}
