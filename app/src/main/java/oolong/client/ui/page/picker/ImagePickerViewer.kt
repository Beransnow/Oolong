package oolong.client.ui.page.picker

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.panpf.zoomimage.CoilZoomAsyncImage
import com.github.panpf.zoomimage.CoilZoomState
import com.github.panpf.zoomimage.rememberCoilZoomState
import com.github.panpf.zoomimage.zoom.Edge
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch
import oolong.client.R
import oolong.client.model.DeviceImage
import oolong.client.ui.common.theme.compose.Motion
import oolong.client.ui.common.theme.compose.SmoothCornerShape
import oolong.client.ui.common.theme.compose.Symbols
import oolong.client.ui.widgets.compose.Button
import oolong.client.ui.widgets.compose.MediaGlassButton
import oolong.client.ui.widgets.compose.MediaGlassIconButton
import oolong.client.ui.widgets.compose.MediaGlassPill
import oolong.client.ui.widgets.compose.ProvideMediaGlass
import oolong.client.ui.widgets.compose.mediaGlass
import kotlin.math.abs

/**
 * [pictures] large, from [start], to swipe through, zoom into and close with a drag down. The
 * circle picks the picture shown; 编辑 opens it in the picture editor; the tray along the bottom
 * shows the picks, and its button says what it gives back: the picture shown when nothing else
 * is picked, or with one to pick, otherwise every pick.
 */
