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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
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
import androidx.compose.ui.layout.onSizeChanged
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
import oolong.client.ui.common.theme.compose.LocalExtendedColors
import oolong.client.ui.common.theme.compose.Motion
import oolong.client.ui.common.theme.compose.Symbols
import oolong.client.ui.widgets.compose.Button
import oolong.client.ui.widgets.compose.ConfirmDialog
import oolong.client.ui.widgets.compose.GlassScaffold
import oolong.client.ui.widgets.compose.GlassTopBar
import oolong.client.ui.widgets.compose.IconButton
import oolong.client.ui.widgets.compose.TextButton
import oolong.client.ui.widgets.compose.floatingGlass
import oolong.client.ui.widgets.compose.pageScroll
import oolong.client.ui.widgets.compose.rememberDialogState
import oolong.client.utils.ThemeUtil
import kotlin.math.abs

/** The space kept between the picture and the bars. */
private val PictureMargin = 16.dp

/** The tone the editor's glass takes on the black page. */
private val ChromeTone = Color(0xFF3A3B40)

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

    // Glass on black takes its tone from the page and would be black itself: here it is lifted
    // to a grey of its own, so the bars stand off the black by tone
    val colors = ExtendedTheme.colors
    val chrome = remember(colors) {
        colors.copy(
            theme = if (ThemeUtil.isTranslucentTheme(colors.theme)) ThemeUtil.THEME_BLUE_DARK else colors.theme,
            background = ChromeTone,
        )
    }
    CompositionLocalProvider(LocalExtendedColors provides chrome) {
        BoxWithConstraints {
            val layout = when {
                maxWidth > maxHeight && maxHeight < 600.dp -> EditorLayout.Landscape
                maxWidth >= 600.dp -> EditorLayout.Side
                else -> EditorLayout.Bottom
            }
            Scaffold(state, saving, layout, leave, onDone)
        }
    }
}

/** Where the editor's controls go. */
private enum class EditorLayout {
    /** A phone upright: the bar above, the tools along the bottom. */
    Bottom,

    /** Wide, as a tablet: the bar above, the tools down the end. */
    Side,

    /** A phone on its side, short of height: no bar; cancel and undo down the start, done and the tools down the end. */
    Landscape,
}

@Composable
private fun Scaffold(
    state: EditorState,
    saving: Boolean,
    layout: EditorLayout,
    leave: () -> Unit,
    onDone: (EditResult) -> Unit,
) {
    val density = LocalDensity.current
    var railWidth by remember { mutableIntStateOf(0) }
    var startWidth by remember { mutableIntStateOf(0) }
    val done: () -> Unit = {
        state.finishCaption()
        val history = state.history
        onDone(EditResult(history.framing, history.strokes, history.captions))
    }
    GlassScaffold(
        backgroundColor = Color.Black,
        topBar = {
            if (layout != EditorLayout.Landscape) {
                GlassTopBar(
                    title = { UndoRedo(state) },
                    centerTitle = true,
                    navigationIcon = { CancelButton(leave) },
                    actionsInCapsule = false,
                    actions = { DoneButton(saving, done, Modifier.padding(end = 12.dp)) },
                )
            }
        },
        bottomBar = { if (layout == EditorLayout.Bottom) EditorBottom(state) },
        overlay = {
            if (layout != EditorLayout.Bottom) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(horizontal = 16.dp)
                        .padding(top = if (layout == EditorLayout.Side) TopBarRoom else 8.dp, bottom = 8.dp),
                ) {
                    EditorTools(state, side = true, onRail = { railWidth = it })
                    if (layout == EditorLayout.Landscape) {
                        CancelButton(leave, Modifier.align(Alignment.TopStart))
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .onSizeChanged { startWidth = it.width },
                        ) { UndoRedo(state, vertical = true) }
                        DoneButton(saving, done, Modifier.align(Alignment.TopEnd))
                    }
                }
            }
        },
    ) { padding ->
        val direction = LocalLayoutDirection.current
        val safe = WindowInsets.safeDrawing.asPaddingValues()
        val insets = when (layout) {
            EditorLayout.Bottom -> padding
            EditorLayout.Side -> PaddingValues(
                start = padding.calculateStartPadding(direction),
                top = padding.calculateTopPadding(),
                end = padding.calculateEndPadding(direction) + with(density) { railWidth.toDp() } + 16.dp,
                bottom = padding.calculateBottomPadding(),
            )
            // The picture takes the whole height between the two sides
            EditorLayout.Landscape -> PaddingValues(
                start = safe.calculateStartPadding(direction) + 16.dp + with(density) { startWidth.toDp() },
                top = safe.calculateTopPadding(),
                end = safe.calculateEndPadding(direction) + 16.dp + with(density) { railWidth.toDp() },
                bottom = safe.calculateBottomPadding(),
            )
        }
        Picture(state, insets)
    }
}

