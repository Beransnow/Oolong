package oolong.client.ui.widgets.compose

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import oolong.client.ui.common.theme.compose.ExtendedTheme
import oolong.client.ui.common.theme.compose.Motion
import oolong.client.ui.common.theme.compose.PressFeedback
import oolong.client.ui.common.theme.compose.pressScale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/** A [GlassTabRow]'s height, the same as the top bar's buttons. */
val GlassTabRowHeight = 48.dp

/** Space between the capsule's edge and its tabs. */
private val TabInset = 4.dp

private val TabCapsule = RoundedCornerShape(percent = 50)

/** Where each tab was last laid out, for the bubble and for scrolling a tab into view. */
private class TabSpans {
    var starts = IntArray(0)
    var widths = IntArray(0)

    /** The start and width of the bubble at [position], between two tabs where it is fractional. */
    fun at(position: Float): Pair<Float, Float> {
        val count = starts.size
        val p = position.coerceIn(0f, (count - 1).toFloat())
        val from = floor(p).toInt()
        val to = (from + 1).coerceAtMost(count - 1)
        val fraction = p - from
        return lerp(starts[from].toFloat(), starts[to].toFloat(), fraction) to lerp(widths[from].toFloat(), widths[to].toFloat(), fraction)
    }

    fun indexAt(x: Float): Int? = starts.indices.firstOrNull { x >= starts[it] && x < starts[it] + widths[it] }
}

/** Whether the tabs are pressed through a lens, which then stands in for their own press feedback. */
private val LocalTabLens = staticCompositionLocalOf { false }

/**
 * Tabs on a floating glass capsule. The chosen tab sits in a bubble like the bottom bar's chosen
 * item. The capsule is as wide as its tabs, up to the space it has, and scrolls them sideways
 * when they don't fit, keeping the chosen one in view. With [pagerState] the bubble follows the
 * pages as they are swiped.
 *
 * Pressing a tab lifts the bubble off as clear glass, a little larger than the tab, as a switch's
 * knob does, and carries it to that tab; dragging, which scrolls the tabs, puts it back.
 *
 * Tabs are laid out at the capsule's inner height; [GlassTab]s fill it.
 */
