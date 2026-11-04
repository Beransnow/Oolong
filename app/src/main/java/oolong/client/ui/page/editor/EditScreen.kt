package oolong.client.ui.page.editor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateValueAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.withMatrix
import oolong.client.R
import oolong.client.ui.common.theme.compose.ExtendedTheme
import oolong.client.ui.common.theme.compose.Motion
import oolong.client.ui.common.theme.compose.Symbols
import oolong.client.ui.widgets.compose.Button
import oolong.client.ui.widgets.compose.ConfirmDialog
import oolong.client.ui.widgets.compose.GlassScaffold
import oolong.client.ui.widgets.compose.GlassTopBar
import oolong.client.ui.widgets.compose.IconButton
import oolong.client.ui.widgets.compose.TextButton
import oolong.client.ui.widgets.compose.floatingGlass
import oolong.client.ui.widgets.compose.rememberDialogState
import kotlin.math.abs

/** The space kept between the picture and the bars. */
private val PictureMargin = 16.dp

/** How near an edge or corner of the frame a finger takes hold of it. */
private val FrameGrip = 24.dp

/** What the editor writes: the picture as framed, with its strokes and captions. */
class EditResult(val framing: Framing, val strokes: List<Edit>, val captions: List<Edit.Caption>)

/**
 * The editor: the picture between the bars, cancel, undo and redo, and done above, the tools
 * below; picking a tool opens its options from it.
 */
@Composable
fun EditScreen(
    renderer: EditRenderer,
    saving: Boolean,
    onCancel: () -> Unit,
    onDone: (EditResult) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val state = remember(renderer) { EditorState(renderer, scope) }
    state.density = density.density
    val discardDialog = rememberDialogState()
    val leave = {
        state.finishCaption()
        if (state.history.canUndo) discardDialog.show() else onCancel()
    }

    BackHandler {
        when {
            state.draft != null -> state.finishCaption()
            state.tool != null -> state.select(null)
            else -> leave()
        }
    }

    ConfirmDialog(
        dialogState = discardDialog,
        onConfirm = onCancel,
        confirmText = stringResource(id = R.string.editor_discard),
        cancelText = stringResource(id = R.string.editor_keep_editing),
    ) {
        Text(text = stringResource(id = R.string.editor_discard_title))
    }

    GlassScaffold(
        backgroundColor = Color.Black,
        topBar = {
            GlassTopBar(
                title = { UndoRedo(state) },
                centerTitle = true,
                navigationIcon = {
                    TextButton(onClick = leave, color = ExtendedTheme.colors.text) {
                        Text(text = stringResource(id = R.string.button_cancel))
                    }
                },
                actionsInCapsule = false,
                actions = {
                    Button(
                        onClick = {
                            state.finishCaption()
                            val history = state.history
                            onDone(EditResult(history.framing, history.strokes, history.captions))
                        },
                        enabled = !saving,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 12.dp),
                    ) {
                        Text(text = stringResource(id = R.string.button_finish))
                    }
                },
            )
        },
        bottomBar = { EditorBottom(state) },
    ) { padding ->
        Picture(state, padding)
    }
}

@Composable
private fun UndoRedo(state: EditorState) {
    Row(modifier = Modifier.floatingGlass(RoundedCornerShape(percent = 50), barButton = false)) {
        IconButton(onClick = state::undo, enabled = state.history.canUndo || state.draft != null) {
            Icon(imageVector = Symbols.Undo, contentDescription = stringResource(id = R.string.editor_undo))
        }
        IconButton(onClick = state::redo, enabled = state.history.canRedo) {
            Icon(imageVector = Symbols.Redo, contentDescription = stringResource(id = R.string.editor_redo))
        }
    }
}

/**
 * The picture in the room [padding] leaves, which grows and shrinks smoothly as the tools' options
 * open and close; and the caption being typed, lifted above the keyboard.
 */
