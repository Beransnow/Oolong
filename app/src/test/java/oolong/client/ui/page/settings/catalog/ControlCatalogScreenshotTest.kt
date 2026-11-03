package oolong.client.ui.page.settings.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import oolong.client.testing.LegacyAppRule
import oolong.client.testing.captureScreenshot
import oolong.client.ui.common.theme.compose.Spacing
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
@Config(qualifiers = "+w393dp-h1200dp-xxhdpi")
class ControlCatalogScreenshotTest {
    @get:Rule
    val legacyApp = LegacyAppRule()

    @get:Rule
    val composeRule = createComposeRule()

    @After
    fun resetTheme() {
        ThemeUtil.updateSettings { ThemeSettings() }
    }

    private fun capture() = captureScreenshot {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            ControlCatalog()
        }
    }

    @Test
    fun light() = capture()

    @Test
    fun dark() {
        ThemeUtil.updateSettings { ThemeSettings(appearance = Appearance.Dark) }
        capture()
    }
}
