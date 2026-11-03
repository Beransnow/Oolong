package oolong.client.ui.widgets.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import oolong.client.ui.common.theme.compose.ExtendedTheme
import oolong.client.ui.common.theme.compose.Motion
import kotlin.math.abs
import kotlin.math.sign

/** How far the list must be pulled down to refresh, and where it waits while refreshing. */
private val RefreshDistance = 72.dp
private val RefreshingOffset = 56.dp

/** How far the list must be pulled up past its end for an [EndPull.Action]. */
internal val EndActionDistance = 72.dp

/** How hard pulling gets: the share of the list's height the pull approaches but never reaches. */
private const val Resistance = 0.55f

/** What pulling a list up past its end does. */
sealed interface EndPull {
    /** Nothing: the list stretches at its end as usual. */
    data object None : EndPull

    /** The list follows the finger, and its end row answers ([loadMoreFooter]); for lists that have ended. */
    data object Tease : EndPull

    /** The list follows the finger, and letting go past [EndActionDistance] calls [onPull]. */
    class Action(val onPull: () -> Unit) : EndPull
}

/** How far the list is pulled up past its end, in pixels, for the end row to answer. */
internal val LocalEndPull = compositionLocalOf<() -> Float> { { 0f } }

/** How far the content is pulled down from its place, for what moves with it outside it. */
internal val LocalPullOffset = compositionLocalOf<() -> Float> { { 0f } }

/**
 * Pull down to refresh: at its top the list follows the finger down, and the loader comes out in
 * the gap it leaves below [indicatorTop], usually the list's top padding. Pulled far enough and
 * let go, the list waits a little lower while [refreshing], then goes back. [endPull] says what
 * pulling up past the end does.
 */
@Composable
fun PullRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    indicatorTop: Dp = 0.dp,
    endPull: EndPull = EndPull.None,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val state = remember { PullState(scope) }
    val refreshPx = with(density) { RefreshDistance.toPx() }
    val holdPx = with(density) { RefreshingOffset.toPx() }
    val actionPx = with(density) { EndActionDistance.toPx() }
    val curRefreshing by rememberUpdatedState(refreshing)
    val curOnRefresh by rememberUpdatedState(onRefresh)
    val curEnabled by rememberUpdatedState(enabled)
    val curEndPull by rememberUpdatedState(endPull)

    // Back up once refreshing ends, unless a finger still holds the list
    LaunchedEffect(refreshing) {
        if (!refreshing && !state.dragging && state.offset > 0f) state.settle(0f, Motion.dismiss())
    }

    val connection = remember(state) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                state.grab()
                val dy = available.y
                // A pull is given back before the list scrolls the other way
                return if (state.offset > 0f && dy < 0f || state.offset < 0f && dy > 0f) {
                    Offset(0f, state.pullBy(dy))
                } else {
                    Offset.Zero
                }
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val dy = available.y
                val pull = dy > 0f && curEnabled && state.offset >= 0f ||
                    dy < 0f && curEndPull != EndPull.None && state.offset <= 0f
                return if (pull) Offset(0f, state.pullBy(dy)) else Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                state.dragging = false
                val offset = state.offset
                if (offset == 0f) return Velocity.Zero
                val velocity = available.y
                if (offset > 0f) {
                    when {
                        curRefreshing -> state.settle(holdPx, Motion.snap(), velocity)

                        offset >= refreshPx -> {
                            curOnRefresh()
                            state.settle(holdPx, Motion.snap(), velocity)
                            // A refresh that didn't start leaves nothing to wait for
                            scope.launch {
                                delay(200)
                                if (!curRefreshing && !state.dragging) state.settle(0f, Motion.dismiss())
                            }
                        }

                        else -> state.settle(0f, Motion.dismiss(), velocity)
                    }
                } else {
                    (curEndPull as? EndPull.Action)?.takeIf { -offset >= actionPx }?.onPull?.invoke()
                    state.settle(0f, Motion.dismiss(), velocity)
                }
                return available
            }
        }
    }

    Box(
        modifier = modifier
            .onSizeChanged { state.height = it.height.toFloat() }
            .nestedScroll(connection),
    ) {
        Box(modifier = Modifier.graphicsLayer { translationY = state.offset }) {
            CompositionLocalProvider(
                LocalEndPull provides { (-state.offset).coerceAtLeast(0f) },
                LocalPullOffset provides { state.offset.coerceAtLeast(0f) },
            ) {
                content()
            }
        }
        RefreshIndicator(state, refreshing, indicatorTop, refreshPx)
    }
}

