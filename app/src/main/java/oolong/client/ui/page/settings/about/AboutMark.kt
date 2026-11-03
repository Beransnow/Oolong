package oolong.client.ui.page.settings.about

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch
import oolong.client.ui.widgets.compose.isGlassAvailable
import oolong.client.ui.widgets.compose.loupeLens
import kotlin.math.roundToInt

/**
 * The lens of the app's mark, which can be lifted off it: held, it grows into a glass lens over
 * the page that follows the finger, and let go, it springs back into the mark.
 */
@Stable
class MarkLens {
    /** Whether the lens is off the mark, from being picked up until it is back in place. */
    internal var lifted by mutableStateOf(false)

    /** How far it has grown from its size in the mark, 0 to 1. */
    internal var grown by mutableFloatStateOf(0f)

    /** How far it is from its place in the mark. */
    internal var moved by mutableStateOf(Offset.Zero)

    /** Where its place in the mark is, in what [LensOverlay] is laid out in. */
    internal var home by mutableStateOf(Offset.Zero)

    internal var markCoordinates: LayoutCoordinates? = null
    internal var overlayCoordinates: LayoutCoordinates? = null
}

/**
 * The app's mark, [size] across, drawn as the launcher icon is: a blue disc, the white bubble on
 * it, and the glass lens over the bubble's corner, which can be picked up with [lens].
 */
@Composable
fun AboutMark(lens: MarkLens, size: Dp, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    Canvas(
        modifier = modifier
            .size(size)
            .onGloballyPositioned {
                lens.markCoordinates = it
                lens.updateHome()
            }
            .pointerInput(lens) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val unit = this.size.width / 100f
                    if (lens.lifted || (down.position - LensCenter * unit).getDistance() > LensRadius * unit * 1.2f) return@awaitEachGesture
                    down.consume()
                    lens.lifted = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val grow = scope.launch {
                        Animatable(lens.grown).animateTo(1f, LiftSpring) { lens.grown = value }
                    }
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        lens.moved = change.position - down.position
                    }
                    // Back into its place, shrinking on the way, and in the mark again once there
                    grow.cancel()
                    scope.launch {
                        val shrink = launch {
                            Animatable(lens.grown).animateTo(0f, returnSpring()) { lens.grown = value }
                        }
                        Animatable(lens.moved, Offset.VectorConverter).animateTo(Offset.Zero, returnSpring()) { lens.moved = value }
                        shrink.join()
                        lens.lifted = false
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    }
                }
            },
    ) {
        val unit = this.size.width / 100f
        drawMark(unit, withLens = !lens.lifted)
    }
}

/**
 * The lens while it is off the mark, over the page captured in [page], which must not contain
 * this. Where glass is not drawn, it is the mark's own pale lens, grown.
 */
@Composable
fun BoxScope.LensOverlay(lens: MarkLens, page: HazeState, markSize: Dp) {
    val density = LocalDensity.current
    val glass = isGlassAvailable()
    Box(
        modifier = Modifier
            .matchParentSize()
            .onGloballyPositioned {
                lens.overlayCoordinates = it
                lens.updateHome()
            },
    )
    if (!lens.lifted) return
    val grown = lens.grown.coerceAtLeast(0f)
    val diameter = lerp(markSize * (LensRadius * 2 / 100f), LiftedDiameter, grown)
    val center = lens.home + lens.moved
    // The mark's painted lens shows while small, so it lands back on the mark unchanged
    val painted = if (glass) (1f - grown * 2f).coerceIn(0f, 1f) else 1f
    Box(
        modifier = Modifier
            .offset {
                val half = with(density) { diameter.toPx() } / 2
                IntOffset((center.x - half).roundToInt(), (center.y - half).roundToInt())
            }
            .size(diameter)
            .drawBehind { drawLensShadow(this.size.width / 2) }
            .loupeLens(page, CircleShape)
            .drawWithContent {
                drawContent()
                drawPaintedLens(this.size.width / 2, painted)
            },
    )
}

private fun MarkLens.updateHome() {
    val mark = markCoordinates ?: return
    val overlay = overlayCoordinates ?: return
    if (!mark.isAttached || !overlay.isAttached) return
    home = overlay.localPositionOf(mark, LensCenter * (mark.size.width / 100f))
}

