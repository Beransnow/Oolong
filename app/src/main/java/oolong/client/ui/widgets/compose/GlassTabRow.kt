package oolong.client.ui.widgets.compose

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import oolong.client.ui.common.theme.compose.ExtendedTheme
import oolong.client.ui.common.theme.compose.Motion
import kotlin.math.floor

/** A [GlassTabRow]'s height, the same as the top bar's buttons. */
val GlassTabRowHeight = 48.dp

/** Space between the capsule's edge and its tabs. */
private val TabInset = 4.dp

private val TabCapsule = RoundedCornerShape(percent = 50)

/** Where each tab was last laid out, for the bubble and for scrolling a tab into view. */
private class TabSpans {
    var starts = IntArray(0)
    var widths = IntArray(0)
}

/**
 * Tabs on a floating glass capsule. The chosen tab sits in a bubble like the bottom bar's chosen
 * item. The capsule is as wide as its tabs, up to the space it has, and scrolls them sideways
 * when they don't fit, keeping the chosen one in view. With [pagerState] the bubble follows the
 * pages as they are swiped.
 *
 * Tabs are laid out at the capsule's inner height, so ordinary [androidx.compose.material.Tab]s
 * fit as they are.
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

    Box(
        modifier = modifier
            .height(GlassTabRowHeight)
            .floatingGlass(TabCapsule)
            .clip(TabCapsule),
        contentAlignment = Alignment.CenterStart,
    ) {
        Layout(
            content = tabs,
            modifier = Modifier
                .horizontalScroll(scroll)
                .padding(horizontal = TabInset)
                .selectableGroup()
                .drawBehind {
                    val count = spans.starts.size
                    if (count == 0) return@drawBehind
                    // Between two tabs while a page is half swiped
                    val position = (pagerState?.let { it.currentPage + it.currentPageOffsetFraction } ?: animated)
                        .coerceIn(0f, (count - 1).toFloat())
                    val from = floor(position).toInt()
                    val to = (from + 1).coerceAtMost(count - 1)
                    val fraction = position - from
                    val start = lerp(spans.starts[from].toFloat(), spans.starts[to].toFloat(), fraction)
                    val width = lerp(spans.widths[from].toFloat(), spans.widths[to].toFloat(), fraction)
                    drawRoundRect(
                        color = bubble,
                        topLeft = Offset(start, 0f),
                        size = Size(width, size.height),
                        cornerRadius = CornerRadius(size.height / 2),
                    )
                },
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

    // Brings the chosen tab to the middle of the capsule, as far as the ends allow
    val inset = with(LocalDensity.current) { TabInset.roundToPx() }
    LaunchedEffect(selectedTabIndex) {
        // Laid out by the next frame, also when the row first appears
        withFrameNanos { }
        if (selectedTabIndex !in spans.starts.indices) return@LaunchedEffect
        val center = inset + spans.starts[selectedTabIndex] + spans.widths[selectedTabIndex] / 2
        scroll.animateScrollTo((center - scroll.viewportSize / 2).coerceIn(0, scroll.maxValue), Motion.settle())
    }
}

/** How much of the bottom bar's emphasis the bubble takes, as the bottom bar's chosen item does. */
private const val BubbleAlpha = 0.12f
