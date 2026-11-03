package oolong.client.ui.widgets.compose

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import oolong.client.ui.common.theme.compose.Motion
import kotlin.math.abs

/**
 * How far a collapsing header has collapsed, after Material 3's `TopAppBarState`.
 *
 * @param heightOffsetLimit how far the header can collapse, a negative number of pixels: minus
 * the height that goes away, which the header sets once it has measured itself
 */
@Stable
class CollapsingState(heightOffsetLimit: Float = -Float.MAX_VALUE, heightOffset: Float = 0f) {
    var heightOffsetLimit by mutableFloatStateOf(heightOffsetLimit)

    private var offset by mutableFloatStateOf(heightOffset)

    /** How far the header has collapsed, from 0 down to [heightOffsetLimit]. */
    var heightOffset: Float
        get() = offset
        set(value) {
            offset = value.coerceIn(minOf(heightOffsetLimit, 0f), 0f)
        }

    /** 0 while the header is open, 1 once it has collapsed fully. */
    val collapsedFraction: Float
        get() = if (heightOffsetLimit != 0f && heightOffsetLimit != -Float.MAX_VALUE) heightOffset / heightOffsetLimit else 0f

    companion object {
        val Saver: Saver<CollapsingState, *> = listSaver(
            save = { listOf(it.heightOffsetLimit, it.heightOffset) },
            restore = { CollapsingState(it[0], it[1]) },
        )
    }
}

@Composable
fun rememberCollapsingState(): CollapsingState = rememberSaveable(saver = CollapsingState.Saver) { CollapsingState() }

/**
 * Collapses a header as content scrolls up under it and opens it again only once the content is
 * back at its start, after Material 3's `exitUntilCollapsedScrollBehavior`. A fling carries on
 * into the header, and a header left partly open settles open or closed, whichever is nearer.
 *
 * Attach [nestedScrollConnection] to a parent of the scrolling content, inside anything that should
 * only see what the header leaves over, such as pull to refresh.
 */
@Stable
class CollapsingScrollBehavior(
    val state: CollapsingState,
    private val flingAnimationSpec: DecayAnimationSpec<Float>?,
    private val snapAnimationSpec: AnimationSpec<Float>?,
) {
    val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            // Only collapse before the content scrolls; opening waits for the content to reach its start
            if (available.y > 0f) return Offset.Zero
            val previous = state.heightOffset
            state.heightOffset += available.y
            // Unlike Material 3, give the content what the header could not take, so the scroll
            // that finishes collapsing it does not stall the content for a frame
            return Offset(0f, state.heightOffset - previous)
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (available.y < 0f || consumed.y < 0f) {
                val previous = state.heightOffset
                state.heightOffset += consumed.y
                return Offset(0f, state.heightOffset - previous)
            }
            if (available.y > 0f) {
                val previous = state.heightOffset
                state.heightOffset += available.y
                return Offset(0f, state.heightOffset - previous)
            }
            return Offset.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
            super.onPostFling(consumed, available) + settle(available.y)
    }

    /** Carries a fling on into the header, then snaps a partly open header open or closed. */
    private suspend fun settle(velocity: Float): Velocity {
        if (state.collapsedFraction < 0.01f || state.collapsedFraction == 1f) return Velocity.Zero
        var remaining = velocity
        if (flingAnimationSpec != null && abs(velocity) > 1f) {
            var last = 0f
            AnimationState(initialValue = 0f, initialVelocity = velocity).animateDecay(flingAnimationSpec) {
                val delta = value - last
                val before = state.heightOffset
                state.heightOffset = before + delta
                val consumed = abs(before - state.heightOffset)
                last = value
                remaining = this.velocity
                // The header has reached an end: leave the rest to whatever is outside it
                if (abs(delta - consumed) > 0.5f) cancelAnimation()
            }
        }
        if (snapAnimationSpec != null && state.heightOffset < 0f && state.heightOffset > state.heightOffsetLimit) {
            AnimationState(initialValue = state.heightOffset).animateTo(
                targetValue = if (state.collapsedFraction < 0.5f) 0f else state.heightOffsetLimit,
                animationSpec = snapAnimationSpec,
            ) { state.heightOffset = value }
        }
        return Velocity(0f, remaining)
    }
}

@Composable
fun rememberCollapsingScrollBehavior(state: CollapsingState = rememberCollapsingState()): CollapsingScrollBehavior {
    val decay = rememberSplineBasedDecay<Float>()
    return remember(state, decay) {
        CollapsingScrollBehavior(
            state = state,
            flingAnimationSpec = decay,
            snapAnimationSpec = Motion.settle(),
        )
    }
}
