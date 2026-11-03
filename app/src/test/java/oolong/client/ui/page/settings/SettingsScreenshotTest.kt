package oolong.client.ui.page.settings

import androidx.compose.runtime.CompositionLocalProvider
import oolong.client.models.database.Account
import oolong.client.testing.LegacyAppRule
import oolong.client.testing.captureScreenshot
import oolong.client.ui.common.LocalAccount
import oolong.client.ui.common.LocalAllAccounts
import oolong.client.ui.navigation.NoOpNavigator
import oolong.client.ui.page.settings.account.AccountManagePage
import oolong.client.ui.page.settings.custom.CustomSettingsPage
import oolong.client.ui.page.settings.custom.FontSizePage
import oolong.client.ui.page.settings.habit.HabitSettingsPage
import oolong.client.ui.page.settings.more.MoreSettingsPage
import oolong.client.ui.page.settings.oksign.OKSignSettingsPage
import oolong.client.ui.page.settings.post.PostSettingsPage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "+w393dp-h851dp-xxhdpi")
class SettingsScreenshotTest {
    @get:Rule
    val legacyApp = LegacyAppRule()

    @Test
    fun hub() {
        captureScreenshot { SettingsPage(navigator = NoOpNavigator) }
    }

    @Test
    @Config(qualifiers = "+en")
    fun hubEnglish() {
        captureScreenshot { SettingsPage(navigator = NoOpNavigator) }
    }

    @Test
    fun appearance() {
        captureScreenshot { CustomSettingsPage(navigator = NoOpNavigator) }
    }

    @Test
    @Config(qualifiers = "+en")
    fun appearanceEnglish() {
        captureScreenshot { CustomSettingsPage(navigator = NoOpNavigator) }
    }

    @Test
    fun fontSize() {
        captureScreenshot { FontSizePage(navigator = NoOpNavigator) }
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
    @Config(qualifiers = "+en")
    fun otherEnglish() {
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
                LocalAccount provides current,
                LocalAllAccounts provides listOf(current, other),
            ) {
                AccountManagePage(navigator = NoOpNavigator)
            }
        }
    }
}
