package oolong.client.ui.widgets.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ProvideTextStyle
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.layout.HorizontalAlignmentLine
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import oolong.client.arch.GlobalEvent
import oolong.client.arch.emitGlobalEvent
import oolong.client.ui.common.theme.compose.ExtendedTheme
import kotlin.math.roundToInt

/** The height of a [GlassTopBar]'s row of icons and title, below the status bar. */
internal val GlassTopBarRowHeight = 56.dp

/** The height of everything floating in that row: buttons, capsules, a title that is a button. */
val GlassTopBarItemHeight = 48.dp

private val TopBarRowPadding = 12.dp
private val TitlePadding = 8.dp

/** The navigation icon's and the actions' glass. */
private val TopBarCapsule = RoundedCornerShape(percent = 50)

/** Where the title starts without a navigation icon, past the row's padding. */
private val BareTitlePadding = 4.dp

/**
 * Where the title starts in a [GlassTopBar] with a navigation icon, for things that move into the
 * title's place: past the row's padding, the 48dp icon button and the title's own padding.
 */
internal val GlassTopBarTitleStart = TopBarRowPadding + 48.dp + TitlePadding

/**
 * The bottom edge of a top bar's rows, without its pinned rows. [GlassScaffold] pads its content
 * down to this line, so pinned rows float over the content and can come and go without moving it.
 */
val GlassTopBarEdge = HorizontalAlignmentLine { a, b -> maxOf(a, b) }

/**
 * A top bar that floats over the content of a [GlassScaffold], from the top edge of the screen.
 * Its background blurs the content behind it, strongest at the top, and fades out within the
 * bar to nothing at its bottom edge instead of ending at a hard line. The glass takes no space of
 * its own below the rows. The navigation icon and the actions float on their own glass: a round
 * button and a capsule. Double-tapping the bar scrolls the page to the top.
 *
 * @param hiddenFraction 0 shows the bar, 1 hides it: everything between the status bar and the
 * pinned rows slides away. Glass stays behind the status bar and the pinned rows, so content
 * never scrolls by sharp under them.
 * The title becomes the page's large title when its [GlassScaffold] has one; see [LargeTitleState].
 *
 * @param title null leaves out the title row, with [navigationIcon] and [actions], for a bar of
 * [content] alone, such as tabs whose page is named elsewhere.
 * @param content extra rows under the bar, such as tabs. They share the bar's background.
 * @param pinnedContent rows below [content] that stay when the bar hides, such as a list's sticky
 * header. They share the bar's background and float over the content: the scaffold does not pad
 * the content for them.
 * @param centerTitle centres the title on the whole row rather than starting it after the
 * navigation icon, for tabs that are the page's title. It does not take part in a large title.
 * @param actionsInCapsule false leaves [actions] off the capsule, for an action that is a button
 * of its own, such as Save.
 * @param wash a colour washed over the glass, strongest at the top and gone at the bottom edge,
 * such as a forum's own colour on its page.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassTopBar(
    title: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    actionsInCapsule: Boolean = true,
    centerTitle: Boolean = false,
    hiddenFraction: () -> Float = { 0f },
    wash: Color = Color.Unspecified,
    pinnedContent: (@Composable ColumnScope.() -> Unit)? = null,
    // Last, so a trailing lambda passes the bar's rows, which the content is padded for
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val coroutineScope = rememberCoroutineScope()
    val largeTitle = LocalLargeTitle.current
    val statusBar = WindowInsets.statusBars.union(WindowInsets.displayCutout)
    Layout(
        content = {
            Box(
                modifier = Modifier
                    .layoutId(TopBarLayer.Background)
                    // The buttons' glass sees the bar's blur too, so it never shows the content
                    // sharper than the bar around it. The blur renders once into its own layer,
                    // which the buttons reuse instead of blurring the content again.
                    .lensTarget()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .topBarGlass()
                    .then(
                        if (wash.isSpecified) {
                            Modifier.background(Brush.verticalGradient(listOf(wash, wash.copy(alpha = 0f))))
                        } else Modifier
                    )
            )
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
                if (title != null) Box(
                    modifier = Modifier
                        .then(
                            if (largeTitle != null) {
                                Modifier.onGloballyPositioned {
                                    largeTitle.rowBottom = it.positionInWindow().y + it.size.height
                                }
                            } else Modifier
                        )
                        .fillMaxWidth()
                        .height(GlassTopBarRowHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = TopBarRowPadding),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProvideContentColor(color = ExtendedTheme.colors.text) {
                            if (navigationIcon != null) {
                                Box(modifier = Modifier.floatingGlass(TopBarCapsule)) { navigationIcon() }
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(
                                        start = if (navigationIcon != null) TitlePadding else BareTitlePadding,
                                        end = TitlePadding,
                                    ),
                            ) {
                                if (!centerTitle) ProvideTextStyle(
                                    value = MaterialTheme.typography.h6.copy(fontWeight = FontWeight.Bold)
                                ) {
                                    if (largeTitle == null) {
                                        title()
                                    } else {
                                        // Measured where the bar puts it, before the move to the slot
                                        Box(
                                            modifier = Modifier
                                                .onGloballyPositioned {
                                                    val position = it.positionInWindow()
                                                    largeTitle.titleTop = position.y
                                                    largeTitle.titleStart = position.x
                                                    largeTitle.titleHeight = it.size.height
                                                }
                                                .graphicsLayer { with(largeTitle) { place() } }
                                        ) { title() }
                                    }
                                }
                            }
                            if (actionsInCapsule) {
                                // With no actions the row measures nothing, so no glass shows
                                Row(
                                    modifier = Modifier.floatingGlass(TopBarCapsule),
                                    verticalAlignment = Alignment.CenterVertically,
                                    content = actions,
                                )
                            } else {
                                actions()
                            }
                        }
                    }
                    // Centred on the whole row, whatever buttons sit at either end
                    if (centerTitle) {
                        ProvideContentColor(color = ExtendedTheme.colors.text) {
                            ProvideTextStyle(value = MaterialTheme.typography.h6.copy(fontWeight = FontWeight.Bold), content = title)
                        }
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
        val height = foreground.height + (pinned?.height ?: 0)
        // Hiding slides away the rows below the status bar, and the pinned rows move up after
        // them. The glass shrinks rather than slides, so it still covers the status bar and the
        // pinned rows and fades out at their bottom edge.
        val hidden = ((foreground.height - statusBar.getTop(this)) * hiddenFraction()).roundToInt()
        // The edge reaches a little past the bar, over the content, so the blur comes in before
        // the bar's edge instead of ending in a line there. It shows only once content is under it.
        val extend = TopBarEdgeTuning.extend.dp.roundToPx()
        val background = measurables.first { it.layoutId == TopBarLayer.Background }
            .measure(Constraints.fixed(foreground.width, height - hidden + extend))
        layout(foreground.width, height, mapOf(GlassTopBarEdge to foreground.height)) {
            background.place(0, 0)
            foreground.placeWithLayer(0, -hidden) { alpha = 1f - hiddenFraction() }
            pinned?.place(0, foreground.height - hidden)
        }
    }
}

/**
 * Glass behind the status bar only, for pages without a top bar whose content scrolls up under
 * the status bar. Like [GlassTopBar], it fades out to nothing at its bottom edge.
 */
@Composable
fun GlassStatusBar(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsTopHeight(WindowInsets.statusBars)
            .topBarGlass()
    )
}

/** Single-line title text for [GlassTopBar]. */
@Composable
fun GlassTopBarTitle(text: String) {
    Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

private enum class TopBarLayer { Background, Foreground, Pinned }
