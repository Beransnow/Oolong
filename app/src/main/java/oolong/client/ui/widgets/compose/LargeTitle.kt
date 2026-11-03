package oolong.client.ui.widgets.compose

import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.dp

/** How much bigger the large title is than the bar's own. */
internal const val LargeTitleScale = 1.5f

/** Space above and below the large title, between the bar and the content. */
internal val LargeTitleTop = 4.dp
internal val LargeTitleBottom = 12.dp

/** Where the large title starts, from the page's start edge. */
internal val LargeTitleStart = 16.dp

/**
 * A page's large title: big at the top of the page, below the bar, it moves up and shrinks into
 * the bar as the content scrolls. The scaffold pads the content down by [height] and keeps an
 * anchor there, moving with the content, which the bar's own title is matched to
 * ([matchedGeometry]) over the first [height] of scroll.
 *
 * [lineHeight], [topPx], [bottomPx] and [startPx] are in pixels.
 */
@Stable
class LargeTitleState internal constructor(
    private val lineHeight: Int,
    internal val topPx: Float,
    private val bottomPx: Float,
    internal val startPx: Float,
) {
    /** The room the large title takes above the content. */
    val height: Int get() = (topPx + titleHeight + bottomPx).toInt()

    /** The large title's own height, as tall as its anchor. */
    internal val titleHeight: Float get() = lineHeight * LargeTitleScale
}

/** The key the large title's anchor and the bar's title are matched by. */
internal object LargeTitleAnchor

/** The large title a [GlassScaffold] gives its top bar, if it has one. */
val LocalLargeTitle = compositionLocalOf<LargeTitleState?> { null }
