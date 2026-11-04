package oolong.client.ui.widgets.compose

import androidx.compose.ui.graphics.Color
import oolong.client.ui.common.theme.compose.ExtendedColors
import oolong.client.utils.ThemeUtil

/**
 * Glass on black takes its tone from the page and would be black itself: over pictures and video
 * it is lifted to a grey of its own, so the controls stand off the black by tone.
 */
private val MediaChromeTone = Color(0xFF3A3B40)

/** [colors] as glass over pictures and video on black takes them. */
fun mediaChromeColors(colors: ExtendedColors): ExtendedColors = colors.copy(
    theme = if (ThemeUtil.isTranslucentTheme(colors.theme)) ThemeUtil.THEME_BLUE_DARK else colors.theme,
    background = MediaChromeTone,
)