/** Room above the side tools for the bar. */
private val TopBarRoom = 64.dp

@Composable
private fun CancelButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, color = ExtendedTheme.colors.text, modifier = modifier) {
        Text(text = stringResource(id = R.string.button_cancel))
    }
}

@Composable
private fun DoneButton(saving: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        enabled = !saving,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        modifier = modifier,
    ) {
        Text(text = stringResource(id = R.string.button_finish))
    }
}

@Composable
private fun UndoRedo(state: EditorState, vertical: Boolean = false) {
    val buttons: @Composable () -> Unit = {
        IconButton(onClick = state::undo, enabled = state.history.canUndo || state.draft != null) {
            Icon(imageVector = Symbols.Undo, contentDescription = stringResource(id = R.string.editor_undo))
        }
        IconButton(onClick = state::redo, enabled = state.history.canRedo) {
            Icon(imageVector = Symbols.Redo, contentDescription = stringResource(id = R.string.editor_redo))
        }
    }
    val glass = Modifier.floatingGlass(RoundedCornerShape(percent = 50), barButton = false)
    if (vertical) Column(modifier = glass) { buttons() } else Row(modifier = glass) { buttons() }
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
        // The insets around the picture; the options' room already moves with their own motion
        val room = Rect(left + margin, top + margin, right + margin, bottom + margin)
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
                // Nothing scrolls under the bar: it stays clear over the black
                .pageScroll(null) { 0 }
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
                drawSelection(state)
            }
        }
        state.draft?.let { draft ->
            CaptionField(state, draft, lift)
        }
        // What is picked up can be taken away, from a button just above it
        val selection = state.selection
        if (selection != null && state.dragged == null && state.tool != EditorTool.Crop) {
            DeleteButton(state, selection)
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
            state.renderer.draw(native, state.framing, state.shownStrokes, captions, state.live, state.draft?.id)
        }
    }
}

