/*
 * Adapted from DampedDragAnimation in Kyant0/AndroidLiquidGlass, Copyright Kyant,
 * licensed under the Apache License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0).
 */
package oolong.client.ui.widgets.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatorMutex
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.util.fastCoerceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import oolong.client.ui.common.theme.compose.LocalReduceMotion
import kotlin.math.abs

/**
 * A glass part that is pressed and moved along a track, such as a switch's knob or the navigation
 * bar's lens: where it is ([value]), how far it has lifted into a lens ([pressProgress]), and how
 * it is drawn while it does, its size swelling a little apart on each axis ([scaleX], [scaleY]) so
 * it wobbles as it grows and shrinks, and drawn out along the way it moves ([stretchX],
 * [stretchY]).
 *
 * It follows the finger through a stiff spring rather than exactly, so how fast it moves can be
 * told and eased. A move made for it, as by a tap, lifts it, carries it and only lets it settle
 * once it has nearly arrived.
 */
@Stable
class ElasticPress internal constructor(
    private val scope: CoroutineScope,
    initialValue: Float,
    private val valueRange: ClosedFloatingPointRange<Float>,
    private val pressedScale: Float,
    /** How fast it must move, in ranges a second, to be drawn out the most. */
    private val velocityScale: Float,
    private val elastic: Boolean,
) {
    private val valueSpec = spring(1f, 1000f, ValueThreshold)
    private val velocitySpec = spring(0.5f, 300f, ValueThreshold * 10f)
    private val pressSpec = spring(1f, 1000f, ScaleThreshold)
    private val scaleXSpec = if (elastic) spring(0.6f, 250f, ScaleThreshold) else pressSpec
    private val scaleYSpec = if (elastic) spring(0.7f, 250f, ScaleThreshold) else pressSpec

    private val valueAnimation = Animatable(initialValue, ValueThreshold)
    private val velocityAnimation = Animatable(0f, 5f)
    private val pressAnimation = Animatable(0f, ScaleThreshold)
    private val scaleXAnimation = Animatable(1f, ScaleThreshold)
    private val scaleYAnimation = Animatable(1f, ScaleThreshold)

    private val mutex = MutatorMutex()
    private val velocityTracker = VelocityTracker()

    /** Where it is along its range. */
    val value: Float get() = valueAnimation.value

    /** Where it is going. */
    val targetValue: Float get() = valueAnimation.targetValue

    /** 0 at rest, 1 lifted into a lens, without overshoot, for what fades in and out with it. */
    val pressProgress: Float get() = pressAnimation.value

    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value

    private val velocity: Float get() = if (elastic) velocityAnimation.value / velocityScale else 0f

    /** How much longer it is drawn along the way it moves, as it moves fast. */
    val stretchX: Float get() = 1f / (1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f))

    /** How much thinner it is drawn across the way it moves, as it moves fast. */
    val stretchY: Float get() = 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)

    /** Lifts it, as when a finger comes down on it. */
    fun press() {
        velocityTracker.resetTracking()
        scope.launch {
            launch { pressAnimation.animateTo(1f, pressSpec) }
            launch { scaleXAnimation.animateTo(pressedScale, scaleXSpec) }
            launch { scaleYAnimation.animateTo(pressedScale, scaleYSpec) }
        }
    }

    /** Lets it settle, once it has nearly reached where it is going. */
    fun release() {
        scope.launch {
            awaitFrame()
            if (value != targetValue) {
                val near = (valueRange.endInclusive - valueRange.start) * 0.025f
                snapshotFlow { valueAnimation.value }
                    .filter { abs(it - valueAnimation.targetValue) < near }
                    .first()
            }
            launch { pressAnimation.animateTo(0f, pressSpec) }
            launch { scaleXAnimation.animateTo(1f, scaleXSpec) }
            launch { scaleYAnimation.animateTo(1f, scaleYSpec) }
        }
    }

    /** Follows a finger to [value], tracking how fast it goes. */
    fun follow(value: Float) {
        val target = value.coerceIn(valueRange)
        scope.launch { valueAnimation.animateTo(target, valueSpec) { trackVelocity() } }
    }

    /** Lifts it, carries it to [value] and lets it settle there, as for a tap. */
    fun moveTo(value: Float) {
        scope.launch {
            mutex.mutate {
                press()
                val target = value.coerceIn(valueRange)
                launch { valueAnimation.animateTo(target, valueSpec) }
                if (velocityAnimation.value != 0f) launch { velocityAnimation.animateTo(0f, velocitySpec) }
                release()
            }
        }
    }

    /** Glides it to [value] without lifting it, as when the pages it stands for are swiped. */
    fun glideTo(value: Float) {
        scope.launch { valueAnimation.animateTo(value.coerceIn(valueRange), valueSpec) }
    }

    /** Puts it at [value] at once. */
    fun snapTo(value: Float) {
        scope.launch { valueAnimation.snapTo(value.coerceIn(valueRange)) }
    }

    private fun trackVelocity() {
        velocityTracker.addPosition(System.currentTimeMillis(), Offset(value, 0f))
        val target = velocityTracker.calculateVelocity().x / (valueRange.endInclusive - valueRange.start)
        scope.launch { velocityAnimation.animateTo(target, velocitySpec) }
    }
}

/**
 * An [ElasticPress] at [initialValue] in [valueRange], growing to [pressedScale] when lifted and
 * drawn out the most at [velocityScale] ranges a second. Reduce motion leaves out the wobble and
 * the drawing out.
 */
@Composable
fun rememberElasticPress(
    initialValue: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    pressedScale: Float,
    velocityScale: Float,
): ElasticPress {
    val scope = rememberCoroutineScope()
    val elastic = !LocalReduceMotion.current
    return remember(scope, valueRange, pressedScale, velocityScale, elastic) {
        ElasticPress(scope, initialValue, valueRange, pressedScale, velocityScale, elastic)
    }
}

private const val ValueThreshold = 0.001f
private const val ScaleThreshold = 0.001f
