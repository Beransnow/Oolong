package oolong.client.ui.widgets.compose

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import oolong.client.ui.common.theme.compose.ExtendedTheme
import oolong.client.ui.common.theme.compose.Motion
import kotlin.math.floor

/** Whether tabs are in a [ClassicTabRow], which they are then styled for. */
internal val LocalClassicTabs = staticCompositionLocalOf { false }

/**
 * A [GlassTabRow] in the classic style, the app's tabs from before the glass: centred when they
 * fit and scrolling when they don't, the chosen one bold over a short rounded line; with
 * [pagerState] the line follows the pages as they are swiped.
 */
@Composable
internal fun ClassicTabRow(
    selectedTabIndex: Int,
    modifier: Modifier,
    pagerState: PagerState?,
    tabs: @Composable () -> Unit,
) {
    val scroll = rememberScrollState()
    val starts = remember { IntArray(0).let { arrayOf(it, it) } }
    val animated by animateFloatAsState(selectedTabIndex.toFloat(), Motion.snap(), label = "classicTabLine")
    val colors = ExtendedTheme.colors
    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(ClassicTabRowHeight)) {
        val available = constraints.maxWidth
        CompositionLocalProvider(LocalClassicTabs provides true) {
            Layout(
                content = tabs,
                modifier = Modifier
                    .horizontalScroll(scroll)
                    .selectableGroup()
                    .drawBehind {
                        val (tabStarts, tabWidths) = starts
                        val count = tabStarts.size
                        if (count == 0) return@drawBehind
                        val position = (pagerState?.let { it.currentPage + it.currentPageOffsetFraction } ?: animated)
                            .coerceIn(0f, (count - 1).toFloat())
                        val from = floor(position).toInt()
                        val to = (from + 1).coerceAtMost(count - 1)
                        val fraction = position - from
                        val center = lerp(
                            tabStarts[from] + tabWidths[from] / 2f,
                            tabStarts[to] + tabWidths[to] / 2f,
                            fraction,
                        )
                        val width = ClassicTabLineWidth.toPx()
                        val line = ClassicTabLine.toPx()
                        drawRoundRect(
                            color = colors.text,
                            topLeft = Offset(center - width / 2, size.height - ClassicTabLineBottom.toPx() - line),
                            size = Size(width, line),
                            cornerRadius = CornerRadius(line / 2),
                        )
                    },
            ) { measurables, _ ->
                val height = ClassicTabRowHeight.roundToPx()
                val minWidth = ClassicTabMinWidth.roundToPx()
                val placeables = measurables.map {
                    it.measure(Constraints(minWidth = minWidth, minHeight = height, maxHeight = height))
                }
                val total = placeables.sumOf { it.width }
                // Centred while they fit
                val lead = ((available - total) / 2).coerceAtLeast(0)
                val tabStarts = IntArray(placeables.size)
                var x = lead
                placeables.forEachIndexed { i, placeable ->
                    tabStarts[i] = x
                    x += placeable.width
                }
                starts[0] = tabStarts
                starts[1] = IntArray(placeables.size) { placeables[it].width }
                layout(maxOf(x + lead, available), height) {
                    placeables.forEachIndexed { i, placeable -> placeable.place(tabStarts[i], 0) }
                }
            }
        }
        // Brings the chosen tab to the middle, as far as the ends allow
        LaunchedEffect(selectedTabIndex) {
            withFrameNanos { }
            val (tabStarts, tabWidths) = starts
            if (selectedTabIndex !in tabStarts.indices) return@LaunchedEffect
            val center = tabStarts[selectedTabIndex] + tabWidths[selectedTabIndex] / 2
            scroll.animateScrollTo((center - scroll.viewportSize / 2).coerceIn(0, scroll.maxValue), Motion.settle())
        }
    }
}

/** A classic tab row's height. */
val ClassicTabRowHeight = 48.dp

/** The narrowest a classic tab is, so a few short tabs spread out a little. */
private val ClassicTabMinWidth = 76.dp

private val ClassicTabLine = 3.dp
private val ClassicTabLineWidth = 16.dp

/** How far above the row's bottom the line sits. */
private val ClassicTabLineBottom = 8.dp
