package com.huanchengfly.tieba.post.ui.page.main.user

import androidx.compose.ui.test.junit4.createComposeRule
import com.huanchengfly.tieba.post.models.database.Account
import com.huanchengfly.tieba.post.testing.LegacyAppRule
import com.huanchengfly.tieba.post.testing.captureScreenshot
import com.huanchengfly.tieba.post.testing.captureScreenshotPaused
import com.huanchengfly.tieba.post.utils.Appearance
import com.huanchengfly.tieba.post.utils.ThemeSettings
import com.huanchengfly.tieba.post.utils.ThemeUtil
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

    @Test
    fun loggedIn() {
        captureScreenshot { UserScreen(account = account, isLoading = false, onRefresh = {}) }
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
