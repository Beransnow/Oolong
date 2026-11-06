package oolong.client.ui.common.theme.compose

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import oolong.client.R
import kotlin.math.abs

/**
 * The app's text size: the system's, or one the user chooses from [Steps]. Large sizes grow text
 * that is already large less than small text, as Android 14 and later do with the system's size,
 * so headings don't outgrow the layouts they sit in.
 */
object TextScale {
    /** The sizes the user can choose, as scales of the text's own size. */
    val Steps = listOf(0.8f, 0.9f, 1f, 1.1f, 1.2f, 1.35f, 1.5f, 1.75f, 2f)

    /** The size chosen, used while not following the system's. */
    val ScaleKey = floatPreferencesKey("fontScale")

    /** Whether the app follows the system's text size. Unset, it does unless a size was chosen before there was a choice. */
    val FollowSystemKey = booleanPreferencesKey("fontScaleFollowSystem")

    /** The bars, tabs and buttons around the content take text this large at most, so it fits their fixed height. */
    const val ChromeMax = 1.3f

    /** Whether the system's size is followed when the setting is unset: unless a size other than the default was chosen. */
    fun followsByDefault(chosen: Float?) = chosen == null || abs(chosen - 1f) < 0.001f

    /** The step nearest [scale]. */
    fun stepOf(scale: Float): Int = Steps.indices.minBy { abs(Steps[it] - scale) }

    /** What the size at [step] is called. */
    @StringRes
    fun nameOf(step: Int): Int = Names[step]

    private val Names = listOf(
        R.string.text_size_small,
        R.string.text_size_little_small,
        R.string.text_size_default,
        R.string.text_size_little_large,
        R.string.text_size_large,
        R.string.text_size_larger,
        R.string.text_size_very_large,
        R.string.text_size_huge,
        R.string.text_size_largest,
    )
}

/** Whether the system's text size is followed, and the size chosen for when it isn't. */
data class TextScaleChoice(val followSystem: Boolean, val scale: Float)

fun Preferences.textScaleChoice(): TextScaleChoice {
    val chosen = this[TextScale.ScaleKey]
    return TextScaleChoice(followSystem = this[TextScale.FollowSystemKey] ?: TextScale.followsByDefault(chosen), scale = chosen ?: 1f)
}

/** The text scale in effect here, the system's or the app's, before [CapTextScale] caps it. */
val LocalTextScale = staticCompositionLocalOf { 1f }

/** A density with [density] pixels per dp whose text grows by [scale], large text less so. */
fun textScaledDensity(density: Float, scale: Float): Density = ScaledDensity(density, scale, converterFor(scale))

/** [content] with text no larger than [max] times its own size, as in bars of a fixed height. */
@Composable
fun CapTextScale(max: Float = TextScale.ChromeMax, content: @Composable () -> Unit) {
    val scale = LocalTextScale.current
    if (scale <= max) {
        content()
    } else {
        CompositionLocalProvider(
            LocalDensity provides textScaledDensity(LocalDensity.current.density, max),
            LocalTextScale provides max,
            content = content,
        )
    }
}

private class ScaledDensity(override val density: Float, override val fontScale: Float, private val converter: Converter) : Density {
    override fun TextUnit.toDp(): Dp {
        check(type == androidx.compose.ui.unit.TextUnitType.Sp) { "Only sp can convert to dp" }
        return converter.toDp(value).dp
    }

    override fun Dp.toSp(): TextUnit = converter.toSp(value).sp
}

/** Sizes in sp to sizes in dp, by straight lines between points, and beyond them by the last ratio. */
private class Converter(private val sp: FloatArray, private val dp: FloatArray) {
    fun toDp(value: Float): Float = map(value, sp, dp)

    fun toSp(value: Float): Float = map(value, dp, sp)

    private fun map(value: Float, from: FloatArray, to: FloatArray): Float {
        val sign = if (value < 0) -1 else 1
        val size = abs(value)
        val index = from.indexOfFirst { it >= size }
        val result = when {
            index == 0 -> size * to[0] / from[0]
            index < 0 -> size * to.last() / from.last()
            else -> {
                val start = index - 1
                val t = (size - from[start]) / (from[index] - from[start])
                to[start] + (to[index] - to[start]) * t
            }
        }
        return sign * result
    }
}

private val CommonSp = floatArrayOf(8f, 10f, 12f, 14f, 18f, 20f, 24f, 30f, 100f)

/** The sizes in dp that the sizes in [CommonSp] take at each scale, the system's own curves. */
private val Tables = listOf(
    1.05f to floatArrayOf(8.4f, 10.5f, 12.6f, 14.8f, 18.6f, 20.6f, 24.4f, 30f, 100f),
    1.1f to floatArrayOf(8.8f, 11f, 13.2f, 15.6f, 19.2f, 21.2f, 24.8f, 30f, 100f),
    1.15f to floatArrayOf(9.2f, 11.5f, 13.8f, 16.4f, 19.8f, 21.8f, 25.2f, 30f, 100f),
    1.2f to floatArrayOf(9.6f, 12f, 14.4f, 17.2f, 20.4f, 22.4f, 25.6f, 30f, 100f),
    1.3f to floatArrayOf(10.4f, 13f, 15.6f, 18.8f, 21.6f, 23.6f, 26.4f, 30f, 100f),
    1.5f to floatArrayOf(12f, 15f, 18f, 22f, 24f, 26f, 28f, 30f, 100f),
    1.8f to floatArrayOf(14.4f, 18f, 21.6f, 24.4f, 27.6f, 30.8f, 32.8f, 34.8f, 100f),
    2f to floatArrayOf(16f, 20f, 24f, 26f, 30f, 34f, 36f, 38f, 100f),
)

/** Straight up to just above 1, then the curve for [scale], between two tables where it falls between them. */
private fun converterFor(scale: Float): Converter {
    if (scale < Tables.first().first - 0.01f) return Converter(floatArrayOf(1f), floatArrayOf(scale))
    Tables.firstOrNull { abs(it.first - scale) < 0.001f }?.let { return Converter(CommonSp, it.second) }
    val upper = Tables.indexOfFirst { it.first > scale }
    if (upper <= 0) return Converter(floatArrayOf(1f), floatArrayOf(scale))
    val (startScale, start) = Tables[upper - 1]
    val (endScale, end) = Tables[upper]
    val t = ((scale - startScale) / (endScale - startScale)).coerceIn(0f, 1f)
    return Converter(CommonSp, FloatArray(CommonSp.size) { start[it] + (end[it] - start[it]) * t })
}
