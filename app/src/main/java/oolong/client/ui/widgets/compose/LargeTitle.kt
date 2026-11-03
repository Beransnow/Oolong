package oolong.client.ui.widgets.compose

import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter

/** How much bigger the large title is than the bar's own. */
internal const val LargeTitleScale = 1.5f

/** Space above and below the large title, between the bar and the content. */
internal val LargeTitleTop = 4.dp
internal val LargeTitleBottom = 12.dp

/** Where the large title starts, from the page's start edge. */
internal val LargeTitleStart = 16.dp

/**
 * A page's large title: big at the top of the page, below the bar, it moves up and shrinks into
 * the bar as the content scrolls. It is the [GlassTopBar]'s own title, moved and scaled by how
 * far the content has scrolled ([ScrollEdgeState]), so it knows nothing of the content: the
 * scaffold makes room for it by padding the content down by [height].
 *
 * [lineHeight], [topPx], [bottomPx] and [startPx] are in pixels; the rest are window positions
 * the bar measures.
 */
@Stable
class LargeTitleState internal constructor(
    private val lineHeight: Int,
    private val topPx: Float,
    private val bottomPx: Float,
    private val startPx: Float,
) {
    /** The bar's title as the bar lays it out, which is where it ends up. */
    internal var titleTop by mutableFloatStateOf(0f)
    internal var titleStart by mutableFloatStateOf(0f)

    /** The bottom of the bar's title row, below which the large title sits at rest. */
    internal var rowBottom by mutableFloatStateOf(0f)

    /** The room the large title takes above the content. */
    val height: Int get() = (topPx + lineHeight * LargeTitleScale + bottomPx).toInt()

    /** How far the title moves, from its place in the bar down to its place below it. */
    private val travel: Float get() = (rowBottom + topPx - titleTop).coerceAtLeast(0f)

    /** How far the title is still below the bar: [travel] at the top of the page, 0 in the bar. */
    private fun offset(scrolled: Int?): Float = (travel - (scrolled ?: 0)).coerceIn(0f, travel)

    /** 0 with the title in the bar, 1 with it fully large. */
    private fun expansion(scrolled: Int?): Float = travel.let { if (it > 0f) offset(scrolled) / it else 0f }

    internal fun GraphicsLayerScope.place(scrolled: Int?, pageStart: Float) {
        val expanded = expansion(scrolled)
        val scale = 1f + (LargeTitleScale - 1f) * expanded
        transformOrigin = TransformOrigin(0f, 0f)
        scaleX = scale
        scaleY = scale
        translationY = offset(scrolled)
        translationX = (pageStart + startPx - titleStart) * expanded
    }

    /**
     * Settles a title left halfway once the content in view stops: it scrolls on into the bar, or
     * back out to large, whichever is nearer.
     */
    internal suspend fun settle(edge: ScrollEdgeState) {
        snapshotFlow { edge.inView }.collectLatest { source ->
            val scrollable = source?.scrollable ?: return@collectLatest
            snapshotFlow { scrollable.isScrollInProgress }.filter { !it }.collect {
                val scrolled = source.scrolled()
                if (scrolled <= 0 || scrolled >= travel) return@collect
                val by = if (scrolled > travel / 2) travel - scrolled else -scrolled.toFloat()
                scrollable.animateScrollBy(by, spring(stiffness = 600f))
            }
        }
    }
}

/** The large title a [GlassScaffold] gives its top bar, if it has one. */
val LocalLargeTitle = compositionLocalOf<LargeTitleState?> { null }
