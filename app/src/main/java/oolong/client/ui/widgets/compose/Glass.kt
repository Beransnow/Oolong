package oolong.client.ui.widgets.compose

import android.content.res.Configuration
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeSourceSelection
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
import oolong.client.rememberPreferenceAsState
import oolong.client.ui.common.theme.compose.ExtendedColors
import oolong.client.ui.common.theme.compose.ExtendedTheme
import oolong.client.utils.ThemeUtil

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
 * What floating chrome samples: the sources in [state] keyed [key], plus the translucent theme's
 * wallpaper if it is in the same state. A null key takes every source.
 */
@Immutable
class GlassSource(val state: HazeState, val key: Any? = null) {
    val input: HazeInput.Sources = if (key == null) {
        HazeInput.Sources(state)
    } else {
        HazeInput.Sources(state, HazeSourceSelection.Behind.where { it.key === key || it.key === WallpaperKey })
    }
}

/**
 * The content that floating chrome samples. Provided by [GlassScaffold]; outside one, chrome
 * samples only the wallpaper on the translucent theme, and otherwise falls back to
 * [GlassTier.Solid].
 *
 * Pages usually leave their background transparent and let the window draw it, so the captured
 * content alone is not opaque: every material composites the theme background behind it first,
 * or the sharp content would show through the blur.
 */
val LocalGlassSource = staticCompositionLocalOf<GlassSource?> { null }

/**
 * The state the translucent theme's wallpaper is captured in, once under every page, so that
 * chrome blurs the wallpaper along with its page and the wallpaper stays put as pages slide.
 * Null on other themes.
 */
val LocalWallpaperGlass = staticCompositionLocalOf<HazeState?> { null }

/** Captures the translucent theme's wallpaper for [LocalWallpaperGlass]. */
fun Modifier.wallpaperGlassSource(state: HazeState): Modifier =
    hazeSource(state, zIndex = WallpaperZIndex, key = WallpaperKey)

private object WallpaperKey

/** Below the scaffold content, which is captured at the default zIndex of 0. */
private const val WallpaperZIndex = -1f

@Composable
private fun currentGlassSource(): GlassSource? =
    LocalGlassSource.current ?: LocalWallpaperGlass.current?.let { remember(it) { GlassSource(it, WallpaperKey) } }

/**
 * What chrome is tinted with: the page's background, except on the translucent theme, whose
 * background is clear: black under its light text, white under its dark text.
 */
private val ExtendedColors.chromeTint: Color
    get() = when {
        !ThemeUtil.isTranslucentTheme(theme) -> background
        theme.contains("light") -> Color.Black
        else -> Color.White
    }

private val ExtendedColors.isTranslucent: Boolean
    get() = ThemeUtil.isTranslucentTheme(theme)

