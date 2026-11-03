package oolong.client.ui.common.windowsizeclass

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/** The size class of the window the pages are drawn in, as [oolong.client.arch.AppContent] provides it. */
val LocalWindowSizeClass = staticCompositionLocalOf {
    WindowSizeClass.calculateFromSize(DpSize(0.dp, 0.dp))
}
