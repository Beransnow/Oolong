package oolong.client.ui.widgets.compose

import android.content.res.Configuration
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
import oolong.client.ui.common.theme.compose.Motion
import oolong.client.ui.common.theme.compose.ShadowAmbient
import oolong.client.ui.common.theme.compose.ShadowSpot
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

/** The old switch that rendered all chrome as [GlassTier.Solid], still read until a material is chosen. */
val ReduceTransparencyKey = booleanPreferencesKey("reduceTransparency")

/** The material chosen for chrome, a [GlassTier]'s name; unset, the best the device draws. */
val GlassMaterialKey = stringPreferencesKey("glassMaterial")

/**
 * How clear glass is, from 0, frosted, to 1, clear; [DefaultGlassClarity] in the middle. It moves
 * how much glass blurs and how much of the page's colour it lays over what it shows.
 */
val GlassClarityKey = floatPreferencesKey("glassClarity")

const val DefaultGlassClarity = 0.5f

/** The tiers this device can draw, best first. */
val GlassTier.Companion.available: List<GlassTier>
    get() = GlassTier.entries.drop(GlassTier.entries.indexOf(GlassTier.forDevice()))

/** The material chosen for chrome, as far as the device draws it. */
@Composable
fun chosenGlassTier(): GlassTier {
    val chosen by rememberPreferenceAsState(GlassMaterialKey, "")
    val reduceTransparency by rememberPreferenceAsState(ReduceTransparencyKey, false)
    val tier = GlassTier.entries.firstOrNull { it.name == chosen }
        ?: if (reduceTransparency) GlassTier.Solid else GlassTier.forDevice()
    return maxOf(tier, GlassTier.forDevice())
}

@Composable
internal fun currentGlassTier(): GlassTier = LocalGlassTier.current ?: chosenGlassTier()

/**
 * How much more than at [DefaultGlassClarity] glass blurs and tints: 1 there, more towards
 * frosted, less towards clear.
 */
@Composable
internal fun glassFrost(): Float {
    val clarity by rememberPreferenceAsState(GlassClarityKey, DefaultGlassClarity)
    return frostAt(clarity)
}

private fun frostAt(clarity: Float): Float = if (clarity < DefaultGlassClarity) {
    lerp(MostFrost, 1f, clarity / DefaultGlassClarity)
} else {
    lerp(1f, LeastFrost, (clarity - DefaultGlassClarity) / (1f - DefaultGlassClarity))
}