/**
 * Background for a bar pinned to the top edge, like iOS's scroll edge. At the top of the page
 * nothing is under the bar, so it is clear; as the content scrolls up under it (see
 * [ScrollEdgeState]) a veil of the page's background and a blur come in, following the scroll.
 * The veil eases out over the whole height, level at both ends so it shows no line. The blur is
 * full down to [TopBarBlurFade] above the bottom edge, which the bar sets [TopBarEdgeExtend] below
 * its own, so content is blurred soon after it passes under the bar and fully by the title.
 *
 * Without a blur, an opaque background fades out over the last [TopBarSolidFade] instead.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun Modifier.topBarGlass(): Modifier {
    val source = currentGlassSource()
    val tier = currentGlassTier()
    val colors = ExtendedTheme.colors
    val color = colors.chromeTint
    val tint = if (colors.isTranslucent) TranslucentTopBarTint else TopBarTint
    val solid = source == null || tier == GlassTier.Solid
    var height by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val scrollEdge = LocalScrollEdge.current
    val revealPx = with(density) { TopBarEdgeReveal.toPx() }
    val shown: () -> Float = remember(scrollEdge, revealPx) {
        { scrollEdge?.scrolled?.let { (it / revealPx).coerceIn(0f, 1f) } ?: 1f }
    }
    val clear by remember(shown) { derivedStateOf { shown() == 0f } }
    // Where the fades start. The bar can change height, such as a header collapsing inside it,
    // so these follow its size; a bar shorter than a fade fades over all of it.
    val solidFadePx = with(density) { TopBarSolidFade.toPx() }
    val solidEdge = if (height > 0) (1f - solidFadePx / height).coerceIn(0f, 1f) else 0f
    val blurFadePx = with(density) { TopBarBlurFade.toPx() }
    val blurEdge = if (height > 0) (1f - blurFadePx / height).coerceIn(0f, 1f) else 0f
    val measured = onSizeChanged { height = it.height }
    // Nothing to draw, and no blur to run, while the bar is clear
    if (clear) return measured
    if (solid) {
        val top = if (colors.isTranslucent) color.copy(alpha = TranslucentTopBarTint) else color
        val brush = remember(top, solidEdge) {
            Brush.verticalGradient(
                0f to top,
                solidEdge to top,
                1f to top.copy(alpha = 0f),
            )
        }
        return measured.graphicsLayer { alpha = shown() }.background(brush)
    }
    val style = remember(color, tint, blurEdge) {
        HazeBlurStyle {
            // Read here, the style follows the scroll without being rebuilt
            val strength = shown()
            backgroundColor(colors.background)
            blurRadius(TopBarBlur * strength)
            noiseFactor(0f)
            progressive(
                HazeProgressive.verticalGradient(
                    // Full strength down to the stretch above the edge, then eased out
                    easing = { t ->
                        val x = if (t <= blurEdge) 0f else ((t - blurEdge) / (1f - blurEdge)).coerceIn(0f, 1f)
                        x * x * (3f - 2f * x)
                    },
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
                                // A smootherstep over the whole height: no flat band, no line
                                val gone = t * t * t * (t * (t * 6f - 15f) + 10f)
                                t to color.copy(alpha = tint * strength * (1f - gone))
                            }
                        )
                    )
                )
            )
        }
    }
    // Full resolution draws the blur at a radius that really shrinks down the bar. At lower
    // resolutions Haze fades a blurred copy over the sharp content instead, which shows both.
    return measured.hazeBlur(
        input = source!!.input,
        style = style,
        performanceMode = HazePerformanceMode.Quality,
    )
}

/** How strongly the scroll edge blurs the content under it. */
private val TopBarBlur = 12.dp

/** How far above its bottom edge the scroll edge's blur eases out. */
private val TopBarBlurFade = 48.dp

/** How far the content scrolls before the scroll edge is fully in. */
private val TopBarEdgeReveal = 32.dp

/** How far the scroll edge reaches below the bar, over the content. */
internal val TopBarEdgeExtend = 24.dp

/** The veil at the top of the scroll edge, of the page's background colour. */
private const val TopBarTint = 0.5f

/** How far above its bottom edge an opaque top bar fades out, kept short so it stays opaque. */
private val TopBarSolidFade = 16.dp

/** The translucent theme's lighter tint, which keeps the wallpaper showing through. */
private const val TranslucentTopBarTint = 0.45f

/** Stops used to draw the tint's curve as a gradient. */
private const val TopBarTintStops = 32

/**
 * Background for a floating, rounded piece of chrome such as the navigation pill.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun Modifier.floatingGlass(shape: RoundedCornerShape, blur: Dp? = null): Modifier {
    val source = currentGlassSource()
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
    if (colors.isTranslucent && tier != GlassTier.Solid) {
        // Over a wallpaper only a light blur, untinted, so the wallpaper still shows through
        return shadowed
            .clip(shape)
            .hazeBlur(input = source!!.input, style = TranslucentFloatingStyle)
            .border(0.5.dp, edge, shape)
    }
    return when (tier) {
        GlassTier.Glass -> {
            val style = remember(shape, colors.background, blur) {
                // Regular is the built-in material for surfaces that carry labels.
                GlassStyle.regular.then {
                    shape(shape)
                    backgroundColor(colors.background)
                    lightPosition(LightAbove)
                    // Regular blurs a compact control only partly, keeping the content under it
                    // sharp; with a blur, all of it is frosted, by that much
                    if (blur != null) optics { copy(depth = OpticalSizeValue.Fixed(1f), blurRadius = OpticalSizeValue.Fixed(blur)) }
                }
            }
            shadowed.hazeGlass(input = source!!.input, style = style)
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
                .hazeBlur(input = source!!.input, style = style)
                .border(0.5.dp, edge, shape)
        }

        GlassTier.Solid -> shadowed
            .clip(shape)
            .background(if (colors.isTranslucent) colors.chromeTint.copy(alpha = TranslucentSolidTint) else colors.background)
            .border(0.5.dp, edge, shape)
    }
}

/**
 * The one light, straight above (design-system.md §2.2), far enough that it falls evenly along a
 * capsule's top. Haze's default, the middle of each shape, lights a capsule's long edges at their
 * middles and leaves its ends dark, pinching it in. Its highlight falls on the edge away from the
 * light's bias, so the bias points down to light the top.
 */
