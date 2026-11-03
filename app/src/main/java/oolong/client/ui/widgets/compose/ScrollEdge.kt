package oolong.client.ui.widgets.compose

import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlin.math.abs

/**
 * How far a page's content has scrolled under its top bar, which shows the bar's scroll edge only
 * once something is under it: at the top of the page the bar is clear, and the edge comes in as
 * the content scrolls up. Lists report themselves with [scrollEdgeSource]; on a page of pages the
 * one in view counts.
 */
@Stable
class ScrollEdgeState internal constructor() {
    internal class Source(val scrolled: () -> Int, val scrollable: ScrollableState?) {
        /** Where the list starts across the page, so the page in view can be told from its neighbours. */
        var start by mutableFloatStateOf(0f)
    }

    private val sources = mutableStateListOf<Source>()

    /** Where the scaffold starts, so a page in a pane beside another still counts as in view. */
    internal var originX by mutableFloatStateOf(0f)

    internal fun add(source: Source) = sources.add(source)

    internal fun remove(source: Source) = sources.remove(source)

    /** The content in view: on a page of pages, the page nearest the scaffold's start. */
    internal val inView: Source? get() = sources.minByOrNull { abs(it.start) }

    /** Pixels scrolled under the bar, or null when no list reports, so the edge simply shows. */
    val scrolled: Int? get() = inView?.scrolled?.invoke()

    /** The top bar's row, in window coordinates, for things that dock into it; NaN before it is laid out. */
    internal var barRowTop by mutableFloatStateOf(Float.NaN)
    internal var barRowBottom by mutableFloatStateOf(Float.NaN)

    /** Where each [matchedGeometryAnchor] was last laid out, in window coordinates. */
    internal val anchors = mutableStateMapOf<Any, Rect>()

    private val snapRanges = mutableStateListOf<ClosedFloatingPointRange<Float>>()

    internal fun addSnapRange(range: ClosedFloatingPointRange<Float>) = snapRanges.add(range)

    internal fun removeSnapRange(range: ClosedFloatingPointRange<Float>) = snapRanges.remove(range)

    /**
     * Whenever the content in view stops inside a range that snaps, scrolls it on to the nearer
     * end, so nothing scroll-driven is left halfway.
     */
    internal suspend fun settle() {
        snapshotFlow { inView }.collectLatest { source ->
            val scrollable = source?.scrollable ?: return@collectLatest
            snapshotFlow { scrollable.isScrollInProgress }.filter { !it }.collect {
                val scrolled = source.scrolled().toFloat()
                val range = snapRanges.firstOrNull { scrolled > it.start && scrolled < it.endInclusive }
                    ?: return@collect
                val target = if (scrolled - range.start < range.endInclusive - scrolled) range.start else range.endInclusive
                scrollable.animateScrollBy(target - scrolled, spring(stiffness = SnapStiffness))
            }
        }
    }
}

/** How firmly a snap carries the content to the end of its range. */
private const val SnapStiffness = 600f

/** The scroll edge a [GlassScaffold] shares between its top bar and its content. */
val LocalScrollEdge = compositionLocalOf<ScrollEdgeState?> { null }

internal fun newScrollEdgeState() = ScrollEdgeState()

/**
 * Reports to the page's [ScrollEdgeState] how far this scrolling content is from its top, as
 * [scrolled] pixels, and the [scrollable] that moves it, which settles a large title left halfway.
 */
@Composable
fun Modifier.scrollEdgeSource(scrollable: ScrollableState?, scrolled: () -> Int): Modifier {
    val state = LocalScrollEdge.current ?: return this
    val source = remember(state, scrollable) { ScrollEdgeState.Source(scrolled, scrollable) }
    DisposableEffect(state, source) {
        state.add(source)
        onDispose { state.remove(source) }
    }
    return onGloballyPositioned { source.start = it.positionInRoot().x - state.originX }
}

/**
 * [scrollEdgeSource] for a lazy list: how far its first item has moved up. Items under the bar
 * are still laid out, so the first item stays known well past the bar and a large title; once it
 * is gone the content counts as far.
 */
@Composable
fun Modifier.scrollEdgeSource(state: LazyListState): Modifier = scrollEdgeSource(state) {
    // Item offsets start at the end of the top padding
    state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }?.let { -it.offset } ?: Int.MAX_VALUE
}

/** [scrollEdgeSource] for a lazy grid, as for a lazy list. */
@Composable
fun Modifier.scrollEdgeSource(state: LazyGridState): Modifier = scrollEdgeSource(state) {
    state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }?.let { -it.offset.y } ?: Int.MAX_VALUE
}

/** [scrollEdgeSource] for a scrolling column. */
@Composable
fun Modifier.scrollEdgeSource(state: ScrollState): Modifier = scrollEdgeSource(state) { state.value }

/** [verticalScroll] that also reports to the top bar's scroll edge. */
@Composable
fun Modifier.verticalScrollUnderBar(state: ScrollState = rememberScrollState()): Modifier =
    scrollEdgeSource(state).verticalScroll(state)