private const val MostFrost = 2f
private const val LeastFrost = 0.25f

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
    val frost = glassFrost()
    val tint = ((if (colors.isTranslucent) TranslucentTopBarTint else TopBarTint) * frost).coerceAtMost(MaxTint)
    val solid = source == null || tier == GlassTier.Solid
    var height by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val scrollEdge = LocalPageScroll.current
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
    val style = remember(color, tint, blurEdge, frost) {
        HazeBlurStyle {
            // Read here, the style follows the scroll without being rebuilt
            val strength = shown()
            backgroundColor(colors.background)
            blurRadius(TopBarBlur * frost * strength)
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
internal val TopBarEdgeReveal = 32.dp

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
 * What a floating top bar tells the glass of the buttons in its row: how far it has come in, from
 * bare at the top of a page to full once content is under the bar.
 */
@Immutable
internal class BarButtons(val shown: () -> Float)

/** The top bar's buttons' glass, while drawing them; null elsewhere. */
internal val LocalBarButtons = staticCompositionLocalOf<BarButtons?> { null }

/**
 * Background for a floating, rounded piece of chrome such as the navigation pill. The content
 * under it is frosted all over, by [FloatingBlur] at the default clarity, and veiled in the page's
 * colour only on the frosted side of it.
 *
 * As a [barButton] in a floating top bar's row, the glass and its shadow come in as content
 * scrolls under the bar, so at the top of a page the button is bare, and the glass answers a press
 * as one piece: it swells a little towards the finger and settles back, lit from where it was
 * touched. Its buttons then leave their own press feedback to it. A control with its own, such as
 * a tab row, passes false.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun Modifier.floatingGlass(shape: RoundedCornerShape, barButton: Boolean = true): Modifier {
    // On a classic bar the band is the ground
    if (LocalOnClassicBar.current) return this
    val bar = if (barButton) LocalBarButtons.current else null
    val source = currentGlassSource()
    val tier = if (source == null) GlassTier.Solid else currentGlassTier()
    val colors = ExtendedTheme.colors
    val frost = glassFrost()
    // A row with nothing in it, as a bar without actions, has no glass: at no size its edge
    // would still draw as a dot
    var empty by remember { mutableStateOf(false) }
    val shown: () -> Float = if (bar == null) FullyShown else remember(bar) { { if (empty) 0f else bar.shown() } }
    val edge = if (colors.isNightMode) Color.White.copy(alpha = 0.10f)
    else Color.Black.copy(alpha = 0.06f)
    // A bar button swells under the finger and settles back, with the light where it was touched
    var pressed by remember { mutableStateOf(false) }
    var pressedAt by remember { mutableStateOf(Offset.Unspecified) }
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = if (pressed) Motion.press() else Motion.release(),
        label = "barButtonPress",
    )
    val chrome = if (bar == null) {
        // Glass stands off the page by its own light and refraction, so it casts no shadow; frosted
        // and opaque chrome have only a hairline and keep theirs. A shadow cast from here would
        // also lie over the glass of a glass container, which draws under everything in it. The
        // layer stays: under the app's lookahead, a part without one that only moves, as the
        // keyboard moves a search field, is moved in the lookahead pass but left where it was drawn.
        if (tier == GlassTier.Glass) {
            graphicsLayer {
                // As the shadow's layer did, it cuts what is on the glass to its shape, such as a
                // press's veil
                this.shape = shape
                clip = true
            }
        } else {
            shadow(elevation = FloatingElevation, shape = shape, ambientColor = ShadowAmbient, spotColor = ShadowSpot)
        }
    } else {
        onSizeChanged { empty = it.width == 0 || it.height == 0 }.pointerInput(Unit) {
            // Watches without taking: the buttons inside still get their clicks
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                pressedAt = down.position
                pressed = true
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                } while (event.changes.any { it.pressed })
                pressed = false
            }
        }.graphicsLayer {
            val scale = 1f + PressSwell * press
            scaleX = scale
            scaleY = scale
            transformOrigin = if (pressedAt.isSpecified && size.width > 0f) {
                TransformOrigin(pressedAt.x / size.width, pressedAt.y / size.height)
            } else TransformOrigin.Center
            // Glass casts none, as other floating glass
            shadowElevation = if (tier == GlassTier.Glass) 0f else FloatingElevation.toPx() * shown()
            this.shape = shape
            ambientShadowColor = ShadowAmbient
            spotShadowColor = ShadowSpot
        }
    }
    // Where the glass does not light up a press itself, or is not in yet, a veil shows it
    val veil = ExtendedTheme.colors.text
    val pressVeil: Modifier = if (bar == null) Modifier else Modifier.drawBehind {
        val lit = if (tier == GlassTier.Glass && !colors.isTranslucent) 1f - shown() else 1f
        val alpha = PressVeil * press * lit
        if (alpha > 0f) drawOutline(shape.createOutline(size, layoutDirection, this), veil.copy(alpha = alpha))
    }
    val edged: Modifier = if (bar == null) Modifier.border(0.5.dp, edge, shape) else Modifier.drawBehind {
        drawOutline(shape.createOutline(size, layoutDirection, this), edge.copy(alpha = edge.alpha * shown()), style = Stroke(0.5.dp.toPx()))
    }
    if (colors.isTranslucent && tier != GlassTier.Solid) {
        // Over a wallpaper only a light blur, untinted, so the wallpaper still shows through
        val style = if (bar == null) TranslucentFloatingStyle else remember(shown) { TranslucentFloatingStyle.then { alpha(shown()) } }
        return chrome
            .clip(shape)
            .hazeBlur(input = source!!.input, style = style)
            .then(pressVeil)
            .then(edged)
    }
    return when (tier) {
        GlassTier.Glass -> {
            val style = remember(shape, colors.background, frost, bar) {
                // Regular is the built-in material for surfaces that carry labels.
                GlassStyle.regular.then {
                    shape(shape)
                    backgroundColor(colors.background)
                    lightPosition(LightAbove)
                    // Regular blurs a compact control only partly, keeping the content under it
                    // sharp; here all of it is frosted, evenly
                    optics { copy(depth = OpticalSizeValue.Fixed(1f), blurRadius = OpticalSizeValue.Fixed(FloatingBlur * frost)) }
                    if (frost > 1f) tint(colors.background.copy(alpha = FrostedFloatingTint * (frost - 1f) / (MostFrost - 1f)))
                    if (bar != null) {
                        // Read here, the glass follows the scroll without being rebuilt
                        alpha(shown())
                        pressed {
                            animate(toSpec = Motion.press(), fromSpec = Motion.release()) {
                                lightingIntensity(1f)
                                whitePointDelta(PressBrighten)
                            }
                        }
                    }
                }
            }
            chrome.hazeGlass(input = source!!.input, style = style).then(pressVeil)
        }

        GlassTier.Blur -> {
            val style = remember(colors.background, frost, bar) {
                HazeBlurStyle {
                    backgroundColor(colors.background)
                    blurRadius(20.dp * frost)
                    noiseFactor(0f)
                    colorEffects(listOf(HazeColorEffect.tint(colors.background.copy(alpha = (0.72f * frost).coerceAtMost(MaxTint)))))
                    if (bar != null) alpha(shown())
                }
            }
            chrome
                .clip(shape)
                .hazeBlur(input = source!!.input, style = style)
                .then(pressVeil)
                .then(edged)
        }

        GlassTier.Solid -> {
            val fill = if (colors.isTranslucent) colors.chromeTint.copy(alpha = TranslucentSolidTint) else colors.background
            chrome
                .clip(shape)
                .then(
                    if (bar == null) Modifier.background(fill) else Modifier.drawBehind {
                        drawOutline(shape.createOutline(size, layoutDirection, this), fill.copy(alpha = fill.alpha * shown()))
                    }
                )
                .then(pressVeil)
                .then(edged)
        }
    }
}

private val FullyShown: () -> Float = { 1f }

/** How high floating glass stands over the content, by its shadow. */
private val FloatingElevation = 12.dp

/** How much a bar button's glass swells under a press. */
private const val PressSwell = 0.06f

/** How much a press brightens a bar button's glass, besides lighting it where it was touched. */
private const val PressBrighten = 0.06f

/** The veil a press puts on a bar button where its glass does not light up. */
private const val PressVeil = 0.08f

/**
 * The band behind a classic bar, across the window: the chosen material, with a hairline on its
 * edge towards the page. The top bar's comes in only once content has scrolled under it, so at
 * the top of a page the bar is the page's own ground, such as a forum's coloured header.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun Modifier.classicBarGlass(top: Boolean): Modifier {
    val source = currentGlassSource()
    val tier = if (source == null) GlassTier.Solid else currentGlassTier()
    val colors = ExtendedTheme.colors
    val frost = glassFrost()
    val scrollEdge = LocalPageScroll.current
    val revealPx = with(LocalDensity.current) { TopBarEdgeReveal.toPx() }
    val line = colors.divider
    // A page that doesn't say how far it has scrolled keeps its bar
    val shown: () -> Float = { if (!top) 1f else scrollEdge?.scrolled?.let { (it / revealPx).coerceIn(0f, 1f) } ?: 1f }
    val edged = graphicsLayer { alpha = shown() }.drawWithContent {
        drawContent()
        val y = if (top) size.height - ClassicBarLine.toPx() / 2 else ClassicBarLine.toPx() / 2
        drawLine(line, Offset(0f, y), Offset(size.width, y), ClassicBarLine.toPx())
    }
    return when {
        tier == GlassTier.Solid -> edged.background(
            if (colors.isTranslucent) colors.chromeTint.copy(alpha = TranslucentSolidTint) else colors.background
        )
        colors.isTranslucent -> edged.hazeBlur(input = source!!.input, style = TranslucentFloatingStyle)
        else -> {
            val style = remember(colors.background, frost) {
                HazeBlurStyle {
                    backgroundColor(colors.background)
                    blurRadius(ClassicBarBlur * frost)
                    noiseFactor(0f)
                    colorEffects(listOf(HazeColorEffect.tint(colors.background.copy(alpha = (ClassicBarTint * frost).coerceAtMost(MaxTint)))))
                }
            }
            edged.hazeBlur(input = source!!.input, style = style)
        }
    }
}

private val ClassicBarBlur = 20.dp
private const val ClassicBarTint = 0.8f
private val ClassicBarLine = 0.5.dp

/** How much floating glass blurs what is under it at the default clarity. */
private val FloatingBlur = 4.dp

/** The veil floating glass lays over what it shows at its most frosted, of the page's colour. */
private const val FrostedFloatingTint = 0.5f

/** No veil hides what is under the glass entirely. */
private const val MaxTint = 0.95f

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
