package oolong.client.ui.widgets.compose

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import kotlin.math.abs

/**
 * How far a page's content has scrolled under its top bar, which shows the bar's scroll edge only
 * once something is under it: at the top of the page the bar is clear, and the edge comes in as
 * the content scrolls up. Lists report themselves with [scrollEdgeSource]; on a page of pages the
 * one in view counts.
 */
@Stable
class ScrollEdgeState internal constructor() {
    internal class Source(val scrolled: () -> Int) {
        /** Where the list starts across the page, so the page in view can be told from its neighbours. */
        var start by mutableFloatStateOf(0f)
    }

    private val sources = mutableStateListOf<Source>()

    /** Where the scaffold starts, so a page in a pane beside another still counts as in view. */
    internal var originX by mutableFloatStateOf(0f)

    internal fun add(source: Source) = sources.add(source)

    internal fun remove(source: Source) = sources.remove(source)

    /** Pixels scrolled under the bar, or null when no list reports, so the edge simply shows. */
    val scrolled: Int?
        get() = sources.minByOrNull { abs(it.start) }?.scrolled?.invoke()
}

/** The scroll edge a [GlassScaffold] shares between its top bar and its content. */
val LocalScrollEdge = compositionLocalOf<ScrollEdgeState?> { null }

internal fun newScrollEdgeState() = ScrollEdgeState()

/**
 * Reports to the page's [ScrollEdgeState] how far this scrolling content is from its top, as
 * [scrolled] pixels; anything past the first item counts as far.
 */
@Composable
fun Modifier.scrollEdgeSource(scrolled: () -> Int): Modifier {
    val state = LocalScrollEdge.current ?: return this
    val source = remember(state) { ScrollEdgeState.Source(scrolled) }
    DisposableEffect(state, source) {
        state.add(source)
        onDispose { state.remove(source) }
    }
    return onGloballyPositioned { source.start = it.positionInRoot().x - state.originX }
}

/** [scrollEdgeSource] for a lazy list. */
@Composable
fun Modifier.scrollEdgeSource(state: LazyListState): Modifier =
    scrollEdgeSource { if (state.firstVisibleItemIndex > 0) Int.MAX_VALUE else state.firstVisibleItemScrollOffset }

/** [scrollEdgeSource] for a lazy grid. */
@Composable
fun Modifier.scrollEdgeSource(state: LazyGridState): Modifier =
    scrollEdgeSource { if (state.firstVisibleItemIndex > 0) Int.MAX_VALUE else state.firstVisibleItemScrollOffset }

/** [scrollEdgeSource] for a scrolling column. */
@Composable
fun Modifier.scrollEdgeSource(state: ScrollState): Modifier = scrollEdgeSource { state.value }