@Composable
private fun Picture(state: EditorState, padding: PaddingValues) {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val margin = with(density) { PictureMargin.toPx() }
    val grip = with(density) { FrameGrip.toPx() }
    val imeBottom = WindowInsets.ime.getBottom(density)
    val top = with(density) { padding.calculateTopPadding().toPx() }
    val bottom = with(density) { padding.calculateBottomPadding().toPx() }
    val left = with(density) { padding.calculateLeftPadding(direction).toPx() }
    val right = with(density) { padding.calculateRightPadding(direction).toPx() }

    Box(modifier = Modifier.fillMaxSize()) {
        // The insets around the picture, eased as the options open and close
        val room by animateValueAsState(
            targetValue = Rect(left + margin, top + margin, right + margin, bottom + margin),
            typeConverter = Rect.VectorConverter,
            animationSpec = Motion.morph(),
            label = "room",
        )
        // Typing near the bottom, everything rises so the caption stays above the keyboard
        val lift by animateFloatAsState(
            targetValue = state.draft?.let { draft ->
                val below = state.toScreen(state.renderer.captionBounds(draft, state.history.framing).bottomCenter).y +
                    with(density) { 24.dp.toPx() } - (state.area.bottom + bottom + margin - imeBottom)
                below.coerceAtLeast(0f)
            } ?: 0f,
            animationSpec = Motion.settle(),
            label = "lift",
        )
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = -lift }
                .pointerInput(state) { gestures(state, grip) },
        ) {
            state.area = Rect(room.left, room.top, size.width - room.right, size.height - room.bottom)
            showHomeOnce(state)
            if (state.tool == EditorTool.Crop) {
                drawEdits(state)
                drawFrame(state)
            } else {
                // Only the kept part shows; what lies outside it is kept for later
                val kept = state.history.framing.rect
                val shown = Rect(state.toScreen(kept.topLeft), state.toScreen(kept.bottomRight))
                clipRect(shown.left, shown.top, shown.right, shown.bottom) { drawEdits(state) }
            }
        }
        state.draft?.let { draft ->
            CaptionField(state, draft, lift)
        }
    }
}

/** Shows the picture's home the first time there is room for it. */
private fun showHomeOnce(state: EditorState) {
    if (!state.laidOut && state.area.width > 0f) {
        state.laidOut = true
        state.goHome(animate = false)
    }
}

private fun DrawScope.drawEdits(state: EditorState) {
    val moving = state.moving
    val captions = state.history.captions.map { caption ->
        if (moving != null && moving.first == caption.id) state.movedCaption(caption, moving.second) else caption
    }
    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        native.withMatrix(state.screenMatrix()) {
            state.renderer.draw(native, state.framing, state.history.strokes, captions, state.live, state.draft?.id)
        }
    }
}

/** The frame being cut to: outside dimmed, its edges and corners, thirds while it is dragged. */
private fun DrawScope.drawFrame(state: EditorState) {
    val rect = state.framing.rect
    val frame = Rect(state.toScreen(rect.topLeft), state.toScreen(rect.bottomRight))
    val outside = Path().apply {
        fillType = PathFillType.EvenOdd
        addRect(Rect(Offset.Zero, size))
        addRect(frame)
    }
    drawPath(outside, Color.Black.copy(alpha = 0.6f))
    drawRect(Color.White, frame.topLeft, frame.size, style = Stroke(1.dp.toPx()))
    if (state.draftRect != null) {
        val line = Color.White.copy(alpha = 0.5f)
        for (i in 1..2) {
            val x = frame.left + frame.width * i / 3
            val y = frame.top + frame.height * i / 3
            drawLine(line, Offset(x, frame.top), Offset(x, frame.bottom), 0.5.dp.toPx())
            drawLine(line, Offset(frame.left, y), Offset(frame.right, y), 0.5.dp.toPx())
        }
    }
    val arm = 16.dp.toPx()
    val width = 3.dp.toPx()
    val corners = Path()
    for ((corner, sx, sy) in listOf(
        Triple(frame.topLeft, 1f, 1f),
        Triple(frame.topRight, -1f, 1f),
        Triple(frame.bottomLeft, 1f, -1f),
        Triple(frame.bottomRight, -1f, -1f),
    )) {
        val c = corner + Offset(-sx * width / 2, -sy * width / 2)
        corners.moveTo(c.x + sx * arm, c.y)
        corners.lineTo(c.x, c.y)
        corners.lineTo(c.x, c.y + sy * arm)
    }
    drawPath(corners, Color.White, style = Stroke(width, join = StrokeJoin.Round))
}

/** The caption being typed, where it will lie, in its look. */
@Composable
private fun CaptionField(state: EditorState, draft: Edit.Caption, lift: Float) {
    val density = LocalDensity.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(draft.id) { focus.requestFocus() }
    val center = state.toScreen(state.turnedCenter(draft))
    val px = draft.size * state.scale
    val style = TextStyle(
        color = draft.textColor(),
        fontSize = with(density) { px.toSp() },
        lineHeight = with(density) { (px * 1.17f).toSp() },
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    val padX = with(density) { (px * 0.35f).toDp() }
    val padY = with(density) { (px * 0.18f).toDp() }
    Box(
        modifier = Modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(
                        (center.x - placeable.width / 2f).toInt(),
                        (center.y - lift - placeable.height / 2f).toInt(),
                    )
                }
            }
            .then(
                if (draft.style == CaptionStyle.Filled) {
                    Modifier.background(draft.color, RoundedCornerShape(with(density) { (px * 0.3f).toDp() }))
                } else {
                    Modifier
                }
            )
            .padding(horizontal = padX, vertical = padY),
    ) {
        if (draft.style == CaptionStyle.Outline) {
            Text(
                text = state.draftText.text,
                style = style.copy(
                    color = draft.outlineColor(),
                    drawStyle = Stroke(width = px * 0.16f, join = StrokeJoin.Round),
                ),
            )
        }
        BasicTextField(
            value = state.draftText,
            onValueChange = { state.draftText = it },
            textStyle = style,
            cursorBrush = SolidColor(draft.textColor()),
            modifier = Modifier
                .focusRequester(focus)
                .width(IntrinsicSize.Min)
                .widthIn(min = 2.dp),
        )
    }
}