/** Round what is picked up, a dashed outline, and a handle on each end that moves alone. */
private fun DrawScope.drawSelection(state: EditorState) {
    val edit = state.selection ?: return
    val bounds = state.screenBounds(edit).inflate(6.dp.toPx())
    val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
    drawRect(Color.Black.copy(alpha = 0.4f), bounds.topLeft, bounds.size, style = Stroke(2.5.dp.toPx(), pathEffect = dash))
    drawRect(Color.White, bounds.topLeft, bounds.size, style = Stroke(1.dp.toPx(), pathEffect = dash))
    for (handle in state.handles(edit)) {
        val at = state.pictureToScreen(handle)
        drawCircle(Color.Black.copy(alpha = 0.3f), 8.dp.toPx(), at)
        drawCircle(Color.White, 6.5.dp.toPx(), at)
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
                if (shown.contains(down.position)) drawOrPick(state, down, viewConfiguration.touchSlop, grip) else transform(state, down)
            }
            EditorTool.Text -> caption(state, down, viewConfiguration.touchSlop)
            null -> tapOrTransform(state, down, viewConfiguration.touchSlop)
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

/**
 * With markup or redaction in hand: on what is picked up, a finger moves it, or one end of it by
 * its handle; a tap on something drawn picks it up, a tap elsewhere puts it down; a drag draws.
 */
private suspend fun AwaitPointerEventScope.drawOrPick(state: EditorState, down: PointerInputChange, slop: Float, grip: Float) {
    val selection = state.selection
    if (selection != null) {
        val handle = state.handles(selection).indexOfFirst { (state.pictureToScreen(it) - down.position).getDistance() < grip }
        if (handle >= 0 || state.objectAt(down.position)?.objectId == selection.objectId) {
            move(state, down, selection, handle.takeIf { it >= 0 })
            return
        }
    }
    val hit = state.objectAt(down.position)
    // Until the finger moves, it may be a tap
    val path = mutableListOf(down.position)
    var drawing = false
    while (!drawing) {
        val event = awaitPointerEvent()
        if (event.changes.count { it.pressed } > 1) {
            transform(state, null)
            return
        }
        val change = event.changes.firstOrNull { it.id == down.id } ?: return
        if (!change.pressed) break
        path += change.position
        if ((change.position - down.position).getDistance() > slop) drawing = true
    }
    if (!drawing) {
        when {
            hit != null -> state.pick(hit)
            selection != null -> state.deselect()
            // A tap draws a dot, or puts a numbered one
            else -> {
                state.beginStroke(down.position)
                state.endStroke()
            }
        }
        return
    }
    state.deselect()
    state.beginStroke(down.position)
    path.drop(1).forEach(state::extendStroke)
    stroke(state, down)
}

/** Moves [edit], or one end of it, with the finger, and makes it a step once lifted. */
private suspend fun AwaitPointerEventScope.move(state: EditorState, down: PointerInputChange, edit: Edit, handle: Int?) {
    val from = state.toPicture(state.toTurned(down.position))
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == down.id } ?: break
        if (!change.pressed) break
        if (change.positionChanged()) {
            state.dragged = state.moved(edit, state.toPicture(state.toTurned(change.position)) - from, handle)
            change.consume()
        }
    }
    state.commitDrag()
}

/** With no tool in hand: a tap on something drawn or written picks it up with its tool; else the view moves. */
private suspend fun AwaitPointerEventScope.tapOrTransform(state: EditorState, down: PointerInputChange, slop: Float) {
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == down.id }
        if (event.changes.count { it.pressed } > 1 || change == null || (change.pressed && (change.position - down.position).getDistance() > slop)) {
            transform(state, null)
            return
        }
        if (!change.pressed) break
    }
    val caption = state.captionAt(down.position)
    val hit = state.objectAt(down.position)
    when {
        caption != null -> {
            state.select(EditorTool.Text)
            state.editCaption(caption)
        }
        hit != null -> state.pick(hit)
    }
}

/** Draws the stroke begun, following the finger until it lifts. */
private suspend fun AwaitPointerEventScope.stroke(state: EditorState, down: PointerInputChange) {
    if (state.live == null) state.beginStroke(down.position)
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

@Composable
private fun DeleteButton(state: EditorState, selection: Edit) {
    val density = LocalDensity.current
    val bounds = state.screenBounds(selection)
    Box(
        modifier = Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
            layout(constraints.maxWidth, constraints.maxHeight) {
                val gap = with(density) { 14.dp.roundToPx() }
                // Above, or below when there is no room above
                val above = bounds.top - gap - placeable.height
                val y = if (above >= state.area.top) above else bounds.bottom + gap
                placeable.place((bounds.center.x - placeable.width / 2f).toInt(), y.toInt())
            }
        },
    ) {
        Box(modifier = Modifier.floatingGlass(RoundedCornerShape(percent = 50), barButton = false)) {
            IconButton(onClick = state::deleteSelection) {
                Icon(imageVector = Symbols.Delete, contentDescription = stringResource(id = R.string.editor_delete), tint = ExtendedTheme.colors.text)
            }
        }
    }
}
