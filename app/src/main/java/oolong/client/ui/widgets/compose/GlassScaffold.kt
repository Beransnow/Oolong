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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import oolong.client.ui.common.theme.compose.ExtendedTheme

/**
 * Space taken at the bottom of the screen by chrome that an ancestor floats over this subtree,
 * such as the main navigation pill. A [GlassScaffold] without its own bottom bar pads its content
 * by this much instead of just the navigation bar.
 */
val LocalChromePadding = staticCompositionLocalOf { PaddingValues(0.dp) }

private enum class GlassScaffoldSlot { Content, TopBar, BottomBar, Fab, Snackbar, Overlay }

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
 * Only the top bar and the content see the page's [LocalLargeTitle] and [LocalScrollEdge]; the
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
    content: @Composable (PaddingValues) -> Unit,
) {
    val density = LocalDensity.current
    val titleStyle = MaterialTheme.typography.h6.copy(fontWeight = FontWeight.Bold)
    val measurer = rememberTextMeasurer()
    val largeTitleState = if (largeTitle) {
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
    val scrollEdge = remember { newScrollEdgeState() }
    // The page's own parts share its large title and scroll edge; what floats over the page has
    // none, rather than an enclosing page's
    val page: @Composable (@Composable () -> Unit) -> Unit = { slot ->
        CompositionLocalProvider(LocalLargeTitle provides largeTitleState, LocalScrollEdge provides scrollEdge, content = slot)
    }
    val overPage: @Composable (@Composable () -> Unit) -> Unit = { slot ->
        CompositionLocalProvider(LocalLargeTitle provides null, LocalScrollEdge provides null, content = slot)
    }
    // On the translucent theme the page joins the wallpaper's state, under its own key, so the
    // chrome blurs the wallpaper too but not the other pages sharing that state
    val wallpaper = LocalWallpaperGlass.current
    val ownState = rememberHazeState()
    val hazeState = wallpaper ?: ownState
    val sourceKey = remember { Any() }
    val glassSource = remember(hazeState) { GlassSource(hazeState, sourceKey.takeIf { wallpaper != null }) }
    val padding = remember { ScaffoldPadding() }
    val chromePadding = LocalChromePadding.current
    var consumedInsets by remember { mutableStateOf(WindowInsets(0, 0, 0, 0)) }
    val insets = WindowInsets.systemBars
        .union(WindowInsets.displayCutout)
        .exclude(consumedInsets)

    // Only chrome may sample the content; content that samples itself would draw recursively.
    val chrome: @Composable (@Composable () -> Unit) -> Unit = { slot ->
        CompositionLocalProvider(LocalGlassSource provides glassSource) {
            ProvideGlassAppearance(slot)
        }
    }

    CompositionLocalProvider(LocalSnackbarHostState provides scaffoldState.snackbarHostState) {
        Surface(
            modifier = modifier
                .onConsumedWindowInsetsChanged { consumedInsets = it }
                .onGloballyPositioned { scrollEdge.originX = it.positionInRoot().x },
            color = backgroundColor,
            contentColor = contentColor,
        ) {
            SubcomposeLayout { constraints ->
                val width = constraints.maxWidth
                val height = constraints.maxHeight
                val loose = constraints.copy(minWidth = 0, minHeight = 0)

                val topBarPlaceables = subcompose(GlassScaffoldSlot.TopBar) {
                    page { chrome(topBar) }
                }.map { it.measure(loose) }
                val topBarHeight = topBarPlaceables.maxOfOrNull {
                    it[GlassTopBarEdge].takeIf { edge -> edge != AlignmentLine.Unspecified } ?: it.height
                } ?: 0

                val bottomBarPlaceables = subcompose(GlassScaffoldSlot.BottomBar) {
                    overPage { chrome(bottomBar) }
                }.map { it.measure(loose) }
                val bottomBarHeight = bottomBarPlaceables.maxOfOrNull { it.height } ?: 0

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
                        page { content(padding) }
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

                val overlayPlaceables = subcompose(GlassScaffoldSlot.Overlay) {
                    overPage { chrome(overlay) }
                }.map { it.measure(constraints) }

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
                    overlayPlaceables.forEach { it.place(0, 0) }
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
