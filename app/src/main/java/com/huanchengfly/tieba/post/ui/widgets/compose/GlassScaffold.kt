package com.huanchengfly.tieba.post.ui.widgets.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.onConsumedWindowInsetsChanged
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ScaffoldState
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Surface
import androidx.compose.material.contentColorFor
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * Space taken at the bottom of the screen by chrome that an ancestor floats over this subtree,
 * such as the main navigation pill. A [GlassScaffold] without its own bottom bar pads its content
 * by this much instead of just the navigation bar.
 */
val LocalChromePadding = staticCompositionLocalOf { PaddingValues(0.dp) }

private enum class GlassScaffoldSlot { Content, TopBar, BottomBar, Fab, Snackbar }

/**
 * A scaffold whose content fills the whole window and scrolls behind its bars.
 *
 * Unlike the Material scaffold, the content is not placed between the bars: it is measured at
 * full size and receives [PaddingValues] covering the bars, the system bars and display cutouts.
 * Scrollable content should apply them as `contentPadding` so it can scroll behind the bars and
 * still reach every item. Bars are drawn over the content and can sample it through
 * [LocalGlassSource].
 *
 * Bars that hide on scroll should move with an offset or graphics layer, not by changing size,
 * so the padding stays stable and the content does not jump.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GlassScaffold(
    modifier: Modifier = Modifier,
    scaffoldState: ScaffoldState = rememberScaffoldState(),
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHost: @Composable (SnackbarHostState) -> Unit = { SwipeToDismissSnackbarHost(it) },
    backgroundColor: Color = MaterialTheme.colors.background,
    contentColor: Color = contentColorFor(backgroundColor),
    content: @Composable (PaddingValues) -> Unit,
) {
    val hazeState = rememberHazeState()
    val chromePadding = LocalChromePadding.current
    var consumedInsets by remember { mutableStateOf(WindowInsets(0, 0, 0, 0)) }
    val insets = WindowInsets.systemBars
        .union(WindowInsets.displayCutout)
        .exclude(consumedInsets)

    // Only chrome may sample the content; content that samples itself would draw recursively.
    val chrome: @Composable (@Composable () -> Unit) -> Unit = { slot ->
        CompositionLocalProvider(LocalGlassSource provides hazeState) {
            ProvideGlassAppearance(slot)
        }
    }

    CompositionLocalProvider(LocalSnackbarHostState provides scaffoldState.snackbarHostState) {
        Surface(
            modifier = modifier.onConsumedWindowInsetsChanged { consumedInsets = it },
            color = backgroundColor,
            contentColor = contentColor,
        ) {
            SubcomposeLayout { constraints ->
                val width = constraints.maxWidth
                val height = constraints.maxHeight
                val loose = constraints.copy(minWidth = 0, minHeight = 0)

                val topBarPlaceables = subcompose(GlassScaffoldSlot.TopBar) {
                    chrome(topBar)
                }.map { it.measure(loose) }
                val topBarHeight = topBarPlaceables.maxOfOrNull { it.height } ?: 0

                val bottomBarPlaceables = subcompose(GlassScaffoldSlot.BottomBar) {
                    chrome(bottomBar)
                }.map { it.measure(loose) }
                val bottomBarHeight = bottomBarPlaceables.maxOfOrNull { it.height } ?: 0

                val top = if (topBarHeight > 0) topBarHeight else insets.getTop(this)
                val bottom = if (bottomBarHeight > 0) bottomBarHeight
                else maxOf(insets.getBottom(this), chromePadding.calculateBottomPadding().roundToPx())
                val left = insets.getLeft(this, layoutDirection)
                val right = insets.getRight(this, layoutDirection)
                val isLtr = layoutDirection == LayoutDirection.Ltr
                val padding = PaddingValues(
                    start = (if (isLtr) left else right).toDp(),
                    top = top.toDp(),
                    end = (if (isLtr) right else left).toDp(),
                    bottom = bottom.toDp(),
                )

                val contentPlaceables = subcompose(GlassScaffoldSlot.Content) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .hazeSource(hazeState)
                    ) {
                        content(padding)
                    }
                }.map { it.measure(constraints) }

                val margin = 16.dp.roundToPx()
                val fabPlaceables = subcompose(GlassScaffoldSlot.Fab) {
                    chrome(floatingActionButton)
                }.map { it.measure(loose) }
                val fabHeight = fabPlaceables.maxOfOrNull { it.height } ?: 0
                val fabWidth = fabPlaceables.maxOfOrNull { it.width } ?: 0

                val snackbarPlaceables = subcompose(GlassScaffoldSlot.Snackbar) {
                    snackbarHost(scaffoldState.snackbarHostState)
                }.map { it.measure(loose) }
                val snackbarHeight = snackbarPlaceables.maxOfOrNull { it.height } ?: 0
                val fabSpace = if (fabHeight > 0) fabHeight + margin else 0

                layout(width, height) {
                    contentPlaceables.forEach { it.place(0, 0) }
                    topBarPlaceables.forEach { it.place(0, 0) }
                    bottomBarPlaceables.forEach { it.place(0, height - bottomBarHeight) }
                    fabPlaceables.forEach {
                        val x = if (isLtr) width - right - margin - fabWidth else left + margin
                        it.place(x, height - bottom - margin - fabHeight)
                    }
                    snackbarPlaceables.forEach {
                        it.place(
                            (width - it.width) / 2,
                            height - bottom - fabSpace - snackbarHeight
                        )
                    }
                }
            }
        }
    }
}

/** Adds [other] to these padding values side by side, e.g. scaffold padding plus list spacing. */
@Composable
fun PaddingValues.plus(other: PaddingValues): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(layoutDirection) + other.calculateStartPadding(layoutDirection),
        top = calculateTopPadding() + other.calculateTopPadding(),
        end = calculateEndPadding(layoutDirection) + other.calculateEndPadding(layoutDirection),
        bottom = calculateBottomPadding() + other.calculateBottomPadding(),
    )
}
