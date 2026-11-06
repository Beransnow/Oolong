package oolong.client.ui.widgets.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import oolong.client.ui.common.theme.compose.CapTextScale
import oolong.client.ui.common.theme.compose.ExtendedTheme
import kotlin.math.roundToInt

/**
 * Space taken at the bottom of the screen by chrome that an ancestor floats over this subtree,
 * such as the main navigation pill. A [GlassScaffold] without its own bottom bar pads its content
 * by this much instead of just the navigation bar.
 */
val LocalChromePadding = staticCompositionLocalOf { PaddingValues(0.dp) }

private enum class GlassScaffoldSlot { Content, TopBar, Fab, Snackbar, Floating }

/**
 * A scaffold whose content fills the whole window and scrolls behind its bars.
 *
 * Unlike the Material scaffold, the content is not placed between the bars: it is measured at
 * full size and receives [PaddingValues] covering the bars, the system bars and display cutouts.
 * The top padding ends at the [GlassTopBarEdge] of a top bar that reports one, leaving out rows
 * that float over the content.
 * Scrollable content should apply them as `contentPadding` so it can scroll behind the bars and
 * still reach every item. Bars are drawn over the content and can sample it through
 * [LocalGlassSource].
 *
 * Bars that hide on scroll should move with an offset or graphics layer, not by changing size,
 * so the padding stays stable and the content does not jump.
 *
 * The padding is one object whose values change as the bars are measured, so content that reads
 * it while it is laid out, such as a lazy list's content padding, follows a bar that changes
 * height in the same frame instead of a frame behind.
 *
 * [overlay] is chrome over the whole scaffold, above the bars: for chrome that grows out of a bar,
 * such as a reply box opening from the bottom bar, which would move the content if it grew inside
 * the bar's slot. The bar's slot then keeps the bar's resting height and the overlay draws it.
 *
 * [sampleContent] false keeps the chrome from sampling the content, for content that can't be
 * drawn twice: a web view drawn into the blur as well alternates between two renders of the page.
 *
 * [largeTitle] gives the page a large title: the top bar's title, shown big below the bar and
 * shrinking into it as the content scrolls up. The content is padded down to make room for it.
 * See [LargeTitleState].
 *
 * [pageScroll] is how the page's content is scrolling; hoist it with [rememberPageScrollState]
 * to read it outside the scaffold.
 *
 * Only the top bar and the content see the page's [LocalLargeTitle] and [LocalPageScroll]; the
 * bottom bar, the overlay and the rest float over the page and have no part in either.
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
    backgroundColor: Color = ExtendedTheme.colors.background,
    contentColor: Color = contentColorFor(backgroundColor),
    overlay: @Composable () -> Unit = {},
    sampleContent: Boolean = true,
    largeTitle: Boolean = false,
    pageScroll: PageScrollState = rememberPageScrollState(),
    content: @Composable (PaddingValues) -> Unit,
) {
    val density = LocalDensity.current
    val titleStyle = MaterialTheme.typography.h6.copy(fontWeight = FontWeight.Bold)
    val measurer = rememberTextMeasurer()
    val chromeStyle = LocalChromeStyle.current
    // A classic bar holds its title itself
    val largeTitleState = if (largeTitle && chromeStyle == ChromeStyle.Floating) {
        remember(density, titleStyle) {
            with(density) {
                // One line of the bar's title, so the room is right before the bar is laid out
                LargeTitleState(
                    lineHeight = measurer.measure("首", titleStyle).size.height,
                    topPx = LargeTitleTop.toPx(),
                    bottomPx = LargeTitleBottom.toPx(),
                    startPx = LargeTitleStart.toPx(),
                )
            }
        }
    } else null
    LaunchedEffect(pageScroll) { pageScroll.settle() }
    // The page's own parts share its large title and scroll edge; what floats over the page has
    // none, rather than an enclosing page's
    val page: @Composable (@Composable () -> Unit) -> Unit = { slot ->
        CompositionLocalProvider(LocalLargeTitle provides largeTitleState, LocalPageScroll provides pageScroll, content = slot)
    }
    val overPage: @Composable (@Composable () -> Unit) -> Unit = { slot ->
        CompositionLocalProvider(LocalLargeTitle provides null, LocalPageScroll provides null, content = slot)
    }
    // On the translucent theme the page joins the wallpaper's state, under its own key, so the
    // chrome blurs the wallpaper too but not the other pages sharing that state
    val wallpaper = LocalWallpaperGlass.current
    val ownState = rememberHazeState()
    val hazeState = wallpaper ?: ownState
    val sourceKey = remember { Any() }
    val glassSource = remember(hazeState) { GlassSource(hazeState, sourceKey.takeIf { wallpaper != null }) }
    val padding = remember { ScaffoldPadding() }
    // The bottom bar's height, as the floating group that holds it with the overlay measured it.
    // Not an alignment line: one read from the group stopped what is in it from moving while its
    // size stayed, as a row scrolled sideways, under the app's lookahead.
    val bottomBarHeightState = remember { mutableIntStateOf(0) }
    val chromePadding = LocalChromePadding.current
    var consumedInsets by remember { mutableStateOf(WindowInsets(0, 0, 0, 0)) }
    val insets = WindowInsets.systemBars
        .union(WindowInsets.displayCutout)
        .exclude(consumedInsets)

    // Only chrome may sample the content; content that samples itself would draw recursively.
    // Its text grows only so far with the text size, as its rows are of a fixed height
    val chrome: @Composable (@Composable () -> Unit) -> Unit = { slot ->
        CompositionLocalProvider(LocalGlassSource provides glassSource) {
            CapTextScale { ProvideGlassAppearance(slot) }
        }
    }

    CompositionLocalProvider(LocalSnackbarHostState provides scaffoldState.snackbarHostState) {
        Surface(
            modifier = modifier
                .onConsumedWindowInsetsChanged { consumedInsets = it }
                .onGloballyPositioned {
                    pageScroll.originX = it.positionInRoot().x
                    pageScroll.frame = it
                }
                // Which way the content is going, for what follows that rather than where it is
                .nestedScroll(pageScroll.scrollWatcher),
            color = backgroundColor,
            contentColor = contentColor,
        ) {
            SubcomposeLayout { constraints ->
                val width = constraints.maxWidth
                val height = constraints.maxHeight
                val loose = constraints.copy(minWidth = 0, minHeight = 0)

                // Composed afresh when the bars' style changes: an open page's bar, composed apart
                // from the page, otherwise kept its floating buttons and large title
                val topBarPlaceables = subcompose(GlassScaffoldSlot.TopBar to chromeStyle) {
                    page { chrome(topBar) }
                }.map { it.measure(loose) }
                val topBarHeight = topBarPlaceables.maxOfOrNull {
                    it[GlassTopBarEdge].takeIf { edge -> edge != AlignmentLine.Unspecified } ?: it.height
                } ?: 0

                // The bottom bar and the overlay are one group, measured together: the overlay can
                // grow out of the bar while the bar's slot keeps its resting height
                val floatingPlaceables = subcompose(GlassScaffoldSlot.Floating) {
                    Layout(
                        contents = listOf(
                            { overPage { chrome(bottomBar) } },
                            { overPage { chrome(overlay) } },
                        ),
                    ) { (bar, over), groupConstraints ->
                        val barPlaceables = bar.map { it.measure(groupConstraints.copy(minWidth = 0, minHeight = 0)) }
                        val barHeight = barPlaceables.maxOfOrNull { it.height } ?: 0
                        bottomBarHeightState.intValue = barHeight
                        val overPlaceables = over.map { it.measure(groupConstraints) }
                        val groupHeight = groupConstraints.maxHeight
                        layout(groupConstraints.maxWidth, groupHeight) {
                            barPlaceables.forEach { it.place(0, groupHeight - barHeight) }
                            overPlaceables.forEach { it.place(0, 0) }
                        }
                    }
                }.map { it.measure(constraints) }
                // Read after the group is measured, and again when the bar's height changes
                val bottomBarHeight = if (floatingPlaceables.isEmpty()) 0 else bottomBarHeightState.intValue

                val top = if (topBarHeight > 0) topBarHeight else insets.getTop(this)
                val bottom = if (bottomBarHeight > 0) bottomBarHeight
                else maxOf(insets.getBottom(this), chromePadding.calculateBottomPadding().roundToPx())
                val left = insets.getLeft(this, layoutDirection)
                val right = insets.getRight(this, layoutDirection)
                val isLtr = layoutDirection == LayoutDirection.Ltr
                padding.start = (if (isLtr) left else right).toDp()
                padding.top = (top + (largeTitleState?.height ?: 0)).toDp()
                padding.end = (if (isLtr) right else left).toDp()
                padding.bottom = bottom.toDp()

                val contentPlaceables = subcompose(GlassScaffoldSlot.Content) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (sampleContent) Modifier.hazeSource(hazeState, key = glassSource.key) else Modifier)
                    ) {
                        page {
                            content(padding)
                            if (largeTitleState != null) LargeTitleAnchorBox(largeTitleState, pageScroll, padding)
                        }
                    }
                }.map { it.measure(constraints) }

                val margin = 16.dp.roundToPx()
                val fabPlaceables = subcompose(GlassScaffoldSlot.Fab) {
                    overPage { chrome(floatingActionButton) }
                }.map { it.measure(loose) }
                val fabHeight = fabPlaceables.maxOfOrNull { it.height } ?: 0
                val fabWidth = fabPlaceables.maxOfOrNull { it.width } ?: 0

                val snackbarPlaceables = subcompose(GlassScaffoldSlot.Snackbar) {
                    overPage { snackbarHost(scaffoldState.snackbarHostState) }
                }.map { it.measure(loose) }
                val snackbarHeight = snackbarPlaceables.maxOfOrNull { it.height } ?: 0
                val fabSpace = if (fabHeight > 0) fabHeight + margin else 0

                layout(width, height) {
                    contentPlaceables.forEach { it.place(0, 0) }
                    topBarPlaceables.forEach { it.place(0, 0) }
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
                    floatingPlaceables.forEach { it.place(0, 0) }
                }
            }
        }
    }
}

/**
 * The padding [GlassScaffold] gives its content, updated as the bars are measured. Its values are
 * state, read wherever the padding is used.
 */
