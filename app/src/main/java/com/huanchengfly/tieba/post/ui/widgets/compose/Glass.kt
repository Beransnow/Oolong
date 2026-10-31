package com.huanchengfly.tieba.post.ui.widgets.compose

import android.content.res.Configuration
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass

/**
 * How floating chrome (bars, pills) is rendered. Every tier lays out identically; only the
 * material behind the chrome changes.
 */
enum class GlassTier {
    /** Refraction, blur and highlights. Needs RuntimeShader. */
    Glass,

    /** Backdrop blur without refraction. Needs RenderEffect. */
    Blur,

    /** Translucent colour, no backdrop sampling. */
    Solid;

    companion object {
        fun forDevice(): GlassTier = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> Glass
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> Blur
            else -> Solid
        }
    }
}

val LocalGlassTier = staticCompositionLocalOf { GlassTier.forDevice() }

/**
 * The content that floating chrome samples. Provided by [GlassScaffold]; null outside one, in
 * which case chrome falls back to [GlassTier.Solid].
 *
 * Pages usually leave their background transparent and let the window draw it, so the captured
 * content alone is not opaque: every material composites the theme background behind it first,
 * or the sharp content would show through the blur.
 */
val LocalGlassSource = staticCompositionLocalOf<HazeState?> { null }

private const val SolidAlpha = 0.94f

/**
 * Background for a bar pinned to the top edge. Blur and tint are strongest at the top and fade to
 * nothing at the bottom, so the bar has no hard edge over the content.
 *
 * @param solidFraction fraction of the height, from the top, that stays fully tinted.
 */
@Composable
fun Modifier.topBarGlass(solidFraction: Float = 0.6f): Modifier {
    val source = LocalGlassSource.current
    val tier = LocalGlassTier.current
    val color = ExtendedTheme.colors.background
    val tint = remember(color, solidFraction) {
        Brush.verticalGradient(
            0f to color.copy(alpha = 0.9f),
            solidFraction to color.copy(alpha = 0.78f),
            1f to color.copy(alpha = 0f),
        )
    }
    if (source == null || tier == GlassTier.Solid) {
        val solid = remember(color, solidFraction) {
            Brush.verticalGradient(
                0f to color.copy(alpha = SolidAlpha),
                solidFraction to color.copy(alpha = SolidAlpha),
                1f to color.copy(alpha = 0f),
            )
        }
        return background(solid)
    }
    val style = remember(color, tint) {
        HazeBlurStyle {
            backgroundColor(color)
            blurRadius(24.dp)
            noiseFactor(0f)
            progressive(
                HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f)
            )
            colorEffects(listOf(HazeColorEffect.tint(tint)))
        }
    }
    return hazeBlur(input = HazeInput.Sources(source), style = style)
}

/**
 * Background for a floating, rounded piece of chrome such as the navigation pill.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun Modifier.floatingGlass(shape: RoundedCornerShape): Modifier {
    val source = LocalGlassSource.current
    val tier = if (source == null) GlassTier.Solid else LocalGlassTier.current
    val colors = ExtendedTheme.colors
    val shadowed = shadow(
        elevation = 12.dp,
        shape = shape,
        ambientColor = Color.Black.copy(alpha = 0.10f),
        spotColor = Color.Black.copy(alpha = 0.16f),
    )
    val edge = if (colors.isNightMode) Color.White.copy(alpha = 0.10f)
    else Color.Black.copy(alpha = 0.06f)
    return when (tier) {
        GlassTier.Glass -> {
            val style = remember(shape, colors.background) {
                // Regular is the built-in material for surfaces that carry labels.
                GlassStyle.regular.then {
                    shape(shape)
                    backgroundColor(colors.background)
                }
            }
            shadowed.hazeGlass(input = HazeInput.Sources(source!!), style = style)
        }

        GlassTier.Blur -> {
            val style = remember(colors.background) {
                HazeBlurStyle {
                    backgroundColor(colors.background)
                    blurRadius(20.dp)
                    noiseFactor(0f)
                    colorEffects(listOf(HazeColorEffect.tint(colors.background.copy(alpha = 0.72f))))
                }
            }
            shadowed
                .clip(shape)
                .hazeBlur(input = HazeInput.Sources(source!!), style = style)
                .border(0.5.dp, edge, shape)
        }

        GlassTier.Solid -> shadowed
            .clip(shape)
            .background(colors.background.copy(alpha = SolidAlpha))
            .border(0.5.dp, edge, shape)
    }
}

/**
 * Glass follows the light/dark appearance of [LocalConfiguration], but the app picks its own
 * theme independently of the system. Hosts glass in a configuration that matches the app theme.
 */
@Composable
fun ProvideGlassAppearance(content: @Composable () -> Unit) {
    val configuration = LocalConfiguration.current
    val night = ExtendedTheme.colors.isNightMode
    val adjusted = remember(configuration, night) {
        Configuration(configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
    }
    CompositionLocalProvider(LocalConfiguration provides adjusted, content = content)
}
