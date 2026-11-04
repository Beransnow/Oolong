package oolong.client.ui.page.editor

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import oolong.client.ui.common.theme.compose.ExtendedTheme
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sign

/** How far past the middle the finger goes while the value holds there. */
private val HoldDistance = 10.dp

/**
 * A ruler that slides under a fixed mark as the finger drags it, [value] under the mark and shown
 * above it by [format]. It goes on by itself after a fling, slowing down.
 *
 * Its middle, [rest], holds the value as the finger passes, for a little way, with a firmer tick
 * to say so: level is easy to find, yet small values either side are still reached by going a
 * little further, rather than being pulled to it. A tick is felt at each major mark. A double
 * tap brings it back to [rest].
 *
 * @param unitWidth the breadth of one unit of [value] on the ruler
 * @param tick the units between marks; every [major]th mark is longer
 */
@Composable
fun Ruler(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    onCommit: () -> Unit,
    label: String,
    format: (Float) -> String,
    modifier: Modifier = Modifier,
    unitWidth: Dp = 5.dp,
    tick: Float = 1f,
    major: Int = 5,
    rest: Float = 0f,
) {
    val colors = ExtendedTheme.colors
    val measurer = rememberTextMeasurer()
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val current by rememberUpdatedState(value)
    val change by rememberUpdatedState(onChange)
    val commit by rememberUpdatedState(onCommit)
    // Where the finger has the ruler, in units, the hold at the middle counted as distance
    val raw = remember { Animatable(0f) }

    Canvas(
        modifier = modifier
            .height(40.dp)
            .semantics {
                contentDescription = label
                stateDescription = format(value)
            }
            .pointerInput(range, rest) {
                val unit = unitWidth.toPx()
                val hold = HoldDistance.toPx() / unit
                fun valueOf(r: Float) = if (abs(r - rest) <= hold) rest else r - sign(r - rest) * hold
                fun rawOf(v: Float) = if (v == rest) rest else v + sign(v - rest) * hold
                val bounds = rawOf(range.start)..rawOf(range.endInclusive)
                var last = current
                fun show(r: Float) {
                    val v = valueOf(r).coerceIn(range)
                    // A firm tick on coming to rest in the middle, a light one at each major mark
                    if (v == rest && last != rest) {
                        view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
                    } else if (floor(v / (tick * major)) != floor(last / (tick * major)) && v != rest) {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                    last = v
                    change(v)
                }
                awaitEachGesture {
                    // The double tap below sees the press first, and takes it
                    val down = awaitFirstDown(requireUnconsumed = false)
                    scope.launch { raw.stop() }
                    last = current
                    val from = rawOf(current)
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var moved = false
                    drag(down.id) { pointer ->
                        tracker.addPosition(pointer.uptimeMillis, pointer.position)
                        moved = true
                        // Dragging the ruler right brings lower numbers under the mark
                        val r = (from - (pointer.position.x - down.position.x) / unit).coerceIn(bounds)
                        scope.launch { raw.snapTo(r) }
                        show(r)
                        pointer.consume()
                    }
                    if (!moved) return@awaitEachGesture
                    // Carried on by the fling, slowing, and kept within the ruler
                    val velocity = -tracker.calculateVelocity().x / unit
                    scope.launch {
                        raw.updateBounds(bounds.start, bounds.endInclusive)
                        raw.animateDecay(velocity, exponentialDecay(frictionMultiplier = 2.5f)) { show(this.value) }
                        raw.updateBounds(null, null)
                        commit()
                    }
                }
            }
            .pointerInput(rest) {
                detectTapGestures(onDoubleTap = {
                    scope.launch {
                        raw.stop()
                        change(rest)
                        commit()
                    }
                })
            }
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                // The ends fade, as the ruler goes on past them
                drawRect(
                    Brush.horizontalGradient(0f to Color.Transparent, 0.18f to Color.Black, 0.82f to Color.Black, 1f to Color.Transparent),
                    blendMode = BlendMode.DstIn,
                )
            },
    ) {
        val unit = unitWidth.toPx()
        val baseline = size.height - 6.dp.toPx()
        val half = size.width / 2 / unit
        val first = floor((value - half) / tick).toInt() - 1
        val last = floor((value + half) / tick).toInt() + 1
        for (i in first..last) {
            val mark = i * tick
            if (mark < range.start || mark > range.endInclusive) continue
            val x = size.width / 2 + (mark - value) * unit
            val long = i % major == 0
            drawLine(
                color = if (mark == rest) colors.text else colors.textSecondary.copy(alpha = if (long) 0.9f else 0.45f),
                start = Offset(x, baseline - (if (long) 10.dp else 6.dp).toPx()),
                end = Offset(x, baseline),
                strokeWidth = 1.dp.toPx(),
            )
        }
        drawLine(colors.text, Offset(size.width / 2, baseline - 14.dp.toPx()), Offset(size.width / 2, baseline + 2.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
        val text = measurer.measure(format(value), TextStyle(color = colors.text, fontSize = 10.sp, fontWeight = FontWeight.Medium))
        drawText(text, topLeft = Offset(size.width / 2 - text.size.width / 2f, 0f))
    }
}
