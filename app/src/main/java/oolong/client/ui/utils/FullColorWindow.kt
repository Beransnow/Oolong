package oolong.client.ui.utils

import android.content.pm.ActivityInfo
import android.os.Build
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import oolong.client.findActivity
import java.util.WeakHashMap

/** How many of those in composition ask each window for full colour. */
private val asking = WeakHashMap<Window, Int>()

/**
 * While in composition, the window shows pictures in their full colour: a wide-gamut one beyond
 * sRGB on a screen that can, and from Android 14 an HDR one brighter than white. Only where
 * pictures are looked at, as the rest of the app is sRGB and a wide-gamut window can cost more
 * memory and power. Several may ask at once; the window goes back when the last is gone.
 */
@Composable
fun FullColorWindow() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        val mode = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> ActivityInfo.COLOR_MODE_HDR
            view.display?.isWideColorGamut == true -> ActivityInfo.COLOR_MODE_WIDE_COLOR_GAMUT
            else -> null
        }
        if (window == null || mode == null) return@DisposableEffect onDispose {}
        val count = asking[window] ?: 0
        asking[window] = count + 1
        if (count == 0) window.colorMode = mode
        onDispose {
            val left = (asking[window] ?: 1) - 1
            if (left == 0) {
                asking.remove(window)
                window.colorMode = ActivityInfo.COLOR_MODE_DEFAULT
            } else {
                asking[window] = left
            }
        }
    }
}
