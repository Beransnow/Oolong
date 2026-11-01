package com.huanchengfly.tieba.post.ui.widgets.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.layout.windowInsetsTopHeight
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

private val TopBarHeight = 56.dp

/**
 * How far the glass keeps fading out below the bar. It overlaps the top of the content: content
 * at rest starts at the bar's edge, above the fade, so the fade does not leave a gap under the bar.
 */
private val TopBarFade = 16.dp

/**
 * The bottom edge of the solid part of a top bar, above its fade. [GlassScaffold] pads its
 * content down to this line rather than to the full height of the bar.
 */
val GlassTopBarEdge = HorizontalAlignmentLine { a, b -> maxOf(a, b) }

/**
 * A top bar that floats over the content of a [GlassScaffold], from the top edge of the screen.
 * Its background blurs the content behind it and fades out below the bar instead of ending at a
 * hard edge. Double-tapping it scrolls the page to the top.
 *
 * @param hiddenFraction 0 shows the bar, 1 moves it (and its fade) fully off screen. Glass stays
 * behind the status bar while the bar is hidden, so content never scrolls by sharp under it.
 * @param content extra rows under the bar, such as tabs. They share the bar's background.
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
) {
    val coroutineScope = rememberCoroutineScope()
    val statusBar = WindowInsets.statusBars.union(WindowInsets.displayCutout)
    Layout(
        content = {
            Box(modifier = Modifier.layoutId(TopBarLayer.StatusBar).topBarGlass(0.5f))
            Box(modifier = Modifier.layoutId(TopBarLayer.Background).topBarGlass(0.8f))
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
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val foreground = measurables.first { it.layoutId == TopBarLayer.Foreground }
            .measure(constraints.copy(minHeight = 0))
        val fade = TopBarFade.roundToPx()
        val height = foreground.height + fade
        val background = measurables.first { it.layoutId == TopBarLayer.Background }
            .measure(Constraints.fixed(foreground.width, height))
        val statusBarStrip = measurables.first { it.layoutId == TopBarLayer.StatusBar }
            .measure(Constraints.fixed(foreground.width, statusBar.getTop(this) + fade))
        // Hiding moves the layers rather than the whole bar, so the status bar keeps its glass
        // and the hidden bar no longer takes touches.
        layout(foreground.width, height, mapOf(GlassTopBarEdge to foreground.height)) {
            if (hiddenFraction() > 0f) {
                statusBarStrip.placeWithLayer(0, 0) { alpha = hiddenFraction() }
            }
            background.placeWithLayer(0, 0) { translationY = -height * hiddenFraction() }
            foreground.placeWithLayer(0, 0) {
                translationY = -height * hiddenFraction()
                alpha = 1f - hiddenFraction()
            }
        }
    }
}

/**
 * Glass behind the status bar only, for pages without a top bar whose content scrolls up under
 * the status bar.
 */
@Composable
fun GlassStatusBar(modifier: Modifier = Modifier) {
    Layout(
        content = {
            Box(modifier = Modifier.topBarGlass(0.5f))
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
            )
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val spacer = measurables[1].measure(constraints.copy(minHeight = 0))
        val background = measurables[0]
            .measure(Constraints.fixed(spacer.width, spacer.height + TopBarFade.roundToPx()))
        layout(background.width, background.height, mapOf(GlassTopBarEdge to spacer.height)) {
            background.place(0, 0)
        }
    }
}

/** Single-line title text for [GlassTopBar]. */
@Composable
fun GlassTopBarTitle(text: String) {
    Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

private enum class TopBarLayer { StatusBar, Background, Foreground }
