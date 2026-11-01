package com.huanchengfly.tieba.post.ui.page.settings

import androidx.compose.runtime.CompositionLocalProvider
import com.huanchengfly.tieba.post.models.database.Account
import com.huanchengfly.tieba.post.testing.LegacyAppRule
import com.huanchengfly.tieba.post.testing.captureScreenshot
import com.huanchengfly.tieba.post.ui.navigation.NoOpNavigator
import com.huanchengfly.tieba.post.ui.page.settings.account.AccountManagePage
import com.huanchengfly.tieba.post.ui.page.settings.custom.CustomSettingsPage
import com.huanchengfly.tieba.post.ui.page.settings.habit.HabitSettingsPage
import com.huanchengfly.tieba.post.ui.page.settings.more.MoreSettingsPage
import com.huanchengfly.tieba.post.ui.page.settings.oksign.OKSignSettingsPage
import com.huanchengfly.tieba.post.ui.page.settings.post.PostSettingsPage
import com.huanchengfly.tieba.post.utils.AccountUtil
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h851dp-xxhdpi")
class SettingsScreenshotTest {
    @get:Rule
    val legacyApp = LegacyAppRule()

    @Test
    fun hub() {
        captureScreenshot { SettingsPage(navigator = NoOpNavigator) }
    }

    @Test
    fun appearance() {
        captureScreenshot { CustomSettingsPage(navigator = NoOpNavigator) }
    }

    @Test
    fun browsing() {
        captureScreenshot { HabitSettingsPage(navigator = NoOpNavigator) }
    }

    @Test
    fun posting() {
        captureScreenshot { PostSettingsPage(navigator = NoOpNavigator) }
    }

    @Test
    fun other() {
        captureScreenshot { MoreSettingsPage(navigator = NoOpNavigator) }
    }

    @Test
    fun signIn() {
        captureScreenshot { OKSignSettingsPage(navigator = NoOpNavigator) }
    }

    @Test
    fun account() {
        captureScreenshot { AccountManagePage(navigator = NoOpNavigator) }
    }

    @Test
    fun accountLoggedIn() {
        val current = Account(id = 1, uid = "1", name = "echo", nameShow = "幻星夜Echo")
        val other = Account(id = 2, uid = "2", name = "alt", nameShow = "小号")
        captureScreenshot {
            CompositionLocalProvider(
                AccountUtil.LocalAccount provides current,
                AccountUtil.AllAccounts provides listOf(current, other),
            ) {
                AccountManagePage(navigator = NoOpNavigator)
            }
        }
    }
}