@Composable
internal fun PickerViewer(
    pictures: List<DeviceImage>,
    start: Int,
    state: ImagePickerState,
    onToggle: (Uri) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onEdit: (Uri) -> Unit,
    onDone: (List<Uri>) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    if (pictures.isEmpty()) return
    val pagerState = rememberPagerState(initialPage = start.coerceIn(0, pictures.lastIndex)) { pictures.size }
    val scope = rememberCoroutineScope()
    val glass = rememberHazeState()
    val zoomStates = remember { mutableStateMapOf<Int, CoilZoomState>() }
    val shown = remember { Animatable(0f, Motion.ProgressThreshold) }
    LaunchedEffect(Unit) { shown.animateTo(1f, Motion.present(Motion.ProgressThreshold)) }
    // How far a drag down has carried the picture
    var drag by remember { mutableStateOf(Offset.Zero) }
    val picture = pictures[pagerState.currentPage.coerceIn(0, pictures.lastIndex)]
    val order = state.selected.indexOf(picture.uri).takeIf { it >= 0 }?.plus(1)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val height = constraints.maxHeight.toFloat()
        val dragScale = 1f - (drag.y.coerceAtLeast(0f) / height).coerceIn(0f, 1f) * 0.45f
        val dragFade = 1f - (abs(drag.y) / (height * 0.5f)).coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = shown.value * dragFade }
                .background(Color.Black)
                // Taps do not fall through to the grid under the viewer
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(glass)
                .graphicsLayer {
                    alpha = shown.value
                    translationX = drag.x
                    translationY = drag.y
                    scaleX = dragScale
                    scaleY = dragScale
                    transformOrigin = TransformOrigin.Center
                }
                .pointerInput(pagerState) {
                    val slop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        if (!zoomStates[pagerState.currentPage].atRest()) return@awaitEachGesture
                        val tracker = VelocityTracker()
                        var total = Offset.Zero
                        var active = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            // A second finger is a pinch, for the picture to zoom
                            if (event.changes.count { it.pressed } > 1) break
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                if (active) {
                                    val velocity = tracker.calculateVelocity()
                                    if (drag.y > 96.dp.toPx() || velocity.y > 900.dp.toPx()) {
                                        onClose()
                                    } else {
                                        scope.launch {
                                            Animatable(drag, Offset.VectorConverter).animateTo(Offset.Zero, Motion.settle()) { drag = value }
                                        }
                                    }
                                }
                                break
                            }
                            val delta = change.positionChange()
                            if (!active) {
                                total += delta
                                if (total.y > slop && abs(total.y) > abs(total.x) * 1.5f) {
                                    active = true
                                } else if (abs(total.x) > slop || total.y < -slop) {
                                    break
                                }
                            }
                            if (active) {
                                change.consume()
                                drag += delta
                                tracker.addPosition(change.uptimeMillis, change.position)
                            }
                        }
                    }
                },
        ) {
            HorizontalPager(
                state = pagerState,
                pageSpacing = 16.dp,
                key = { pictures[it].uri.toString() },
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val zoomState = rememberCoilZoomState()
                DisposableEffect(page, zoomState) {
                    zoomStates[page] = zoomState
                    onDispose { if (zoomStates[page] === zoomState) zoomStates.remove(page) }
                }
                val uri = pictures[page].uri
                CoilZoomAsyncImage(
                    model = state.edits[uri] ?: uri,
                    contentDescription = null,
                    zoomState = zoomState,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        ProvideMediaGlass(glass) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .graphicsLayer { alpha = shown.value * dragFade }
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .fillMaxWidth(),
            ) {
                MediaGlassIconButton(
                    icon = Symbols.ArrowBack,
                    description = stringResource(id = R.string.button_back),
                    onClick = onClose,
                )
                if (pictures.size > 1) {
                    MediaGlassPill {
                        Text(
                            text = stringResource(id = R.string.title_picker_index, pagerState.currentPage + 1, pictures.size),
                            color = Color.White,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                }
                MediaGlassButton(
                    onClick = { onToggle(picture.uri) },
                    onClickLabel = stringResource(id = R.string.desc_picker_select),
                ) {
                    PickCircle(order = order, size = 26.dp, filled = Color.White, onFilled = Color.Black)
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer { alpha = shown.value * dragFade }
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth()
                    .height(64.dp)
                    .mediaGlass(SmoothCornerShape.Capsule)
                    .padding(horizontal = 8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .height(44.dp)
                        .clickable(role = Role.Button) { onEdit(picture.uri) }
                        .padding(horizontal = 12.dp),
                ) {
                    Icon(imageVector = Symbols.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Text(text = stringResource(id = R.string.editor_edit_picture), color = Color.White, fontSize = 14.sp)
                }
                Box(modifier = Modifier.weight(1f)) {
                    if (state.multiple) {
                        PickedStrip(
                            state = state,
                            onOpen = { index ->
                                val uri = state.selected[index]
                                val page = pictures.indexOfFirst { it.uri == uri }
                                if (page >= 0) scope.launch { pagerState.animateScrollToPage(page) }
                            },
                            onMove = onMove,
                            current = picture.uri,
                            filled = Color.White,
                            onFilled = Color.Black,
                        )
                    }
                }
                val shownOne = listOf(state.edits[picture.uri] ?: picture.uri)
                Button(
                    onClick = {
                        // Says what it gives back: the picture shown, unless others are picked
                        onDone(if (state.multiple && state.selected.isNotEmpty()) state.result else shownOne)
                    },
                    contentPadding = PaddingValues(horizontal = 18.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color.White, contentColor = Color.Black),
                ) {
                    Text(
                        text = when {
                            !state.multiple -> stringResource(id = R.string.button_picker_use_this)
                            state.selected.isEmpty() -> stringResource(id = R.string.button_picker_add_this)
                            else -> stringResource(id = R.string.button_picker_add, state.selected.size)
                        },
                    )
                }
            }
        }
    }
}

/** Whether the picture shows whole and unzoomed, so a drag down may close it. */
private fun CoilZoomState?.atRest(): Boolean {
    val zoomable = this?.zoomable ?: return true
    if (zoomable.userTransform.scaleX > 1.01f) return false
    // A long picture scrolled down drags back up first
    return zoomable.scrollEdge.vertical.let { it == Edge.START || it == Edge.BOTH }
}