/** The loader in the gap above a list pulled down: an arc drawn as far as the pull has come. */
@Composable
private fun BoxScope.RefreshIndicator(state: PullState, refreshing: Boolean, top: Dp, refreshPx: Float) {
    val haptic = LocalHapticFeedback.current
    val color = ExtendedTheme.colors.emphasis
    val armed by remember(refreshPx) { derivedStateOf { state.offset >= refreshPx } }
    val bump = remember { Animatable(1f) }
    LaunchedEffect(armed) {
        if (armed && state.dragging && !refreshing) {
            haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
            bump.snapTo(1.15f)
            bump.animateTo(1f, Motion.snap())
        }
    }
    // Spins from the refresh until the list is back up, so the arc doesn't jump as it ends
    var spinning by remember { mutableStateOf(false) }
    LaunchedEffect(refreshing) {
        if (refreshing) {
            spinning = true
        } else {
            snapshotFlow { state.offset <= 0f || state.dragging }.first { it }
            spinning = false
        }
    }
    val wave by animateFloatAsState(if (armed || spinning) 1f else 0f, Motion.snap(), label = "Wave")
    val time = rememberLoaderTime(running = spinning)

    LoaderCanvas(
        Modifier
            .align(Alignment.TopCenter)
            .graphicsLayer {
                val offset = state.offset.coerceAtLeast(0f)
                val progress = (offset / refreshPx).coerceAtMost(1f)
                translationY = top.toPx() + offset / 2 - size.height / 2
                alpha = (offset / (refreshPx * 0.45f)).coerceIn(0f, 1f)
                val scale = (0.6f + 0.4f * progress) * bump.value
                scaleX = scale
                scaleY = scale
            },
    ) { path ->
        if (state.offset <= 0f) return@LoaderCanvas
        val frame = if (spinning) {
            // From the full ring into the loop without a jump
            val t = time.value
            val loop = spinningArc(t)
            val mix = (t / 0.3f).coerceAtMost(1f)
            ArcFrame(loop.start * mix, 1f + (loop.sweep - 1f) * mix, loop.rotation + 180f * (1f - mix))
        } else {
            val progress = (state.offset / refreshPx).coerceAtMost(1f)
            ArcFrame(0f, progress, progress * 180f)
        }
        drawWavyArc(frame, wave, if (spinning) wavePhase(time.value) else 0f, color, path)
    }
}

@Stable
private class PullState(private val scope: CoroutineScope) {
    /** Down is positive: pulled down to refresh; negative is pulled up past the end. */
    var offset by mutableFloatStateOf(0f)
        private set
    var dragging by mutableStateOf(false)
    var height = 0f

    /** How far the finger has gone, which [offset] follows with resistance. */
    private var finger = 0f
    private var settling: Job? = null

    /** Takes over from a settle in progress, for the finger to carry on from where the list is. */
    fun grab() {
        if (dragging) return
        dragging = true
        settling?.cancel()
        finger = sign(offset) * fingerFor(abs(offset))
    }

    /** Moves the pull by [dy] of finger travel, never through zero; returns how much it took. */
    fun pullBy(dy: Float): Float {
        val next = finger + dy
        val clamped = if (finger > 0f && next < 0f || finger < 0f && next > 0f) 0f else next
        val used = clamped - finger
        finger = clamped
        offset = sign(finger) * resisted(abs(finger))
        return used
    }

    fun settle(target: Float, spec: AnimationSpec<Float>, velocity: Float = 0f) {
        settling?.cancel()
        settling = scope.launch {
            animate(offset, target, velocity, spec) { value, _ -> offset = value }
            finger = sign(offset) * fingerFor(abs(offset))
        }
    }

    private val dim get() = height.coerceAtLeast(1f)

    private fun resisted(x: Float) = (1f - 1f / (x * Resistance / dim + 1f)) * dim

    private fun fingerFor(pull: Float): Float {
        val p = pull.coerceAtMost(dim * 0.99f)
        return (1f / (1f - p / dim) - 1f) * dim / Resistance
    }
}
