package oolong.client.ui.widgets.compose

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import oolong.client.ui.common.theme.compose.ExtendedTheme
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

val LoaderSize = 24.dp

private val LoaderStroke = 2.4.dp
private val LoaderRadius = 9.dp

/** How far the line waves to either side of the arc, and how many waves make a full ring. */
private val WaveAmplitude = 0.8.dp
private const val WaveCount = 10

/** How fast the waves run along the arc, in radians a second. */
private const val WaveSpeed = 8f

/** One stretch and shrink of the arc, and one turn of the whole, in seconds. */
private const val BreathSeconds = 1.333f
private const val TurnSeconds = 1.568f

/** Where an arc starts and how far it runs, in turns, and how far the whole is turned, in degrees. */
internal class ArcFrame(val start: Float, val sweep: Float, val rotation: Float)

/** The app's loader: an arc that turns, stretches and shrinks, its line rippling. */
@Composable
fun WavyLoader(modifier: Modifier = Modifier, color: Color = ExtendedTheme.colors.emphasis) {
    val time = rememberLoaderTime(running = true)
    LoaderCanvas(modifier) { path ->
        val t = time.value
        drawWavyArc(spinningArc(t), wave = 1f, phase = wavePhase(t), color = color, path = path)
    }
}

/** The loader's arc at [t] seconds: the head runs ahead, then the tail catches up, as it turns. */
internal fun spinningArc(t: Float): ArcFrame {
    val cycle = floor(t / BreathSeconds)
    val f = t / BreathSeconds - cycle
    val head = 0.75f * ease(min(1f, f * 2))
    val tail = 0.75f * ease(max(0f, f * 2 - 1))
    return ArcFrame(
        start = tail + cycle * 0.75f,
        sweep = max(0.04f, head - tail + 0.03f),
        rotation = t * 360f / TurnSeconds,
    )
}

internal fun wavePhase(t: Float): Float = t * WaveSpeed

/** Seconds since [running] last became true, ticking every frame while it stays true. */
@Composable
internal fun rememberLoaderTime(running: Boolean): State<Float> {
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        time.floatValue = 0f
        val start = withFrameNanos { it }
        while (true) withFrameNanos { time.floatValue = (it - start) / 1_000_000_000f }
    }
    return time
}

/** Draws [frame] as a round-capped line whose radius waves by [wave] (0 is a plain arc). */
internal fun DrawScope.drawWavyArc(frame: ArcFrame, wave: Float, phase: Float, color: Color, path: Path = Path()) {
    val radius = LoaderRadius.toPx()
    val amplitude = WaveAmplitude.toPx() * wave
    val closed = frame.sweep >= 0.999f
    val sweep = frame.sweep.coerceIn(0.001f, 1f)
    val steps = max(2, ceil(sweep * 120).toInt())
    path.reset()
    for (i in 0..steps) {
        val angle = (frame.start + sweep * i / steps) * 2 * PI.toFloat() + (frame.rotation - 90f) * PI.toFloat() / 180f
        val r = radius + amplitude * sin(WaveCount * angle - phase)
        val x = center.x + r * cos(angle)
        val y = center.y + r * sin(angle)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    if (closed) path.close()
    drawPath(path, color, style = Stroke(width = LoaderStroke.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** A [LoaderSize] box drawn by [draw] with a path kept between frames. */
@Composable
internal fun LoaderCanvas(modifier: Modifier, draw: DrawScope.(Path) -> Unit) {
    Spacer(
        modifier
            .size(LoaderSize)
            .drawWithCache {
                val path = Path()
                onDrawBehind { draw(path) }
            },
    )
}

private fun ease(x: Float): Float = if (x < 0.5f) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
