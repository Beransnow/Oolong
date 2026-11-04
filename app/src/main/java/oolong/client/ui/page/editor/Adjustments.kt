package oolong.client.ui.page.editor

import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.os.Build
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import java.nio.IntBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** What the adjust tool changes, each from -100 to 100, 0 leaving it as it is. */
enum class Adjustment { Exposure, Brightness, Contrast, Highlights, Shadows, Saturation, Warmth }

/** Adjustments with nothing changed. */
val NoAdjustments: Map<Adjustment, Float> = emptyMap()

/** Whether [values] change anything. */
fun Map<Adjustment, Float>.changesNothing(): Boolean = values.all { it == 0f }

/**
 * [source] with [values] applied, at most [maxSide] on its longer side when given. The tone,
 * from exposure to shadows, goes through one curve on each channel; saturation and warmth are
 * mixed in after. The same for the screen and for the file, so they look alike.
 */
fun adjust(source: Bitmap, values: Map<Adjustment, Float>, maxSide: Int? = null): Bitmap {
    val scaled = if (maxSide != null) fitted(source, maxSide) else source
    if (values.changesNothing()) return scaled
    val curve = toneCurve(values)
    val saturation = 1f + (values[Adjustment.Saturation] ?: 0f) / 100f
    val warmth = (values[Adjustment.Warmth] ?: 0f) / 100f
    val warmR = 1f + 0.12f * warmth
    val warmB = 1f - 0.12f * warmth
    val warmG = 1f + 0.02f * warmth
    // The raw pixels, in the picture's own colour space, so a wide-gamut one stays as wide.
    // Each comes as its bytes in memory, red first, so on this little-endian int as ABGR.
    val source8888 = if (scaled.config == Bitmap.Config.ARGB_8888) scaled else scaled.copy(Bitmap.Config.ARGB_8888, false)
    val pixels = IntBuffer.allocate(source8888.width * source8888.height)
    source8888.copyPixelsToBuffer(pixels)
    val data = pixels.array()
    for (i in data.indices) {
        val c = data[i]
        val a = (c ushr 24) and 0xFF
        if (a == 0) continue
        // Stored with alpha multiplied in; the curve goes on the colour itself
        fun unpremultiply(v: Int) = if (a == 255) v else (v * 255 + a / 2) / a
        var r = curve[unpremultiply(c and 0xFF).coerceAtMost(255)]
        var g = curve[unpremultiply((c shr 8) and 0xFF).coerceAtMost(255)]
        var b = curve[unpremultiply((c shr 16) and 0xFF).coerceAtMost(255)]
        if (saturation != 1f) {
            val luma = 0.299f * r + 0.587f * g + 0.114f * b
            r = luma + (r - luma) * saturation
            g = luma + (g - luma) * saturation
            b = luma + (b - luma) * saturation
        }
        if (warmth != 0f) {
            r *= warmR
            g *= warmG
            b *= warmB
        }
        fun premultiply(v: Float) = if (a == 255) channel(v) else (channel(v) * a + 127) / 255
        data[i] = (a shl 24) or (premultiply(b) shl 16) or (premultiply(g) shl 8) or premultiply(r)
    }
    val result = bitmapLike(source8888, source8888.width, source8888.height)
    result.copyPixelsFromBuffer(pixels.rewind())
    return result
}

/**
 * A bitmap [width] by [height] in the colour space of [like], so a wide-gamut picture drawn or
 * written into it stays as wide. Before Android 8 every bitmap is sRGB.
 */
fun bitmapLike(like: Bitmap, width: Int, height: Int): Bitmap =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        createBitmap(width, height, Bitmap.Config.ARGB_8888, true, like.colorSpace ?: ColorSpace.get(ColorSpace.Named.SRGB))
    } else {
        createBitmap(width, height)
    }

private fun channel(v: Float) = v.roundToInt().coerceIn(0, 255)

/** The tone curve, as the 0–255 value each 0–255 input comes out at. */
private fun toneCurve(values: Map<Adjustment, Float>): FloatArray {
    val exposure = (values[Adjustment.Exposure] ?: 0f) / 100f
    val brightness = (values[Adjustment.Brightness] ?: 0f) / 100f
    val contrast = (values[Adjustment.Contrast] ?: 0f) / 100f
    val highlights = (values[Adjustment.Highlights] ?: 0f) / 100f
    val shadows = (values[Adjustment.Shadows] ?: 0f) / 100f
    val gain = 2f.pow(exposure)
    val slope = if (contrast >= 0f) 1f + contrast * 0.8f else 1f + contrast * 0.6f
    return FloatArray(256) { i ->
        var x = i / 255f
        // Exposure: a stop either way at the ends
        x *= gain
        // Brightness: the middle tones most, the ends kept
        x += brightness * 0.3f * 4f * x * (1f - x).coerceAtLeast(0f)
        // Contrast: about the middle
        x = (x - 0.5f) * slope + 0.5f
        x = x.coerceIn(0f, 1f)
        // Highlights and shadows: only the bright, or only the dark, part moves
        x += highlights * 0.25f * smoothstep(0.45f, 1f, x)
        x += shadows * 0.3f * (1f - smoothstep(0f, 0.55f, x))
        x.coerceIn(0f, 1f) * 255f
    }
}

private fun smoothstep(from: Float, to: Float, x: Float): Float {
    val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** [source] no longer than [maxSide] on its longer side: itself when it is not. */
fun fitted(source: Bitmap, maxSide: Int): Bitmap {
    if (max(source.width, source.height) <= maxSide) return source
    val s = maxSide.toFloat() / max(source.width, source.height)
    return source.scale((source.width * s).roundToInt().coerceAtLeast(1), (source.height * s).roundToInt().coerceAtLeast(1))
}

/** The longest side of the copy adjusted while the ruler moves: small enough to keep up with it. */
const val MovingSide = 1024

/** The longest side the screen's copy of an adjusted picture needs. */
fun previewSide(width: Int, height: Int): Int = min(max(width, height), 2048)