private val LightAbove = BiasAlignment(horizontalBias = 0f, verticalBias = 8f)

@OptIn(ExperimentalHazeApi::class)
private val TranslucentFloatingStyle = HazeBlurStyle {
    backgroundColor(Color.Transparent)
    blurRadius(3.dp)
    noiseFactor(0f)
}

/** Without a blur, floating chrome on the translucent theme needs some tint to stand apart. */
private const val TranslucentSolidTint = 0.35f

/**
 * Lets glass drawn above this chrome see it along with the content behind it: a [glassLens] that
 * magnifies it, or buttons floating on a top bar. Apply before the chrome's own material so the
 * material is part of what that glass sees.
 */
@Composable
fun Modifier.lensTarget(): Modifier {
    val source = currentGlassSource()
    if (source == null || currentGlassTier() != GlassTier.Glass) return this
    return hazeSource(source.state, zIndex = LensTargetZIndex, key = source.key)
}

/**
 * A clear glass lens over chrome marked with [lensTarget], such as the bubble that lifts off the
 * navigation pill while it is pressed. Draws nothing below [GlassTier.Glass].
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun Modifier.glassLens(shape: RoundedCornerShape): Modifier {
    val source = currentGlassSource()
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
        input = source.input,
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

/** Whether glass is drawn: the device supports it and reduce transparency is off. */
@Composable
fun isGlassAvailable(): Boolean = currentGlassTier() == GlassTier.Glass

/**
 * A control's part that turns to glass while held, such as a switch's knob (design-system.md
 * §8.1): clear glass, like the navigation bar's lens, over [state], faded in by [alpha]. [state]
 * should capture everything the part can cover, transparent where the control draws nothing, so
 * what lies beyond the control shows through as it is. Only on [GlassTier.Glass].
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun Modifier.controlLens(state: HazeState, shape: RoundedCornerShape, alpha: Float): Modifier {
    if (alpha <= 0f || currentGlassTier() != GlassTier.Glass) return this
    val style = remember(shape, alpha) {
        GlassStyle.clear.then {
            shape(shape)
            backgroundColor(Color.Transparent)
            optics(ControlLensOptics)
            alpha(alpha)
        }
    }
    return hazeGlass(input = HazeInput.Sources(state), style = style, performanceMode = HazePerformanceMode.Quality)
}

/**
 * [UnblurredClearOptics] for a control a few dozen dp across: refraction only at the rim, so what is
 * under the part reads sharply, as through the navigation bar's lens.
 */
@OptIn(ExperimentalHazeApi::class)
private val ControlLensOptics = GlassOptics(
    refractionStrength = 0.5f,
    refractionHeightFraction = 0.35f,
    refractionDisplacement = 6.dp,
    refractionProfile = RefractionProfile.Edge(6.dp),
    depth = OpticalSizeValue.Fixed(1f),
    blurRadius = OpticalSizeValue.Fixed(0.dp),
    refractionDetailIntensity = 0.76f,
)

/**
 * A large clear lens over [state], such as the one lifted off the icon on the about page: like
 * [controlLens], but bending what it covers from further in from its rim, as a thicker glass does.
 * Only on [GlassTier.Glass].
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun Modifier.loupeLens(state: HazeState, shape: RoundedCornerShape): Modifier {
    if (currentGlassTier() != GlassTier.Glass) return this
    // On the page's ground, as pages leave theirs to the window and what lies under the lens would show
    val background = ExtendedTheme.colors.background
    val style = remember(shape, background) {
        GlassStyle.clear.then {
            shape(shape)
            backgroundColor(background)
            optics(LoupeOptics)
        }
    }
    return hazeGlass(input = HazeInput.Sources(state), style = style, performanceMode = HazePerformanceMode.Quality)
}

/** Clear glass, unblurred, with a deep rim for a lens a hundred dp or more across. */
@OptIn(ExperimentalHazeApi::class)
private val LoupeOptics = GlassOptics.clear.copy(
    refractionDisplacement = 40.dp,
    refractionProfile = RefractionProfile.Edge(40.dp),
    blurRadius = OpticalSizeValue.Fixed(0.dp),
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
