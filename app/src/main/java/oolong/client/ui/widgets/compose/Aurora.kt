package oolong.client.ui.widgets.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import oolong.client.ui.common.theme.compose.ExtendedTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * Where an [Aurora] is: how long it has drifted, how far its colours have spread from the middle
 * (0 gathered there, 1 spread over the page), and the finger drawing them.
 */
@Stable
class AuroraState(spread: Float = 1f) {
    val spread = Animatable(spread)
    var time by mutableFloatStateOf(0f)
        private set
    var pull by mutableFloatStateOf(0f)
        private set
    var finger by mutableStateOf<Offset?>(null)
        private set

    /** Draws the colours a little towards [position], letting go over a moment. */
    fun touch(position: Offset) {
        finger = position
        pull = 1f
    }

    /** Moves the light on every frame for as long as it is called. */
    suspend fun drift() {
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val dt = (now - last) / 1e9f
                last = now
                time += dt
                pull *= exp(-dt * 2.4f)
            }
        }
    }
}

/**
 * The light behind the landing and About pages: soft colours drifting over the ground, leaning a
 * little towards a finger. Draws [state] as it is; [AuroraState.drift] keeps it moving.
 */
@Composable
fun Aurora(state: AuroraState, modifier: Modifier = Modifier, dark: Boolean = ExtendedTheme.colors.isNightMode) {
    val palette = if (dark) AuroraDark else AuroraLight
    Canvas(modifier) {
        drawRect(palette.background)
        drawAuroraLight(state, Rect(Offset.Zero, size), dark)
        // The edges stay quiet; fading from the ground's own colour keeps them from turning grey
        val edge = if (dark) Color.Black.copy(alpha = .45f) else palette.background.copy(alpha = .85f)
        drawRect(
            Brush.radialGradient(
                0f to edge.copy(alpha = 0f),
                .25f to edge.copy(alpha = 0f),
                1f to edge,
                center = Offset(size.width / 2, size.height * .46f),
                radius = size.height * .8f,
            ),
        )
    }
}

/**
 * The aurora's colours alone, over [area] and without its ground: on dark, light added to what
 * is under it. [pace] speeds the drift and [sway] widens it; [fill] sizes the colours by the
 * area's longer side rather than its width, so a tall one is washed over whole; [alpha] fades it.
 */
fun DrawScope.drawAuroraLight(
    state: AuroraState,
    area: Rect,
    dark: Boolean,
    pace: Float = 1f,
    sway: Float = 1f,
    fill: Boolean = false,
    alpha: Float = 1f,
) {
    val palette = if (dark) AuroraDark else AuroraLight
    val w = area.width
    val h = area.height
    val t = state.time * pace
    val spread = state.spread.value
    Blobs.forEachIndexed { i, blob ->
        val (color, strength) = palette.blobs[i]
        // Every colour starts at the middle and spreads, as dye does in water
        var x = area.left + lerp(.5f, blob.x + sin(t * blob.speed * 1.6f * 2 * PI.toFloat() + blob.phase) * blob.dx * sway, spread) * w
        var y = area.top + lerp(.36f, blob.y + cos(t * blob.speed * 1.6f * 5.1f + blob.phase) * blob.dy * sway, spread) * h
        state.finger?.let { finger ->
            val dx = finger.x - x
            val dy = finger.y - y
            val d = hypot(dx, dy) + 1f
            val pull = state.pull * 60.dp.toPx() * exp(-d / 260.dp.toPx())
            x += dx / d * pull
            y += dy / d * pull
        }
        val r = blob.radius * (if (fill) max(w, h) * .8f else w) * lerp(.08f, 1f, spread) * (1f + sin(t * .4f + i) * .06f)
        val a = strength * lerp(.2f, 1f, spread) * alpha
        val center = Offset(x, y)
        drawCircle(
            brush = Brush.radialGradient(
                // Close to a Gaussian, so no edge shows where the colour runs out
                0f to color.copy(alpha = a),
                .2f to color.copy(alpha = a * .86f),
                .4f to color.copy(alpha = a * .58f),
                .6f to color.copy(alpha = a * .3f),
                .8f to color.copy(alpha = a * .1f),
                1f to color.copy(alpha = 0f),
                center = center,
                radius = r,
            ),
            radius = r,
            center = center,
            blendMode = if (dark) BlendMode.Plus else BlendMode.SrcOver,
        )
    }
}

/** One of the aurora's colours, for light that belongs with it. */
fun auroraColor(index: Int, dark: Boolean = true): Color = (if (dark) AuroraDark else AuroraLight).blobs[index % Blobs.size].first

/** Positions are fractions of the page's width and height; speeds are turns per second. */
private class Blob(val x: Float, val y: Float, val radius: Float, val dx: Float, val dy: Float, val speed: Float, val phase: Float)

private val Blobs = listOf(
    Blob(.5f, .38f, .62f, .18f, .10f, .07f, 0f),
    Blob(.28f, .62f, .55f, .14f, .16f, .05f, 2f),
    Blob(.78f, .26f, .5f, .12f, .12f, .06f, 4f),
    Blob(.62f, .78f, .58f, .2f, .1f, .045f, 1f),
    Blob(.2f, .2f, .42f, .1f, .14f, .08f, 3f),
)

private class AuroraPalette(val background: Color, val blobs: List<Pair<Color, Float>>)

private val AuroraLight = AuroraPalette(
    Color(0xFFEEF1FA),
    listOf(Color(0xFF4F7FE8) to .5f, Color(0xFF8B6CF0) to .42f, Color(0xFF3CC6C0) to .4f, Color(0xFFF5A97F) to .38f, Color(0xFFF08BB8) to .34f),
)
private val AuroraDark = AuroraPalette(
    Color(0xFF090C16),
    listOf(Color(0xFF3D6EF0) to .55f, Color(0xFF7B5CF0) to .5f, Color(0xFF20B3AD) to .4f, Color(0xFFE98A5A) to .3f, Color(0xFFD9659C) to .32f),
)

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
