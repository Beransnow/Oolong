package com.huanchengfly.tieba.post.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.arch.BaseComposeActivity.Companion.LocalWindowSizeClass
import com.huanchengfly.tieba.post.ui.common.navigation.LocalIsPop
import com.huanchengfly.tieba.post.ui.common.navigation.PageTransitions
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.common.windowsizeclass.WindowSizeClass
import kotlinx.coroutines.launch

/**
 * How the two panes share the window: the list pane's width, set by dragging the divider, and
 * whether the list is folded away so the open page has the whole width.
 */
@Stable
class ListDetailState(listWidth: Dp = DefaultListWidth) {
    var listWidth by mutableStateOf(listWidth)

    /** Lasts until the list is the top page again, as opening something new from it would need it shown. */
    var collapsed by mutableStateOf(false)

    companion object {
        val DefaultListWidth = 400.dp
        val MinListWidth = 320.dp
        val MaxListWidth = 560.dp
        val MinDetailWidth = 400.dp

        /** Releasing the divider left of this folds the list away. */
        val CollapseBelow = 200.dp
    }
}

/**
 * Shows the last list on the stack on the left and the last page it opened on the right, or an
 * empty pane while there is none. Pages over the whole window, such as logging in, fall through
 * to the single pane.
 */
class ListDetailSceneStrategy<T : Any>(
    private val state: ListDetailState,
    private val onListWidthChange: (Dp?) -> Unit,
) : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val top = entries.last()
        if (top.paneRole != PaneRole.List && top.paneRole != PaneRole.Detail) return null
        val list = entries.lastOrNull { it.paneRole == PaneRole.List } ?: return null
        val detail = entries.lastOrNull { it.paneRole == PaneRole.Detail }
        return ListDetailScene(
            list = list,
            detail = detail,
            listOnTop = top === list,
            previousEntries = entries.dropLast(1),
            state = state,
            onListWidthChange = onListWidthChange,
        )
    }
}

private class ListDetailScene<T : Any>(
    private val list: NavEntry<T>,
    private val detail: NavEntry<T>?,
    private val listOnTop: Boolean,
    // Back removes the top page only, whichever pane it is in
    override val previousEntries: List<NavEntry<T>>,
    private val state: ListDetailState,
    private val onListWidthChange: (Dp?) -> Unit,
) : Scene<T> {
    // One key for every pair, so the display keeps this scene and each pane animates its own change
    override val key: Any = ListDetailScene::class

    override val entries: List<NavEntry<T>> = listOfNotNull(list, detail)

    override val content: @Composable () -> Unit = {
        ListDetailLayout(list, detail, listOnTop, state, onListWidthChange)
    }

    override fun equals(other: Any?): Boolean =
        other is ListDetailScene<*> && list == other.list && detail == other.detail &&
            listOnTop == other.listOnTop && previousEntries == other.previousEntries

    override fun hashCode(): Int = listOf(list, detail, listOnTop, previousEntries).hashCode()
}

