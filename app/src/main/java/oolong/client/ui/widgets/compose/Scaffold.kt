package oolong.client.ui.widgets.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.material.DrawerDefaults
import androidx.compose.material.FabPosition
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.ScaffoldState
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.contentColorFor
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import oolong.client.ui.common.theme.compose.ExtendedTheme
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The snackbars, which a sideways swipe past half their width, or a fling, dismisses. Its offset is
 * always a number, unlike the library's swipe-to-dismiss, whose offset is undefined until it has
 * been measured and which then fails when a lookahead pass places it first.
 */
@Composable
internal fun SwipeToDismissSnackbarHost(hostState: SnackbarHostState) {
    val data = hostState.currentSnackbarData
    val offset = remember(data) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var width by remember { mutableIntStateOf(0) }
    val dragState = rememberDraggableState { delta -> scope.launch { offset.snapTo(offset.value + delta) } }
    Box(
        modifier = Modifier
            .onSizeChanged { width = it.width }
            .offset { IntOffset(offset.value.roundToInt(), 0) }
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                enabled = data != null,
                onDragStopped = { velocity ->
                    val dismiss = width > 0 && (abs(offset.value) > width / 2f || abs(velocity) > SnackbarFlingVelocity)
                    if (dismiss) {
                        val direction = if (offset.value + velocity >= 0f) 1 else -1
                        offset.animateTo(direction * width.toFloat())
                        data?.dismiss()
                    } else {
                        offset.animateTo(0f)
                    }
                },
            ),
    ) {
        SnackbarHost(hostState = hostState)
    }
}

/** How fast a fling, in pixels a second, dismisses a snackbar however far it went. */
private const val SnackbarFlingVelocity = 2000f

val LocalSnackbarHostState = compositionLocalOf<SnackbarHostState> { error("no scaffold here!") }

@Composable
fun MyScaffold(
    modifier: Modifier = Modifier,
    scaffoldState: ScaffoldState = rememberScaffoldState(),
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable (SnackbarHostState) -> Unit = { SwipeToDismissSnackbarHost(it) },
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    isFloatingActionButtonDocked: Boolean = false,
    drawerContent: @Composable (ColumnScope.() -> Unit)? = null,
    drawerGesturesEnabled: Boolean = true,
    drawerShape: Shape = MaterialTheme.shapes.large,
    drawerElevation: Dp = DrawerDefaults.Elevation,
    drawerBackgroundColor: Color = ExtendedTheme.colors.card,
    drawerContentColor: Color = contentColorFor(drawerBackgroundColor),
    drawerScrimColor: Color = DrawerDefaults.scrimColor,
    backgroundColor: Color = ExtendedTheme.colors.background,
    contentColor: Color = contentColorFor(backgroundColor),
    content: @Composable (PaddingValues) -> Unit
) {
    CompositionLocalProvider(LocalSnackbarHostState provides scaffoldState.snackbarHostState) {
        Scaffold(
            modifier,
            scaffoldState,
            topBar,
            bottomBar,
            snackbarHost,
            floatingActionButton,
            floatingActionButtonPosition,
            isFloatingActionButtonDocked,
            drawerContent,
            drawerGesturesEnabled,
            drawerShape,
            drawerElevation,
            drawerBackgroundColor,
            drawerContentColor,
            drawerScrimColor,
            backgroundColor,
            contentColor,
            content
        )
    }
}
