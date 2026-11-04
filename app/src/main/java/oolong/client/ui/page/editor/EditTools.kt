package oolong.client.ui.page.editor

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.collections.immutable.toImmutableList
import oolong.client.R
import oolong.client.ui.common.theme.compose.ExtendedTheme
import oolong.client.ui.common.theme.compose.Motion
import oolong.client.ui.common.theme.compose.Symbols
import oolong.client.ui.widgets.compose.IconButton
import oolong.client.ui.widgets.compose.Refocus
import oolong.client.ui.widgets.compose.SegmentedControl
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
 * The tools on one piece of glass, along the bottom or, when [side], down the end of the screen;
 * the chosen tool's options grow out of its button, above it or beside it, and go back into it.
 */
@Composable
fun EditorTools(state: EditorState, side: Boolean = false) {
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            panel()
            rail()
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

/** A tool's options: one or two rows of choices. */
@Composable
private fun ToolOptions(state: EditorState, tool: EditorTool) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        when (tool) {
            EditorTool.Crop -> CompositionLocalProvider(LocalContentColor provides ExtendedTheme.colors.text) {
                val portrait = state.history.framing.turned(state.renderer.size).let { it.height > it.width }
                Choice(
                    options = listOf(
                        stringResource(id = R.string.editor_ratio_free),
                        stringResource(id = R.string.editor_ratio_original),
                        "1:1",
                        if (portrait) "3:4" else "4:3",
                        if (portrait) "9:16" else "16:9",
                    ),
                    selected = state.ratio.ordinal,
                    onSelect = { state.pickRatio(CropRatio.entries[it]) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
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

            EditorTool.Mask -> Row(verticalAlignment = Alignment.CenterVertically) {
                Choice(
                    options = listOf(
                        stringResource(id = R.string.editor_mosaic),
                        stringResource(id = R.string.editor_blur),
                        stringResource(id = R.string.editor_block),
                    ),
                    selected = state.maskStyle.ordinal,
                    onSelect = { state.maskStyle = MaskStyle.entries[it] },
                    modifier = Modifier.weight(1f),
                )
                Sizes(selected = state.maskSize, onSelect = { state.maskSize = it })
            }

            EditorTool.Mark -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Choice(
                        options = listOf(
                            stringResource(id = R.string.editor_pen),
                            stringResource(id = R.string.editor_arrow),
                            stringResource(id = R.string.editor_box),
                        ),
                        selected = state.markKind.ordinal,
                        onSelect = { state.markKind = MarkKind.entries[it] },
                        modifier = Modifier.weight(1f),
                    )
                    Sizes(selected = state.markSize, onSelect = { state.markSize = it })
                }
                Swatches(selected = state.markColor, onSelect = { state.markColor = it })
            }

            EditorTool.Text -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Choice(
                        options = listOf(
                            stringResource(id = R.string.editor_outline),
                            stringResource(id = R.string.editor_filled),
                            stringResource(id = R.string.editor_plain),
                        ),
                        selected = state.captionStyle.ordinal,
                        onSelect = {
                            state.captionStyle = CaptionStyle.entries[it]
                            state.styleCaption()
                        },
                        modifier = Modifier.weight(1f),
                    )
                    Sizes(selected = state.captionSize, onSelect = {
                        state.captionSize = it
                        state.styleCaption()
                    })
                }
                Swatches(selected = state.captionColor, onSelect = {
                    state.captionColor = it
                    state.styleCaption()
                })
            }
        }
    }
}

@Composable
private fun Choice(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    SegmentedControl(
        options = remember(options) { options.toImmutableList() },
        selected = selected,
        onSelect = onSelect,
        trackColor = TrackColor,
        modifier = modifier,
    )
}

/** Three sizes, each a dot of its size. */
@Composable
private fun Sizes(selected: Int, onSelect: (Int) -> Unit) {
    val colors = ExtendedTheme.colors
    val labels = listOf(R.string.editor_size_small, R.string.editor_size_medium, R.string.editor_size_large)
    Row(modifier = Modifier.padding(start = 8.dp)) {
        for (i in 0..2) {
            val label = stringResource(id = labels[i])
            Box(
                modifier = Modifier
                    .size(36.dp)
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

@Composable
private fun Swatches(selected: Color, onSelect: (Color) -> Unit) {
    val labels = listOf(
        R.string.editor_color_red,
        R.string.editor_color_yellow,
        R.string.editor_color_green,
        R.string.editor_color_blue,
        R.string.editor_color_white,
        R.string.editor_color_black,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        EditorColors.forEachIndexed { i, color ->
            val label = stringResource(id = labels[i])
            val chosen = color == selected
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.RadioButton) { onSelect(color) }
                    .semantics {
                        contentDescription = label
                        this.selected = chosen
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(if (chosen) 22.dp else 26.dp)
                        .background(color, CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape),
                )
                if (chosen) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .border(2.dp, Color.White, CircleShape),
                    )
                }
            }
        }
    }
}
