package oolong.client.ui.page.editor

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import oolong.client.R
import oolong.client.ui.common.theme.compose.ExtendedTheme
import oolong.client.ui.common.theme.compose.Motion
import oolong.client.ui.common.theme.compose.Symbols
import oolong.client.ui.widgets.compose.IconButton
import oolong.client.ui.widgets.compose.Refocus
import oolong.client.ui.widgets.compose.condense
import oolong.client.ui.widgets.compose.floatingGlass

/** The selected tool's bubble, as on the bottom bar. */
private val BubbleColor = Color.White.copy(alpha = 0.16f)

/** The track of a choice on the options' glass. */
private val TrackColor = Color.White.copy(alpha = 0.08f)

private val ToolWidth = 68.dp
private val ToolHeight = 52.dp

/** How wide a tool's options are beside a side rail. */
private val SidePanelWidth = 340.dp

/**
 * The tools on one piece of glass, along the bottom or, when [side], down the end of the space it
 * fills; the chosen tool's options grow out of its button, above it or level with it beside it,
 * and go back into it. [onRail] hears how wide the side rail is, for the picture to keep clear of.
 */
@Composable
fun EditorTools(state: EditorState, side: Boolean = false, onRail: (Int) -> Unit = {}) {
    val tools = remember { mutableStateMapOf<EditorTool, Rect>() }
    var panelBounds by remember { mutableStateOf(Rect.Zero) }
    // The options shown: the tool's while open, the last one's while closing
    var shown by remember { mutableStateOf<EditorTool?>(null) }
    val progress = remember { Animatable(0f, Motion.ProgressThreshold) }
    LaunchedEffect(state.tool) {
        val tool = state.tool
        if (tool != null) {
            val opening = shown == null
            shown = tool
            if (opening) progress.animateTo(1f, Motion.morph())
        } else if (shown != null) {
            progress.animateTo(0f, Motion.dismiss())
            shown = null
        }
    }

    val panel: @Composable () -> Unit = {
        shown?.let { tool ->
            Box(
                modifier = Modifier
                    .then(
                        if (side) {
                            Modifier
                                .padding(end = 10.dp)
                                .width(SidePanelWidth)
                        } else {
                            Modifier
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 10.dp)
                                .widthIn(max = 420.dp)
                                .fillMaxWidth()
                        }
                    )
                    .onGloballyPositioned { panelBounds = it.boundsInRoot() }
                    .graphicsLayer {
                        // Grown out of the tool's button
                        val p = progress.value
                        val from = tools[tool] ?: return@graphicsLayer
                        val panel = panelBounds
                        if (panel.width <= 0f) return@graphicsLayer
                        transformOrigin = if (side) {
                            TransformOrigin(1f, ((from.center.y - panel.top) / panel.height).coerceIn(0f, 1f))
                        } else {
                            TransformOrigin(((from.center.x - panel.left) / panel.width).coerceIn(0f, 1f), 1f)
                        }
                        scaleX = lerp(from.width / panel.width, 1f, p)
                        scaleY = lerp(from.height / panel.height, 1f, p)
                        if (side) translationX = lerp(from.right - panel.right, 0f, p) else translationY = lerp(from.bottom - panel.bottom, 0f, p)
                        alpha = (p * 3f).coerceIn(0f, 1f)
                    }
                    .floatingGlass(RoundedCornerShape(26.dp), barButton = false)
                    .animateContentSize(Motion.morph())
                    .padding(10.dp),
            ) {
                Refocus(
                    targetState = tool,
                    modifier = Modifier.condense(progress = { progress.value }, blur = 10.dp),
                ) { ToolOptions(state, it) }
            }
        }
    }
    val rail: @Composable () -> Unit = {
        Box(modifier = Modifier.floatingGlass(RoundedCornerShape(percent = 50), barButton = false)) {
            // The bubble slides to the chosen tool
            val selected = state.tool?.let { tools[it] }
            val first = tools[EditorTool.Crop]
            val shift by animateFloatAsState(
                targetValue = when {
                    selected == null || first == null -> 0f
                    side -> selected.top - first.top
                    else -> selected.left - first.left
                },
                animationSpec = Motion.snap(),
                label = "bubble",
            )
            val bubble by animateFloatAsState(if (selected != null) 1f else 0f, Motion.snap(), label = "bubbleIn")
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .graphicsLayer {
                        if (side) translationY = shift else translationX = shift
                        alpha = bubble
                    }
                    .size(ToolWidth, ToolHeight)
                    .background(BubbleColor, RoundedCornerShape(percent = 50)),
            )
            val buttons: @Composable () -> Unit = {
                for (tool in EditorTool.entries) {
                    ToolButton(
                        tool = tool,
                        selected = state.tool == tool,
                        onClick = { state.select(tool) },
                        modifier = Modifier.onGloballyPositioned { tools[tool] = it.boundsInRoot() },
                    )
                }
            }
            if (side) Column(modifier = Modifier.padding(4.dp)) { buttons() } else Row(modifier = Modifier.padding(4.dp)) { buttons() }
        }
    }
    if (side) {
        var railTop by remember { mutableFloatStateOf(0f) }
        var railHeight by remember { mutableIntStateOf(0) }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
            Row(verticalAlignment = Alignment.Top) {
                // Level with the chosen tool, kept within the rail's height
                Box(
                    modifier = Modifier.offset {
                        val from = shown?.let { tools[it] } ?: return@offset IntOffset.Zero
                        val height = panelBounds.height
                        val room = railHeight - height
                        val y = (from.center.y - railTop - height / 2).coerceIn(minOf(0f, room), maxOf(0f, room))
                        IntOffset(0, y.toInt())
                    },
                ) { panel() }
                Box(
                    modifier = Modifier.onGloballyPositioned {
                        railTop = it.boundsInRoot().top
                        railHeight = it.size.height
                        onRail(it.size.width)
                    },
                ) { rail() }
            }
        }
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            panel()
            rail()
        }
    }
}

