package oolong.client.ui.widgets.compose

import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.filter
import kotlin.math.roundToInt

/** How much bigger the large title is than the bar's own. */
private const val LargeTitleScale = 1.5f

/** Space above and below the large title, inside its slot. */
private val LargeTitleTop = 4.dp
private val LargeTitleBottom = 12.dp

/** Where the large title starts, from the slot's start edge. */
private val LargeTitleStart = 16.dp

/**
 * A page's large title: shown big at the top of its list, it shrinks into the top bar as the
 * list scrolls up. It is one title, the [GlassTopBar]'s, moved and scaled to follow an empty
 * [LargeTitleSlot] at the top of the list, so it scrolls with the content until it reaches the bar.
 * Positions are in window coordinates.
 */
@Stable
class LargeTitleState internal constructor() {
    /** The slot's top, or NaN while the list has scrolled it out and disposed it. */
    internal var slotTop by mutableFloatStateOf(Float.NaN)
    internal var slotStart by mutableFloatStateOf(0f)

    /** The bar's title as the bar lays it out, which is where it ends up. */
    internal var titleTop by mutableFloatStateOf(0f)
    internal var titleStart by mutableFloatStateOf(0f)
    internal var titleHeight by mutableIntStateOf(0)

    /** The bottom of the bar's title row, where the slot sits with the list at the top. */
    internal var rowBottom by mutableFloatStateOf(0f)

    /**
     * The list that shows the title: the first to ask, so a list in a sheet over it does not. State,
     * so a list that asked while another held it, such as the content after a loading skeleton,
     * asks again once that one lets go.
     */
    private var owner: Any? by mutableStateOf(null)

    internal fun claim(list: Any): Boolean {
        if (owner == null) owner = list
        return owner === list
    }

    internal fun release(list: Any) {
        if (owner === list) owner = null
    }

    internal var topPx = 0f
    internal var startPx = 0f

    /** How far the title still is from the bar: the whole way at the top of the list, 0 in the bar. */
    internal val offset: Float
        get() {
            val top = slotTop
            return if (top.isNaN()) 0f else (top + topPx - titleTop).coerceIn(0f, restOffset)
        }

    private val restOffset: Float get() = (rowBottom + topPx - titleTop).coerceAtLeast(0f)

    /** 0 with the title in the bar, 1 with it fully large. */
    internal val expansion: Float
        get() = restOffset.let { if (it > 0f) offset / it else 0f }

    internal fun GraphicsLayerScope.place() {
        val expanded = expansion
        val scale = 1f + (LargeTitleScale - 1f) * expanded
        transformOrigin = TransformOrigin(0f, 0f)
        scaleX = scale
        scaleY = scale
        translationY = offset
        translationX = (slotStart + startPx - titleStart) * expanded
    }

    /**
     * Settles a title left halfway once [scrollable] stops: it scrolls on into the bar, or back
     * out to large, whichever is nearer.
     */
    internal suspend fun settle(scrollable: ScrollableState) {
        snapshotFlow { scrollable.isScrollInProgress }.filter { !it }.collect {
            val expanded = expansion
            if (expanded <= 0f || expanded >= 1f) return@collect
            val by = if (expanded < 0.5f) offset else offset - restOffset
            scrollable.animateScrollBy(by, spring(stiffness = 600f))
        }
    }
}

/** The large title a [GlassScaffold] gives its top bar and its list, if it has one. */
val LocalLargeTitle = compositionLocalOf<LargeTitleState?> { null }

internal fun newLargeTitleState() = LargeTitleState()

/**
 * The room for the page's large title at the top of its list. Lists that know about large titles,
 * such as [MyLazyColumn] and [PrefsScreen][oolong.client.ui.common.prefs.PrefsScreen], add it
 * themselves; a page that scrolls a plain column puts it first and passes its scroll state to
 * [LargeTitleSettle].
 */
@Composable
fun LargeTitleSlot(modifier: Modifier = Modifier) {
    val state = LocalLargeTitle.current ?: return
    val density = LocalDensity.current
    state.topPx = with(density) { LargeTitleTop.toPx() }
    state.startPx = with(density) { LargeTitleStart.toPx() }
    val bottomPx = with(density) { LargeTitleBottom.toPx() }
    DisposableEffect(state) {
        onDispose { state.slotTop = Float.NaN }
    }
    Spacer(
        modifier = modifier
            .layout { measurable, constraints ->
                val height = (state.topPx + state.titleHeight * LargeTitleScale + bottomPx).roundToInt()
                val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
                layout(placeable.width, height) { placeable.place(0, 0) }
            }
            .onGloballyPositioned {
                val position = it.positionInWindow()
                state.slotTop = position.y
                state.slotStart = position.x
            }
    )
}

/** Settles a half-shrunk large title when [scrollable] stops; see [LargeTitleSlot]. */
@Composable
fun LargeTitleSettle(scrollable: ScrollableState) {
    val state = LocalLargeTitle.current ?: return
    LaunchedEffect(state, scrollable) { state.settle(scrollable) }
}

/**
 * Whether the list scrolled by [scrollable] shows the page's large title, settling it as it
 * stops. Lists that add a [LargeTitleSlot] themselves call this and put the slot first when true.
 */
@Composable
internal fun rememberShowsLargeTitle(scrollable: ScrollableState): Boolean {
    val state = LocalLargeTitle.current ?: return false
    val list = remember { Any() }
    val shows = state.claim(list)
    DisposableEffect(state) {
        onDispose { state.release(list) }
    }
    if (shows) LargeTitleSettle(scrollable)
    return shows
}