/** What a finger does on the picture, by the tool in hand. */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.gestures(state: EditorState, grip: Float) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        // A tap away from the caption being typed ends it
        if (state.draft != null) {
            state.finishCaption()
            transform(state, down)
            return@awaitEachGesture
        }
        when (state.tool) {
            EditorTool.Crop -> {
                val hold = frameHold(state, down.position, grip)
                if (hold == null) transform(state, down) else dragFrame(state, down, hold)
            }

            EditorTool.Mask, EditorTool.Mark -> {
                // Strokes start on the picture; elsewhere a finger moves the view
                val kept = state.history.framing.rect
                val shown = Rect(state.toScreen(kept.topLeft), state.toScreen(kept.bottomRight))
                if (shown.contains(down.position)) stroke(state, down) else transform(state, down)
            }
            EditorTool.Text -> caption(state, down, viewConfiguration.touchSlop)
            null -> transform(state, down)
        }
    }
}

/** Which edge or corner of the frame is under [point], or the inside: -1, 0 or 1 across and down. */
private fun frameHold(state: EditorState, point: Offset, grip: Float): Pair<Int, Int>? {
    val rect = state.framing.rect
    val frame = Rect(state.toScreen(rect.topLeft), state.toScreen(rect.bottomRight))
    if (!frame.inflate(grip).contains(point)) return null
    var dx = when {
        abs(point.x - frame.left) < grip -> -1
        abs(point.x - frame.right) < grip -> 1
        else -> 0
    }
    var dy = when {
        abs(point.y - frame.top) < grip -> -1
        abs(point.y - frame.bottom) < grip -> 1
        else -> 0
    }
    // A fixed shape is changed by its corners only; an edge moves the frame
    if (state.aspect() != null && (dx == 0 || dy == 0)) {
        dx = 0
        dy = 0
    }
    return dx to dy
}

private suspend fun AwaitPointerEventScope.dragFrame(state: EditorState, down: PointerInputChange, hold: Pair<Int, Int>) {
    val start = state.framing.rect
    val least = state.leastFrame()
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == down.id } ?: break
        if (!change.pressed) break
        if (change.positionChanged()) {
            state.draftRect = state.dragFrame(start, hold.first, hold.second, (change.position - down.position) / state.scale, least)
            change.consume()
        }
    }
    state.commitFrame()
}

private suspend fun AwaitPointerEventScope.stroke(state: EditorState, down: PointerInputChange) {
    state.beginStroke(down.position)
    while (true) {
        val event = awaitPointerEvent()
        // A second finger zooms instead
        if (event.changes.count { it.pressed } > 1) {
            state.live = null
            transform(state, null)
            return
        }
        val change = event.changes.firstOrNull { it.id == down.id } ?: break
        if (!change.pressed) break
        if (change.positionChanged()) {
            state.extendStroke(change.position)
            change.consume()
        }
    }
    state.endStroke()
}

private suspend fun AwaitPointerEventScope.caption(state: EditorState, down: PointerInputChange, slop: Float) {
    val caption = state.captionAt(down.position)
    var dragged = false
    while (true) {
        val event = awaitPointerEvent()
        if (event.changes.count { it.pressed } > 1) {
            state.moving = null
            transform(state, null)
            return
        }
        val change = event.changes.firstOrNull { it.id == down.id } ?: break
        if (!change.pressed) break
        val by = change.position - down.position
        if (caption != null && (dragged || by.getDistance() > slop)) {
            dragged = true
            state.moving = caption.id to by
            change.consume()
        }
    }
    val moving = state.moving
    state.moving = null
    when {
        caption != null && dragged && moving != null -> state.history.push(state.movedCaption(caption, moving.second))
        caption != null -> state.editCaption(caption)
        else -> state.editCaption(screen = down.position)
    }
}

/** Zooms and moves the view with the fingers until they all lift. */
private suspend fun AwaitPointerEventScope.transform(state: EditorState, down: PointerInputChange?) {
    if (down != null && !down.pressed) return
    while (true) {
        val event = awaitPointerEvent()
        if (event.changes.none { it.pressed }) break
        val zoom = event.calculateZoom()
        val pan = event.calculatePan()
        if (zoom != 1f || pan != Offset.Zero) {
            state.transform(event.calculateCentroid(useCurrent = false), pan, zoom)
            event.changes.forEach { if (it.positionChanged()) it.consume() }
        }
    }
    state.settle()
}

@Composable
private fun EditorBottom(state: EditorState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 12.dp),
        contentAlignment = Alignment.BottomCenter,
    ) { EditorTools(state) }
}
