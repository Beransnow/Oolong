package com.huanchengfly.tieba.post.ui.page.settings

import com.huanchengfly.tieba.post.testing.LegacyAppRule
import com.huanchengfly.tieba.post.testing.captureScreenshot
import com.huanchengfly.tieba.post.ui.page.settings.custom.CustomSettingsPage
import com.huanchengfly.tieba.post.ui.page.settings.habit.HabitSettingsPage
import com.huanchengfly.tieba.post.ui.page.settings.more.MoreSettingsPage
import com.huanchengfly.tieba.post.ui.page.settings.post.PostSettingsPage
import com.ramcosta.composedestinations.navigation.EmptyDestinationsNavigator
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
        captureScreenshot { SettingsPage(navigator = EmptyDestinationsNavigator) }
    }

    @Test
    fun appearance() {
        captureScreenshot { CustomSettingsPage(navigator = EmptyDestinationsNavigator) }
    }

    @Test
    fun browsing() {
        captureScreenshot { HabitSettingsPage(navigator = EmptyDestinationsNavigator) }
    }

    @Test
    fun posting() {
        captureScreenshot { PostSettingsPage(navigator = EmptyDestinationsNavigator) }
    }

    @Test
    fun other() {
        captureScreenshot { MoreSettingsPage(navigator = EmptyDestinationsNavigator) }
    }
}