private val EditorTool.label: Int
    get() = when (this) {
        EditorTool.Crop -> R.string.editor_crop
        EditorTool.Mask -> R.string.editor_mask
        EditorTool.Mark -> R.string.editor_mark
        EditorTool.Text -> R.string.editor_text
    }

private fun EditorTool.icon(selected: Boolean): ImageVector = when (this) {
    EditorTool.Crop -> if (selected) Symbols.CropFill else Symbols.Crop
    EditorTool.Mask -> if (selected) Symbols.BlurOnFill else Symbols.BlurOn
    EditorTool.Mark -> if (selected) Symbols.DrawFill else Symbols.Draw
    EditorTool.Text -> if (selected) Symbols.TextFieldsFill else Symbols.TextFields
}

@Composable
private fun ToolButton(tool: EditorTool, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ExtendedTheme.colors
    val color = if (selected) colors.text else colors.textSecondary
    Column(
        modifier = modifier
            .size(ToolWidth, ToolHeight)
            .clip(RoundedCornerShape(percent = 50))
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(imageVector = tool.icon(selected), contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Text(text = stringResource(id = tool.label), color = color, style = MaterialTheme.typography.caption)
    }
}

/** A tool's options: what it draws on the first row, how on the second. */
@Composable
private fun ToolOptions(state: EditorState, tool: EditorTool) {
    // Picking a colour of one's own takes the colour row's place until done
    var picking by remember(tool) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        when (tool) {
            EditorTool.Crop -> CompositionLocalProvider(LocalContentColor provides ExtendedTheme.colors.text) {
                val portrait = state.history.framing.turned(state.renderer.size).let { it.height > it.width }
                OptionRow {
                    Options(
                        options = CropRatio.entries,
                        selected = state.ratio,
                        onSelect = state::pickRatio,
                        label = { ratioLabel(it, portrait) },
                        width = { if (it == CropRatio.Free || it == CropRatio.Original) 52.dp else 40.dp },
                    ) { ratio, _ -> RatioShape(ratio, portrait) }
                }
                OptionRow {
                    IconButton(onClick = state::rotate) {
                        Icon(imageVector = Symbols.RotateLeft, contentDescription = stringResource(id = R.string.button_rotate), tint = LocalContentColor.current)
                    }
                    IconButton(onClick = state::flip) {
                        Icon(imageVector = Symbols.Flip, contentDescription = stringResource(id = R.string.editor_flip), tint = LocalContentColor.current)
                    }
                    IconButton(onClick = state::restore, enabled = state.history.framing != state.whole) {
                        Icon(imageVector = Symbols.RestartAlt, contentDescription = stringResource(id = R.string.button_restore), tint = LocalContentColor.current)
                    }
                }
            }

            EditorTool.Mask -> {
                OptionRow(Arrangement.SpaceBetween) {
                    Options(
                        options = MaskShape.entries,
                        selected = state.maskShape,
                        onSelect = { state.maskShape = it },
                        label = { stringResource(id = if (it == MaskShape.Brush) R.string.editor_brush else R.string.editor_rect) },
                    ) { shape, on ->
                        Icon(
                            imageVector = if (shape == MaskShape.Brush) Symbols.Gesture else Symbols.Select,
                            contentDescription = null,
                            tint = if (on) ExtendedTheme.colors.text else ExtendedTheme.colors.textSecondary,
                        )
                    }
                    // A box needs no breadth
                    if (state.maskShape == MaskShape.Brush) Sizes(selected = state.maskSize, onSelect = { state.maskSize = it })
                }
                OptionRow {
                    val density = LocalDensity.current
                    val previews = remember(state.renderer) {
                        val (w, h) = with(density) { 40.dp.roundToPx() to 28.dp.roundToPx() }
                        MaskStyle.entries.associateWith { state.renderer.stylePreview(it, w, h).asImageBitmap() }
                    }
                    Options(
                        options = MaskStyle.entries,
                        selected = state.maskStyle,
                        onSelect = { state.maskStyle = it },
                        label = { stringResource(id = it.label) },
                        width = { 52.dp },
                    ) { style, on ->
                        Image(
                            bitmap = previews.getValue(style),
                            contentDescription = null,
                            modifier = Modifier
                                .size(40.dp, 28.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .border(if (on) 1.5.dp else 1.dp, Color.White.copy(alpha = if (on) 1f else 0.2f), RoundedCornerShape(7.dp)),
                        )
                    }
                }
            }

            EditorTool.Mark -> {
                OptionRow {
                    Options(
                        options = MarkKind.entries,
                        selected = state.markKind,
                        onSelect = { state.markKind = it },
                        label = { stringResource(id = it.label) },
                    ) { kind, _ -> MarkPreview(kind, state.markColor) }
                }
                if (picking) {
                    HuePicker(color = state.markColor, onPick = { state.markColor = it }, onDone = { picking = false })
                } else {
                    ColorRow(
                        selected = state.markColor,
                        onSelect = { state.markColor = it },
                        onCustom = { picking = true },
                        step = state.markSize,
                        onStep = { state.markSize = it },
                    )
                }
            }

            EditorTool.Text -> {
                OptionRow {
                    Options(
                        options = CaptionStyle.entries,
                        selected = state.captionStyle,
                        onSelect = {
                            state.captionStyle = it
                            state.styleCaption()
                        },
                        label = { stringResource(id = it.label) },
                    ) { style, _ -> CaptionPreview(style) }
                }
                val pick: (Color) -> Unit = {
                    state.captionColor = it
                    state.styleCaption()
                }
                if (picking) {
                    HuePicker(color = state.captionColor, onPick = pick, onDone = { picking = false })
                } else {
                    ColorRow(
                        selected = state.captionColor,
                        onSelect = pick,
                        onCustom = { picking = true },
                        step = state.captionSize,
                        onStep = {
                            state.captionSize = it
                            state.styleCaption()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionRow(arrangement: Arrangement.Horizontal = Arrangement.Center, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp),
        horizontalArrangement = arrangement,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** One choice among [options] on a shared track, the chosen one in a bubble; each drawn by [content]. */
@Composable
private fun <T> Options(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    width: (T) -> Dp = { 40.dp },
    content: @Composable (T, Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .background(TrackColor, CircleShape)
            .padding(2.dp),
    ) {
        for (option in options) {
            val on = option == selected
            val description = label(option)
            Box(
                modifier = Modifier
                    .size(width(option), 36.dp)
                    .clip(CircleShape)
                    .background(if (on) BubbleColor else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onSelect(option) }
                    .semantics {
                        contentDescription = description
                        this.selected = on
                    },
                contentAlignment = Alignment.Center,
            ) { content(option, on) }
        }
    }
}

@Composable
private fun ratioLabel(ratio: CropRatio, portrait: Boolean): String = when (ratio) {
    CropRatio.Free -> stringResource(id = R.string.editor_ratio_free)
    CropRatio.Original -> stringResource(id = R.string.editor_ratio_original)
    CropRatio.Square -> "1:1"
    CropRatio.Classic -> if (portrait) "3:4" else "4:3"
    CropRatio.Wide -> if (portrait) "9:16" else "16:9"
}

/** A fixed ratio as its shape with the numbers in it; free and original, which have no shape, as words. */
@Composable
private fun RatioShape(ratio: CropRatio, portrait: Boolean) {
    val colors = ExtendedTheme.colors
    val label = ratioLabel(ratio, portrait)
    if (ratio == CropRatio.Free || ratio == CropRatio.Original) {
        Text(text = label, color = colors.text, style = MaterialTheme.typography.body2)
        return
    }
    val (long, short) = when (ratio) {
        CropRatio.Square -> 17.dp to 17.dp
        CropRatio.Classic -> 21.dp to 16.dp
        else -> 23.dp to 13.dp
    }
    Box(
        modifier = Modifier
            .size(if (portrait) short else long, if (portrait) long else short)
            .border(1.5.dp, colors.text, RoundedCornerShape(3.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = colors.text, fontSize = if (label.length > 3) 6.sp else 7.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** What a kind of mark draws, drawn small in [color]. */
@Composable
private fun MarkPreview(kind: MarkKind, color: Color) {
    val on = if (color.luma() > 0.6f) Color(0xFF16181C) else Color.White
    val measurer = rememberTextMeasurer()
    Canvas(modifier = Modifier.size(24.dp)) {
        val w = 2.2.dp.toPx()
        val stroke = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val s = size.width / 24f
        when (kind) {
            MarkKind.Pen -> drawPath(Path().apply {
                moveTo(3 * s, 16 * s)
                cubicTo(6 * s, 10 * s, 8 * s, 19 * s, 11 * s, 13 * s)
                cubicTo(14 * s, 7 * s, 16 * s, 15 * s, 20 * s, 9 * s)
            }, color, style = stroke)
            MarkKind.Highlighter -> drawLine(color.copy(alpha = 0.45f), Offset(3 * s, 12 * s), Offset(21 * s, 12 * s), 8 * s, StrokeCap.Butt)
            MarkKind.Line -> drawLine(color, Offset(4 * s, 19 * s), Offset(20 * s, 5 * s), w, StrokeCap.Round)
            MarkKind.Arrow -> drawPath(Path().apply {
                moveTo(4 * s, 20 * s)
                lineTo(19 * s, 5 * s)
                moveTo(11 * s, 5 * s)
                lineTo(19 * s, 5 * s)
                lineTo(19 * s, 13 * s)
            }, color, style = stroke)
            MarkKind.Box -> drawRoundRect(color, Offset(3.5f * s, 5.5f * s), Size(17 * s, 13 * s), CornerRadius(2.5f * s), style = stroke)
            MarkKind.Ellipse -> drawOval(color, Offset(3.5f * s, 5.5f * s), Size(17 * s, 13 * s), style = stroke)
            MarkKind.Number -> {
                drawCircle(color, 8 * s)
                val text = measurer.measure("1", TextStyle(color = on, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold))
                drawText(text, topLeft = center - Offset(text.size.width / 2f, text.size.height / 2f))
            }
        }
    }
}

/** A caption style as an 「A」 in it. */
@Composable
private fun CaptionPreview(style: CaptionStyle) {
    val letter = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
    when (style) {
        CaptionStyle.Outline -> Box {
            Text(text = "A", style = letter.copy(color = Color.Black, drawStyle = Stroke(width = 5f, join = StrokeJoin.Round)))
            Text(text = "A", style = letter.copy(color = Color.White))
        }
        CaptionStyle.Filled -> Box(
            modifier = Modifier
                .size(22.dp, 20.dp)
                .background(Color.White, RoundedCornerShape(5.dp)),
            contentAlignment = Alignment.Center,
        ) { Text(text = "A", style = letter.copy(color = Color(0xFF202124), fontSize = 14.sp)) }
        CaptionStyle.Plain -> Text(text = "A", style = letter.copy(color = ExtendedTheme.colors.text))
    }
}

/**
 * The colours, scrolling sideways and fading out at an end with more past it, then one's own
 * colour; the three sizes stay put at the end.
 */
@Composable
private fun ColorRow(selected: Color, onSelect: (Color) -> Unit, onCustom: () -> Unit, step: Int, onStep: (Int) -> Unit) {
    val labels = listOf(
        R.string.editor_color_red, R.string.editor_color_orange, R.string.editor_color_yellow, R.string.editor_color_green,
        R.string.editor_color_blue, R.string.editor_color_purple, R.string.editor_color_white, R.string.editor_color_black,
        R.string.editor_color_pink, R.string.editor_color_cyan, R.string.editor_color_navy, R.string.editor_color_brown,
        R.string.editor_color_grey,
    )
    val scroll = rememberScrollState()
    OptionRow {
        Row(
            modifier = Modifier
                .weight(1f)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val fade = 28.dp.toPx() / size.width
                    val start = if (scroll.canScrollBackward) 0f else 1f
                    val end = if (scroll.canScrollForward) 0f else 1f
                    drawRect(
                        Brush.horizontalGradient(
                            0f to Color.Black.copy(alpha = start),
                            fade to Color.Black,
                            1f - fade to Color.Black,
                            1f to Color.Black.copy(alpha = end),
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
                .horizontalScroll(scroll)
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EditorColors.forEachIndexed { i, color ->
                Swatch(color = color, chosen = color == selected, label = stringResource(id = labels[i])) { onSelect(color) }
            }
            val own = selected !in EditorColors
            Swatch(color = if (own) selected else null, chosen = own, label = stringResource(id = R.string.editor_color_custom), onClick = onCustom)
        }
        Box(
            modifier = Modifier
                .padding(horizontal = 6.dp)
                .size(1.dp, 22.dp)
                .background(Color.White.copy(alpha = 0.14f)),
        )
        Sizes(selected = step, onSelect = onStep)
    }
}

/** A colour to pick; [color] null draws the wheel that stands for picking one's own. */
@Composable
private fun Swatch(color: Color?, chosen: Boolean, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = label
                selected = chosen
            },
        contentAlignment = Alignment.Center,
    ) {
        val fill = Modifier
            .size(if (chosen) 18.dp else 22.dp)
            .clip(CircleShape)
        if (color != null) {
            Box(modifier = fill.background(color).border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape))
        } else {
            Box(modifier = fill.background(Brush.sweepGradient(HueStops)))
        }
        if (chosen) Box(modifier = Modifier.size(28.dp).border(2.dp, Color.White, CircleShape))
    }
}

private val HueStops = List(7) { Color.hsv(it * 60f % 360f, 0.8f, 1f) }

/** One's own colour, picked along the hues; back to the colours when done. */
@Composable
private fun HuePicker(color: Color, onPick: (Color) -> Unit, onDone: () -> Unit) {
    OptionRow {
        IconButton(onClick = onDone) {
            Icon(imageVector = Symbols.ArrowBack, contentDescription = stringResource(id = R.string.editor_color_done), tint = ExtendedTheme.colors.text)
        }
        var hue by remember { mutableFloatStateOf(hueOf(color)) }
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
                .height(28.dp)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(HueStops))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        fun pick(x: Float) {
                            hue = (x / size.width).coerceIn(0f, 1f) * 360f
                            onPick(Color.hsv(hue, 0.8f, 1f))
                        }
                        pick(down.position.x)
                        drag(down.id) { pick(it.position.x) }
                    }
                }
                .drawWithContent {
                    drawContent()
                    val x = (hue / 360f * size.width).coerceIn(size.height / 2, size.width - size.height / 2)
                    drawCircle(Color.White, size.height / 2 - 2.dp.toPx(), Offset(x, size.height / 2), style = Stroke(3.dp.toPx()))
                },
        )
    }
}

private fun hueOf(color: Color): Float {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    return hsv[0]
}

/** Three sizes, each a dot of its size. */
@Composable
private fun Sizes(selected: Int, onSelect: (Int) -> Unit) {
    val colors = ExtendedTheme.colors
    val labels = listOf(R.string.editor_size_small, R.string.editor_size_medium, R.string.editor_size_large)
    Row(
        modifier = Modifier
            .background(TrackColor, CircleShape)
            .padding(2.dp),
    ) {
        for (i in 0..2) {
            val label = stringResource(id = labels[i])
            Box(
                modifier = Modifier
                    .size(30.dp, 36.dp)
                    .clip(CircleShape)
                    .background(if (i == selected) BubbleColor else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onSelect(i) }
                    .semantics {
                        contentDescription = label
                        this.selected = i == selected
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size((6 + i * 4).dp)
                        .background(if (i == selected) colors.text else colors.textSecondary, CircleShape),
                )
            }
        }
    }
}

private val MaskStyle.label: Int
    get() = when (this) {
        MaskStyle.Mosaic -> R.string.editor_mosaic
        MaskStyle.Blur -> R.string.editor_blur
        MaskStyle.Block -> R.string.editor_block
    }

private val MarkKind.label: Int
    get() = when (this) {
        MarkKind.Pen -> R.string.editor_pen
        MarkKind.Highlighter -> R.string.editor_highlighter
        MarkKind.Line -> R.string.editor_line
        MarkKind.Arrow -> R.string.editor_arrow
        MarkKind.Box -> R.string.editor_box
        MarkKind.Ellipse -> R.string.editor_ellipse
        MarkKind.Number -> R.string.editor_number
    }

private val CaptionStyle.label: Int
    get() = when (this) {
        CaptionStyle.Outline -> R.string.editor_outline
        CaptionStyle.Filled -> R.string.editor_filled
        CaptionStyle.Plain -> R.string.editor_plain
    }
