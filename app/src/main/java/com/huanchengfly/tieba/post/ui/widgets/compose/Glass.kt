package com.huanchengfly.tieba.post.ui.widgets.compose

import android.content.res.Configuration
import android.os.Build
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.huanchengfly.tieba.post.rememberPreferenceAsState
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.OpticalSizeValue
import dev.chrisbanes.haze.glass.RefractionProfile
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.hazeSource

/**
 * How floating chrome (bars, pills) is rendered. Every tier lays out identically; only the
 * material behind the chrome changes.
 */
enum class GlassTier {
    /** Refraction, blur and highlights. Needs RuntimeShader. */
    Glass,

    /** Backdrop blur without refraction. Needs RenderEffect. */
    Blur,

    /** Opaque colour, no backdrop sampling. */
    Solid;

    companion object {
        fun forDevice(): GlassTier = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> Glass
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> Blur
            else -> Solid
        }
    }
}

/** Forces a tier, for previews and screenshot tests. */
val LocalGlassTier = staticCompositionLocalOf<GlassTier?> { null }

/** Preference that renders all chrome as [GlassTier.Solid]. */
val ReduceTransparencyKey = booleanPreferencesKey("reduceTransparency")

@Composable
private fun currentGlassTier(): GlassTier {
    LocalGlassTier.current?.let { return it }
    val reduceTransparency by rememberPreferenceAsState(ReduceTransparencyKey, false)
    return if (reduceTransparency) GlassTier.Solid else GlassTier.forDevice()
}

/**
 * The content that floating chrome samples. Provided by [GlassScaffold]; null outside one, in
 * which case chrome falls back to [GlassTier.Solid].
 *
 * Pages usually leave their background transparent and let the window draw it, so the captured
 * content alone is not opaque: every material composites the theme background behind it first,
 * or the sharp content would show through the blur.
 */
val LocalGlassSource = staticCompositionLocalOf<HazeState?> { null }

/**
 * Background for a bar pinned to the top edge. The blur is strongest at the top and eases off
 * down the whole bar until it ends at the bottom edge; the tint follows the same curve. The curve
 * flattens out at the bottom, so content that has just scrolled under the bar only softens and
 * blurs more the further up it goes.
 *
 * Without a blur, an opaque background fades out over the last [fade] of the height instead.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun Modifier.topBarGlass(fade: Dp): Modifier {
    val source = LocalGlassSource.current
    val tier = currentGlassTier()
    val color = ExtendedTheme.colors.background
    if (source == null || tier == GlassTier.Solid) {
        var height by remember { mutableIntStateOf(0) }
        val fadePx = with(LocalDensity.current) { fade.toPx() }
        // The bar can change height, such as a header collapsing inside it, so this follows its size
        val solidFraction = if (height > 0) (1f - fadePx / height).coerceIn(0f, 1f) else 1f
        val solid = remember(color, solidFraction) {
            Brush.verticalGradient(
                0f to color,
                solidFraction to color,
                1f to color.copy(alpha = 0f),
            )
        }
        return onSizeChanged { height = it.height }.background(solid)
    }
    val style = remember(color) {
        HazeBlurStyle {
            backgroundColor(color)
            blurRadius(40.dp)
            noiseFactor(0f)
            progressive(
                HazeProgressive.verticalGradient(
                    easing = TopBarEasing,
                    startIntensity = 1f,
                    endIntensity = 0f,
                )
            )
            colorEffects(
                listOf(
                    HazeColorEffect.tint(
                        Brush.verticalGradient(
                            *Array(TopBarTintStops + 1) { i ->
                                val t = i / TopBarTintStops.toFloat()
                                t to color.copy(alpha = TopBarTint * (1f - TopBarEasing.transform(t)))
                            }
                        )
                    )
                )
            )
        }
    }
    // Full resolution draws the blur at a radius that really shrinks down the bar. At lower
    // resolutions Haze fades a blurred copy over the sharp content instead, which shows both.
    return hazeBlur(
        input = HazeInput.Sources(source),
        style = style,
        performanceMode = HazePerformanceMode.Quality,
    )
}

/** How a top bar's blur and tint ease off from its top (0) to its bottom edge (1): a smoothstep. */
private val TopBarEasing = Easing { t -> t * t * (3f - 2f * t) }

/** The tint at the top of a top bar, behind the status bar. */
private const val TopBarTint = 0.75f

/** Stops used to draw [TopBarEasing] as a gradient for the tint. */
private const val TopBarTintStops = 8

/**
 * Background for a floating, rounded piece of chrome such as the navigation pill.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun Modifier.floatingGlass(shape: RoundedCornerShape): Modifier {
    val source = LocalGlassSource.current
    val tier = if (source == null) GlassTier.Solid else currentGlassTier()
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
            .background(colors.background)
            .border(0.5.dp, edge, shape)
    }
}

/**
 * Lets a [glassLens] drawn above this chrome magnify it along with the content behind it. Apply
 * before the chrome's own material so the material is part of what the lens sees.
 */
@Composable
fun Modifier.lensTarget(): Modifier {
    val source = LocalGlassSource.current
    if (source == null || currentGlassTier() != GlassTier.Glass) return this
    return hazeSource(source, zIndex = LensTargetZIndex)
}

/**
 * A clear glass lens over chrome marked with [lensTarget], such as the bubble that lifts off the
 * navigation pill while it is pressed. Draws nothing below [GlassTier.Glass].
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun Modifier.glassLens(shape: RoundedCornerShape): Modifier {
    val source = LocalGlassSource.current
    if (source == null || currentGlassTier() != GlassTier.Glass) return this
    val background = ExtendedTheme.colors.background
    val style = remember(shape, background) {
        GlassStyle.clear.then {
            shape(shape)
            backgroundColor(background)
            // A lens magnifies what is under it; it does not frost it.
            optics(UnblurredClearOptics)
        }
    }
    // Not inside any source of this state, so it sees the content and the lens targets. A lens is
    // looked through, so it renders at full quality; it is small and only shown while pressed.
    return hazeGlass(
        input = HazeInput.Sources(source),
        style = style,
        performanceMode = HazePerformanceMode.Quality,
    )
}

/**
 * Clear's optics as Haze 2.0.1 defines them, minus its shallow blur. Haze 2.0.1 keeps them
 * internal; later versions expose them as `GlassOptics.clear` for `copy(blurRadius = …)`.
 */
@OptIn(ExperimentalHazeApi::class)
private val UnblurredClearOptics = GlassOptics(
    refractionStrength = 0.85f,
    refractionHeightFraction = 0.35f,
    refractionDisplacement = 56.dp,
    refractionProfile = RefractionProfile.Edge(28.dp),
    depth = OpticalSizeValue.Fixed(1f),
    blurRadius = OpticalSizeValue.Fixed(0.dp),
    refractionDetailIntensity = 0.76f,
)

/** Above the scaffold content, which is captured at the default zIndex of 0. */
private const val LensTargetZIndex = 1f

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