/** The mark in units of a hundredth of its width, as the splash icon's vector draws it. */
private fun DrawScope.drawMark(unit: Float, withLens: Boolean) {
    fun at(x: Float, y: Float) = Offset(x * unit, y * unit)
    drawCircle(
        brush = Brush.verticalGradient(listOf(Color(0xFF5B8FE6), Color(0xFF2F57B0))),
        radius = 50 * unit,
        center = at(50f, 50f),
    )
    if (withLens) {
        drawCircle(
            brush = Brush.radialGradient(
                0.7f to Color(0x26163A80),
                0.84f to Color(0x1A163A80),
                1f to Color(0x00163A80),
                center = at(64f, 37.6f),
                radius = 24 * unit,
            ),
            radius = 24 * unit,
            center = at(64f, 37.6f),
        )
        drawCircle(
            brush = Brush.verticalGradient(listOf(Color(0x80FFFFFF), Color(0x2EFFFFFF)), startY = 16 * unit, endY = 56 * unit),
            radius = LensRadius * unit,
            center = LensCenter * unit,
        )
    }
    // The bubble and its shadow
    drawCircle(
        brush = Brush.radialGradient(
            0.7f to Color(0x4C163A80),
            0.84f to Color(0x33163A80),
            1f to Color(0x00163A80),
            center = at(41f, 61.2f),
            radius = 30 * unit,
        ),
        radius = 30 * unit,
        center = at(41f, 61.2f),
    )
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color.White,
            0.55f to Color(0xFFF4F8FE),
            1f to Color(0xFFD8E4F7),
            center = at(32.25f, 47.75f),
            radius = 38.75f * unit,
        ),
        radius = 25 * unit,
        center = at(41f, 59f),
    )
    val lines = Color(0xFF3F6FC8)
    drawRoundRect(lines, topLeft = at(28f, 53f), size = Size(22f * unit, 5.6f * unit), cornerRadius = CornerRadius(2.8f * unit))
    drawRoundRect(lines, topLeft = at(28f, 63f), size = Size(13f * unit, 5.6f * unit), cornerRadius = CornerRadius(2.8f * unit))
    if (withLens) {
        // Where the lens lies over the bubble, the bubble shows through it tinted
        val bubble = Path().apply { addOval(Rect(at(41f, 59f), 25 * unit)) }
        clipPath(bubble) {
            drawCircle(
                brush = Brush.verticalGradient(listOf(Color(0xFFD3E3FA), Color(0xFFAFCAF1)), startY = 16 * unit, endY = 56 * unit),
                radius = LensRadius * unit,
                center = LensCenter * unit,
            )
        }
        drawRim(LensCenter * unit, LensRadius * unit, strokeWidth = 0.9f * unit, alpha = 1f)
    }
}

/** The lens's soft shadow, a little below it, for a lens of [radius]. */
private fun DrawScope.drawLensShadow(radius: Float) {
    val center = Offset(size.width / 2, size.height / 2 + radius * 0.06f)
    drawCircle(
        brush = Brush.radialGradient(
            0.8f to Color(0x1F000000),
            1f to Color(0x00000000),
            center = center,
            radius = radius * 1.12f,
        ),
        radius = radius * 1.12f,
        center = center,
    )
}

/** The mark's pale lens, of [radius], at [alpha]: what shows where there is no glass. */
private fun DrawScope.drawPaintedLens(radius: Float, alpha: Float) {
    if (alpha <= 0f) return
    val center = Offset(size.width / 2, size.height / 2)
    drawCircle(
        brush = Brush.verticalGradient(listOf(Color(0x80FFFFFF), Color(0x2EFFFFFF))),
        radius = radius,
        center = center,
        alpha = alpha,
    )
    drawRim(center, radius, strokeWidth = radius * 0.045f, alpha = alpha)
}

private fun DrawScope.drawRim(center: Offset, radius: Float, strokeWidth: Float, alpha: Float) {
    drawCircle(
        brush = Brush.linearGradient(
            0f to Color(0xF2FFFFFF),
            0.5f to Color(0x1FFFFFFF),
            1f to Color(0x80FFFFFF),
            start = center + Offset(-radius * 0.6f, -radius),
            end = center + Offset(radius * 0.6f, radius),
        ),
        radius = radius - strokeWidth / 2,
        center = center,
        style = Stroke(strokeWidth),
        alpha = alpha,
    )
}

/** The lens's place and size in the mark, in hundredths of its width. */
private val LensCenter = Offset(64f, 36f)
private const val LensRadius = 20f

private val LiftedDiameter = 128.dp
private val LiftSpring = spring<Float>(dampingRatio = 0.7f, stiffness = 400f)
private fun <T> returnSpring() = spring<T>(dampingRatio = 0.6f, stiffness = 300f)
