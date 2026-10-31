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
import androidx.compose.ui.graphics.graphicsLayer
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
 * How far the glass keeps fading out below the bar. Counted in the bar's height, so content at
 * rest starts below the fade and only content scrolled under it gets blurred.
 */
private val TopBarFade = 16.dp

/**
 * A top bar that floats over the content of a [GlassScaffold], from the top edge of the screen.
 * Its background blurs the content behind it and fades out below the bar instead of ending at a
 * hard edge. Double-tapping it scrolls the page to the top.
 *
 * @param hiddenFraction 0 shows the bar, 1 moves it (and its fade) fully off screen.
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
    Layout(
        content = {
            Box(modifier = Modifier.layoutId(TopBarLayer.Background).topBarGlass(0.8f))
            Column(
                modifier = Modifier
                    .layoutId(TopBarLayer.Foreground)
                    .windowInsetsPadding(
                        WindowInsets.statusBars
                            .union(WindowInsets.displayCutout)
                            .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
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
        modifier = modifier
            .graphicsLayer {
                translationY = -size.height * hiddenFraction()
            }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onDoubleClick = { coroutineScope.emitGlobalEvent(GlobalEvent.ScrollToTop) },
                onClick = {},
            ),
    ) { measurables, constraints ->
        val foreground = measurables.first { it.layoutId == TopBarLayer.Foreground }
            .measure(constraints.copy(minHeight = 0))
        val fade = TopBarFade.roundToPx()
        val background = measurables.first { it.layoutId == TopBarLayer.Background }
            .measure(Constraints.fixed(foreground.width, foreground.height + fade))
        layout(background.width, background.height) {
            background.place(0, 0)
            foreground.place(0, 0)
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
        layout(background.width, background.height) {
            background.place(0, 0)
        }
    }
}

/** Single-line title text for [GlassTopBar]. */
@Composable
fun GlassTopBarTitle(text: String) {
    Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

private enum class TopBarLayer { Background, Foreground }