@Stable
private class ScaffoldPadding : PaddingValues {
    var start by mutableStateOf(0.dp)
    var top by mutableStateOf(0.dp)
    var end by mutableStateOf(0.dp)
    var bottom by mutableStateOf(0.dp)

    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp =
        if (layoutDirection == LayoutDirection.Ltr) start else end

    override fun calculateTopPadding(): Dp = top

    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp =
        if (layoutDirection == LayoutDirection.Ltr) end else start

    override fun calculateBottomPadding(): Dp = bottom
}

/**
 * Adds [other] to these padding values side by side, e.g. scaffold padding plus list spacing. The
 * sum is worked out when it is read, so it follows the scaffold's padding as that changes.
 */
@Composable
fun PaddingValues.plus(other: PaddingValues): PaddingValues {
    val base = this
    return remember(base, other) {
        object : PaddingValues {
            override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp =
                base.calculateLeftPadding(layoutDirection) + other.calculateLeftPadding(layoutDirection)

            override fun calculateTopPadding(): Dp = base.calculateTopPadding() + other.calculateTopPadding()

            override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp =
                base.calculateRightPadding(layoutDirection) + other.calculateRightPadding(layoutDirection)

            override fun calculateBottomPadding(): Dp = base.calculateBottomPadding() + other.calculateBottomPadding()
        }
    }
}

/**
 * Where the large title sits below the bar, moving up with the content as it scrolls, for the
 * bar's title to be matched to. It draws nothing.
 *
 * There is no large title of its own: the bar's title is the only one, drawn here, scaled up by
 * this anchor's height over its own, and drawn back in the bar as the content scrolls
 * ([matchedGeometry]). Only the anchor's start, top and height count, so it is 1 px wide.
 */
@Composable
private fun LargeTitleAnchorBox(state: LargeTitleState, page: PageScrollState, padding: ScaffoldPadding) {
    Box(
        modifier = Modifier
            .layout { measurable, _ ->
                val placeable = measurable.measure(Constraints.fixed(1, state.titleHeight.roundToInt()))
                layout(placeable.width, placeable.height) {
                    val top = padding.top.roundToPx() - state.height + state.topPx
                    placeable.place(state.startPx.roundToInt(), top.roundToInt())
                }
            }
            // Far past it, the content's scroll stops mattering: the title is in the bar by then
            .graphicsLayer { translationY = -(page.scrolled ?: 0).coerceAtMost(state.height * 2).toFloat() }
            .matchedGeometryAnchor(LargeTitleAnchor)
    )
}