@Composable
private fun <T : Any> ListDetailLayout(
    list: NavEntry<T>,
    detail: NavEntry<T>?,
    listOnTop: Boolean,
    state: ListDetailState,
    onListWidthChange: (Dp?) -> Unit,
) {
    LaunchedEffect(listOnTop, detail) {
        if (listOnTop || detail == null) state.collapsed = false
    }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ExtendedTheme.colors.background)
    ) {
        val maxListWidth = (maxWidth - ListDetailState.MinDetailWidth)
            .coerceIn(ListDetailState.MinListWidth, ListDetailState.MaxListWidth)
        val listWidth = state.listWidth.coerceIn(ListDetailState.MinListWidth, maxListWidth)
        val target = if (state.collapsed) 0.dp else listWidth
        val shown = remember { Animatable(target, Dp.VectorConverter) }
        var dragging by remember { mutableStateOf(false) }
        LaunchedEffect(target) {
            if (!dragging) shown.animateTo(target, DividerSpring)
        }
        val shownWidth = shown.value

        // The list keeps its width while it folds away, sliding under the edge, so its text does
        // not reflow on every frame
        Box(
            modifier = Modifier
                .width(listWidth)
                .fillMaxHeight()
                .offset(x = shownWidth - listWidth)
                .consumeWindowInsets(PaneInsets.only(WindowInsetsSides.End))
                .clipToBounds()
        ) {
            if (shownWidth > 0.dp) Pane(list)
        }
        Box(
            modifier = Modifier
                .offset(x = shownWidth)
                .width(maxWidth - shownWidth)
                .fillMaxHeight()
                .consumeWindowInsets(PaneInsets.only(WindowInsetsSides.Start))
                .clipToBounds()
        ) {
            Pane(detail)
        }

        val coroutineScope = rememberCoroutineScope()
        val density = LocalDensity.current
        val dragState = rememberDraggableState { delta ->
            val width = shown.value + with(density) { delta.toDp() }
            coroutineScope.launch { shown.snapTo(width.coerceIn(0.dp, maxListWidth)) }
        }
        Divider(
            modifier = Modifier
                .offset(x = shownWidth - DividerTouchWidth / 2)
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    onDragStarted = { dragging = true },
                    onDragStopped = {
                        dragging = false
                        val released = shown.value
                        if (released < ListDetailState.CollapseBelow && detail != null) {
                            state.collapsed = true
                            shown.animateTo(0.dp, DividerSpring)
                        } else {
                            val width = released.coerceIn(ListDetailState.MinListWidth, maxListWidth)
                            state.collapsed = false
                            state.listWidth = width
                            onListWidthChange(width)
                            shown.animateTo(width, DividerSpring)
                        }
                    },
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            state.collapsed = false
                            state.listWidth = ListDetailState.DefaultListWidth
                            onListWidthChange(null)
                        }
                    )
                },
        )
    }
}

/** A pane showing [entry], or the empty pane while there is none, animating as pages change in it. */
@Composable
private fun <T : Any> Pane(entry: NavEntry<T>?) {
    val isPop = LocalIsPop.current
    val overlap = PageTransitions.rememberOverlap()
    // Pages size themselves by the window; in a pane, the pane is their window
    BoxWithConstraints {
        val paneSizeClass = WindowSizeClass.calculateFromSize(DpSize(maxWidth, maxHeight))
        AnimatedContent(
            targetState = entry,
            contentKey = { it?.contentKey },
            transitionSpec = {
                if (isPop) PageTransitions.popTransitionSpec<NavEntry<T>?>(overlap)()
                else PageTransitions.transitionSpec<NavEntry<T>?>(overlap)()
            },
            modifier = Modifier.fillMaxSize(),
            label = "Pane",
        ) { shown ->
            CompositionLocalProvider(
                LocalNavAnimatedContentScope provides this,
                LocalWindowSizeClass provides paneSizeClass,
            ) {
                PageTransitions.PageFrame {
                    if (shown == null) EmptyPane() else shown.Content()
                }
            }
        }
    }
}

/** The right pane before anything is open: the app's mark, faintly. */
@Composable
private fun EmptyPane() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Drained of colour rather than tinted flat, so the mark keeps its inner lines
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_new_monochrome),
            contentDescription = null,
            colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
            alpha = if (ExtendedTheme.colors.isNightMode) 0.12f else 0.25f,
            modifier = Modifier.size(160.dp),
        )
    }
}

/** A hairline between the panes with a grip in the middle; wider to touch than it looks. */
@Composable
private fun Divider(modifier: Modifier) {
    Box(
        modifier = modifier
            .width(DividerTouchWidth)
            .fillMaxHeight(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(ExtendedTheme.colors.divider)
        )
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 36.dp)
                .background(ExtendedTheme.colors.textDisabled.copy(alpha = 0.5f), GripShape)
        )
    }
}

private val PaneInsets: WindowInsets
    @Composable get() = WindowInsets.systemBars.union(WindowInsets.displayCutout)
private val DividerTouchWidth = 16.dp
private val GripShape = RoundedCornerShape(percent = 50)
private val DividerSpring = spring<Dp>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
