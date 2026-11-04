package oolong.client.ui.page.main.user

import androidx.compose.ui.test.junit4.createComposeRule
import oolong.client.model.HistoryEntry
import oolong.client.models.database.Account
import oolong.client.testing.LegacyAppRule
import oolong.client.testing.captureScreenshot
import oolong.client.testing.captureScreenshotPaused
import oolong.client.utils.Appearance
import oolong.client.utils.ThemeSettings
import oolong.client.utils.ThemeUtil
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
class UserScreenScreenshotTest {
    @get:Rule
    val legacyApp = LegacyAppRule()

    @get:Rule
    val composeRule = createComposeRule()

    @After
    fun resetTheme() {
        ThemeUtil.updateSettings { ThemeSettings() }
    }

    private val account = Account(
        uid = "1",
        name = "echo",
        nameShow = "幻星夜Echo",
        intro = "咕咕咕",
        concernNum = "40",
        fansNum = "88",
        postNum = "1789",
    )

    private val lastThread = HistoryEntry.Thread(id = 1, time = 0, avatar = "", threadId = 7, title = "【攻略】新手入坑必看：从零开始的配队思路整理", authorName = "a")

    @Test
    fun loggedIn() {
        captureScreenshot { UserScreen(account = account, isLoading = false, onRefresh = {}) }
    }

    @Test
    fun withUpdates() {
        captureScreenshot {
            UserScreen(account = account, isLoading = false, onRefresh = {}, lastThread = lastThread, storedWithReplies = 3, updateAvailable = true)
        }
    }

    @Test
    @Config(qualifiers = "+en")
    fun loggedInEnglish() {
        captureScreenshot { UserScreen(account = account, isLoading = false, onRefresh = {}) }
    }

    @Test
    fun loggedInDark() {
        ThemeUtil.updateSettings { ThemeSettings(appearance = Appearance.Dark, darkTheme = ThemeUtil.THEME_AMOLED_DARK) }
        captureScreenshot { UserScreen(account = account, isLoading = false, onRefresh = {}) }
    }

    @Test
    fun guest() {
        captureScreenshot { UserScreen(account = null, isLoading = false, onRefresh = {}) }
    }

    @Test
    fun loading() {
        composeRule.captureScreenshotPaused { UserScreen(account = null, isLoading = true, onRefresh = {}) }
    }
}
