package oolong.client.ui.common.theme.compose

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.Window
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.suspendCancellableCoroutine
import oolong.client.util.findActivity
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/** Whether a theme above already fades its changes, so a theme inside it doesn't again. */
private val LocalThemeFades = compositionLocalOf { false }

/**
 * Shows [content] in the colours [target] gives, and when they change, changes them under a
 * picture of the window as it was, system bars' backgrounds included, which then fades away
 * (design-system.md §7.2), rather than every colour flipping in one frame. The old colours stay
 * until the picture is taken. Taking it needs Android 8; before, and in a theme inside another,
 * the colours change at once.
 */
@Composable
internal fun <T> ThemeChangeFade(target: T, content: @Composable (shown: T) -> Unit) {
    val outer = LocalThemeFades.current
    val window = LocalContext.current.findActivity()?.window
    if (outer || window == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        content(target)
        return
    }
    val resources = LocalContext.current.resources
    var shown by remember { mutableStateOf(target) }
    LaunchedEffect(target) {
        if (target == shown) return@LaunchedEffect
        val decor = window.decorView
        val taken = if (decor.width > 0 && decor.height > 0) capture(window, decor.width, decor.height) else null
        shown = target
        if (taken == null) return@LaunchedEffect
        // Over the whole window, above everything in it, as a drawable on its overlay
        val picture = BitmapDrawable(resources, taken).apply { setBounds(0, 0, decor.width, decor.height) }
        decor.overlay.add(picture)
        try {
            // The new colours draw once under the picture before it starts to go
            withFrameNanos {}
            Animatable(1f, Motion.ProgressThreshold).animateTo(0f, Motion.settle(Motion.ProgressThreshold)) {
                picture.alpha = (value.coerceIn(0f, 1f) * 255).roundToInt()
            }
        } finally {
            decor.overlay.remove(picture)
            taken.recycle()
        }
    }
    CompositionLocalProvider(LocalThemeFades provides true) { content(shown) }
}

/** The window's pixels as they are on screen now, glass and all, or null if they can't be had. */
@RequiresApi(Build.VERSION_CODES.O)
private suspend fun capture(window: Window, width: Int, height: Int): Bitmap? {
    val bitmap = runCatching { Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) }.getOrNull() ?: return null
    return suspendCancellableCoroutine { continuation ->
        runCatching {
            PixelCopy.request(window, bitmap, { result ->
                continuation.resume(bitmap.takeIf { result == PixelCopy.SUCCESS })
            }, Handler(Looper.getMainLooper()))
        }.onFailure { continuation.resume(null) }
    }
}