@Composable
fun GlassTabRow(
    selectedTabIndex: Int,
    modifier: Modifier = Modifier,
    pagerState: PagerState? = null,
    tabs: @Composable () -> Unit,
) {
    val scroll = rememberScrollState()
    val spans = remember { TabSpans() }
    val animated by animateFloatAsState(selectedTabIndex.toFloat(), Motion.snap(), label = "tabBubble")
    val bubble = ExtendedTheme.colors.emphasis.copy(alpha = BubbleAlpha)
    // Between two tabs while a page is half swiped
    val chosen: () -> Float = { pagerState?.let { it.currentPage + it.currentPageOffsetFraction } ?: animated }

    val glassAvailable = isGlassAvailable()
    val glassState = rememberHazeState()
    val scope = rememberCoroutineScope()
    val touchSlop = LocalViewConfiguration.current.touchSlop
    // While the lens has the bubble, it is where the lens is, until the chosen tab catches up
    val lens = remember { Animatable(0f) }
    var lensActive by remember { mutableStateOf(false) }
    var holding by remember { mutableStateOf(false) }
    val held by animateFloatAsState(
        targetValue = if (holding) 1f else 0f,
        animationSpec = if (holding) Motion.press() else Motion.release(),
        label = "tabHeld",
    )
    val position: () -> Float = { if (lensActive) lens.value else chosen() }
    // Outside the gesture, whose scope allows only its own suspending calls
    val letGo: suspend (released: Boolean) -> Unit = { released ->
        if (released) {
            // The tab picks itself; the bubble stays with the lens until the chosen tab gets there
            withTimeoutOrNull(LensHandOverMillis) {
                snapshotFlow { chosen() }.first { abs(it - lens.value) < 0.02f }
            }
        } else {
            lens.animateTo(chosen(), Motion.snap())
        }
        if (!holding) lensActive = false
    }
    val inset = with(LocalDensity.current) { TabInset.roundToPx() }

    Box(modifier = modifier.height(GlassTabRowHeight)) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .floatingGlass(TabCapsule)
                .clip(TabCapsule)
                // What the lens sees: the tabs and the bubble, which fades from under it
                .hazeSource(glassState),
            contentAlignment = Alignment.CenterStart,
        ) {
            CompositionLocalProvider(LocalTabLens provides glassAvailable) {
                Layout(
                    content = tabs,
                    modifier = Modifier
                        .horizontalScroll(scroll)
                        .padding(horizontal = TabInset)
                        .selectableGroup()
                        .drawBehind {
                            if (spans.starts.isEmpty()) return@drawBehind
                            val (start, width) = spans.at(position())
                            drawRoundRect(
                                color = bubble.copy(alpha = bubble.alpha * if (glassAvailable) 1f - held else 1f),
                                topLeft = Offset(start, 0f),
                                size = Size(width, size.height),
                                cornerRadius = CornerRadius(size.height / 2),
                            )
                        }
                        .then(
                            if (!glassAvailable) {
                                Modifier
                            } else {
                                // Watches presses without taking them: the tabs still get their clicks
                                // and the row its scrolling
                                Modifier.pointerInput(Unit) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
                                        val index = spans.indexAt(down.position.x) ?: return@awaitEachGesture
                                        val from = position()
                                        lensActive = true
                                        holding = true
                                        scope.launch {
                                            lens.snapTo(from)
                                            lens.animateTo(index.toFloat(), Motion.snap())
                                        }
                                        var released = false
                                        while (true) {
                                            val change = awaitPointerEvent(PointerEventPass.Final).changes
                                                .firstOrNull { it.id == down.id } ?: break
                                            if (!change.pressed) {
                                                released = true
                                                break
                                            }
                                            if (change.isConsumed || (change.position - down.position).getDistance() > touchSlop) break
                                        }
                                        holding = false
                                        scope.launch { letGo(released) }
                                    }
                                }
                            }
                        ),
                ) { measurables, _ ->
                    val height = (GlassTabRowHeight - TabInset * 2).roundToPx()
                    val placeables = measurables.map {
                        it.measure(Constraints(minHeight = height, maxHeight = height))
                    }
                    val starts = IntArray(placeables.size)
                    var x = 0
                    placeables.forEachIndexed { i, placeable ->
                        starts[i] = x
                        x += placeable.width
                    }
                    spans.starts = starts
                    spans.widths = IntArray(placeables.size) { placeables[it].width }
                    layout(x, height) {
                        placeables.forEachIndexed { i, placeable -> placeable.place(starts[i], 0) }
                    }
                }
            }
        }
        if (glassAvailable && held > 0f) {
            // Over the capsule, which it grows past, so outside its clip and its size
            Box(modifier = Modifier.matchParentSize()) {
                Box(
                    modifier = Modifier
                        .wrapContentSize(Alignment.TopStart, unbounded = true)
                        .layout { measurable, _ ->
                            if (spans.starts.isEmpty()) return@layout layout(0, 0) {}
                            val (start, width) = spans.at(lens.value)
                            val growWidth = HeldGrowthWidth.toPx() * held
                            val growHeight = HeldGrowthHeight.toPx() * held
                            val tabHeight = GlassTabRowHeight.toPx() - inset * 2
                            val w = (width + growWidth).roundToInt()
                            val h = (tabHeight + growHeight).roundToInt()
                            val placeable = measurable.measure(Constraints.fixed(w, h))
                            layout(w, h) {
                                placeable.place(
                                    (inset + start - scroll.value - growWidth / 2).roundToInt(),
                                    (inset - growHeight / 2).roundToInt(),
                                )
                            }
                        }
                        .controlLens(glassState, TabCapsule, held)
                )
            }
        }
    }

    // Brings the chosen tab to the middle of the capsule, as far as the ends allow
    LaunchedEffect(selectedTabIndex) {
        // Laid out by the next frame, also when the row first appears
        withFrameNanos { }
        if (selectedTabIndex !in spans.starts.indices) return@LaunchedEffect
        val center = inset + spans.starts[selectedTabIndex] + spans.widths[selectedTabIndex] / 2
        scroll.animateScrollTo((center - scroll.viewportSize / 2).coerceIn(0, scroll.maxValue), Motion.settle())
    }
}

/** How much the bubble grows as it lifts off, as a switch's knob does. */
private val HeldGrowthWidth = 16.dp
private val HeldGrowthHeight = 14.dp

/** How long the lens keeps the bubble after a tap, at most, for the chosen tab to get there. */
private const val LensHandOverMillis = 800L

/** How much of the bottom bar's emphasis the bubble takes, as the bottom bar's chosen item does. */
private const val BubbleAlpha = 0.12f

/**
 * A tab for [GlassTabRow]. Pressed, the row's lens shows it; where glass isn't drawn, it shrinks a
 * little under a veil the shape of the bubble, as the app's other controls do. [content] is
 * centred in a row.
 */
@Composable
fun GlassTab(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selectedContentColor: Color = ExtendedTheme.colors.text,
    unselectedContentColor: Color = ExtendedTheme.colors.textSecondary,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val color by animateColorAsState(if (selected) selectedContentColor else unselectedContentColor, label = "tabColor")
    val lens = LocalTabLens.current
    CompositionLocalProvider(LocalContentColor provides color, LocalContentAlpha provides 1f) {
        Row(
            modifier = modifier
                .then(if (lens) Modifier else Modifier.pressScale(interactionSource))
                .clip(TabCapsule)
                .selectable(
                    selected = selected,
                    interactionSource = interactionSource,
                    indication = if (lens) null else PressFeedback.Overlay,
                    enabled = enabled,
                    role = Role.Tab,
                    onClick = onClick,
                )
                .fillMaxHeight(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** A [GlassTab] of [text], set and spaced as a Material tab's text is. */
@Composable
fun GlassTab(
    selected: Boolean,
    onClick: () -> Unit,
    text: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    selectedContentColor: Color = ExtendedTheme.colors.text,
    unselectedContentColor: Color = ExtendedTheme.colors.textSecondary,
) {
    GlassTab(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        selectedContentColor = selectedContentColor,
        unselectedContentColor = unselectedContentColor,
    ) {
        ProvideTextStyle(MaterialTheme.typography.button) {
            Box(modifier = Modifier.padding(horizontal = 16.dp)) { text() }
        }
    }
}
