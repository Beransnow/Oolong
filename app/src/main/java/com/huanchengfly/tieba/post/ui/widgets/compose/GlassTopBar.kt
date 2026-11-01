package com.huanchengfly.tieba.post.ui.widgets.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ProvideTextStyle
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.HorizontalAlignmentLine
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.huanchengfly.tieba.post.arch.GlobalEvent
import com.huanchengfly.tieba.post.arch.emitGlobalEvent
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import kotlin.math.roundToInt

private val TopBarHeight = 56.dp

/**
 * Empty space at the bottom of a top bar, below its last row, where its glass fades out. The blur
 * eases off over this distance instead of stopping at the rows, and ends at the bar's bottom edge.
 */
val GlassTopBarFadeBand = 24.dp

/** The same below the status bar, for [GlassStatusBar]. */
private val StatusBarFadeBand = 16.dp

/**
 * The bottom edge of a top bar without its pinned rows: its other rows and its fade band.
 * [GlassScaffold] pads its content down to this line, so pinned rows float over the content and
 * can come and go without moving it.
 */
val GlassTopBarEdge = HorizontalAlignmentLine { a, b -> maxOf(a, b) }

/**
 * A top bar that floats over the content of a [GlassScaffold], from the top edge of the screen.
 * Its background blurs the content behind it and fades out over [GlassTopBarFadeBand] at its
 * bottom, below its rows, instead of ending at a hard line. Double-tapping it scrolls the page to
 * the top.
 *
 * @param hiddenFraction 0 shows the bar, 1 hides it: everything between the status bar and the
 * pinned rows slides away. Glass stays behind the status bar and the pinned rows, so content
 * never scrolls by sharp under them.
 * @param content extra rows under the bar, such as tabs. They share the bar's background.
 * @param pinnedContent rows below [content] that stay when the bar hides, such as a list's sticky
 * header. They share the bar's background, above its fade band, but float over the content: the
 * scaffold does not pad the content for them.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    hiddenFraction: () -> Float = { 0f },
    content: (@Composable ColumnScope.() -> Unit)? = null,
    pinnedContent: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val coroutineScope = rememberCoroutineScope()
    val statusBar = WindowInsets.statusBars.union(WindowInsets.displayCutout)
    Layout(
        content = {
            Box(modifier = Modifier.layoutId(TopBarLayer.Background).topBarGlass(GlassTopBarFadeBand))
            Column(
                modifier = Modifier
                    .layoutId(TopBarLayer.Foreground)
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onDoubleClick = { coroutineScope.emitGlobalEvent(GlobalEvent.ScrollToTop) },
                        onClick = {},
                    )
                    .windowInsetsPadding(
                        statusBar.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(TopBarHeight)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProvideContentColor(color = ExtendedTheme.colors.text) {
                        navigationIcon?.invoke()
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = if (navigationIcon != null) 8.dp else 12.dp),
                        ) {
                            ProvideTextStyle(
                                value = MaterialTheme.typography.h6.copy(fontWeight = FontWeight.Bold)
                            ) {
                                title()
                            }
                        }
                        actions()
                    }
                }
                content?.invoke(this)
            }
            if (pinnedContent != null) {
                Column(
                    modifier = Modifier
                        .layoutId(TopBarLayer.Pinned)
                        .windowInsetsPadding(statusBar.only(WindowInsetsSides.Horizontal)),
                    content = pinnedContent,
                )
            }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val loose = constraints.copy(minHeight = 0)
        val foreground = measurables.first { it.layoutId == TopBarLayer.Foreground }.measure(loose)
        val pinned = measurables.firstOrNull { it.layoutId == TopBarLayer.Pinned }?.measure(loose)
        val band = GlassTopBarFadeBand.roundToPx()
        val height = foreground.height + (pinned?.height ?: 0) + band
        // Hiding slides away the rows below the status bar, and the pinned rows move up after
        // them. The glass shrinks rather than slides, so it still covers the status bar and the
        // pinned rows and fades out below them.
        val hidden = ((foreground.height - statusBar.getTop(this)) * hiddenFraction()).roundToInt()
        val background = measurables.first { it.layoutId == TopBarLayer.Background }
            .measure(Constraints.fixed(foreground.width, height - hidden))
        layout(foreground.width, height, mapOf(GlassTopBarEdge to foreground.height + band)) {
            background.place(0, 0)
            foreground.placeWithLayer(0, -hidden) { alpha = 1f - hiddenFraction() }
            pinned?.place(0, foreground.height - hidden)
        }
    }
}

/**
 * Glass behind the status bar only, for pages without a top bar whose content scrolls up under
 * the status bar. Like [GlassTopBar], it fades out over a band below the status bar.
 */
@Composable
fun GlassStatusBar(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .topBarGlass(StatusBarFadeBand)
            .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
            .height(StatusBarFadeBand)
    )
}

/** Single-line title text for [GlassTopBar]. */
@Composable
fun GlassTopBarTitle(text: String) {
    Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

private enum class TopBarLayer { Background, Foreground, Pinned }
