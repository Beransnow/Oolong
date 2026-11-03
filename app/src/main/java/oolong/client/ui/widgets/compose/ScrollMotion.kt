package oolong.client.ui.widgets.compose

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.util.lerp

/*
 * Scroll-driven motion. The content scrolls as it always does; what reacts to it reads how far it
 * has gone, never takes the scroll for itself. A [ScrollProgress] turns the scroll into 0..1, and
 * what moves reads it where it draws, so scrolling redraws but neither recomposes nor lays out.
 */

/**
 * How far along a scroll-driven change is, from 0 to 1. Read [value] or [fraction] in a draw
 * phase, such as a `graphicsLayer` block, so the scroll only redraws what follows it.
 */
@Stable
class ScrollProgress internal constructor(private val read: () -> Float) {
    val value: Float get() = read()

    /**
     * How far along [range] of the whole the progress is, eased by [easing]: 0 before it, 1 after.
     * Lets parts of one change keep their own pace, like a detail that fades in its first 40%.
     */
    fun fraction(range: ClosedFloatingPointRange<Float>, easing: Easing = LinearEasing): Float {
        val span = range.endInclusive - range.start
        val linear = if (span > 0f) ((value - range.start) / span).coerceIn(0f, 1f) else if (value >= range.start) 1f else 0f
        return easing.transform(linear)
    }
}

/**
 * Progress through [range] of the page's scroll: 0 with the content scrolled to [range]'s start
 * or less, 1 at its end or beyond. With [snapping], content let go inside the range scrolls on to
 * the nearer end.
 */
@Composable
fun rememberScrollProgress(range: ClosedRange<Dp>, snapping: Boolean = false): ScrollProgress {
    val start = with(LocalDensity.current) { range.start.toPx() }
    val end = with(LocalDensity.current) { range.endInclusive.toPx() }
    return rememberScrollProgress(start..end, snapping)
}

/** [rememberScrollProgress] over a range in pixels. */
@Composable
internal fun rememberScrollProgress(range: ClosedFloatingPointRange<Float>, snapping: Boolean): ScrollProgress {
    val page = LocalScrollEdge.current
    if (snapping && page != null) {
        DisposableEffect(page, range) {
            page.addSnapRange(range)
            onDispose { page.removeSnapRange(range) }
        }
    }
    return remember(page, range) {
        ScrollProgress {
            val scrolled = page?.scrolled ?: return@ScrollProgress 0f
            val span = range.endInclusive - range.start
            if (span > 0f) ((scrolled - range.start) / span).coerceIn(0f, 1f) else 1f
        }
    }
}

/**
 * Progress of the element marked with [matchedGeometryAnchor] under [anchor] into the top bar's
 * row: 0 while its middle is below the bar, 1 once it reaches the middle of the row. For something
 * that docks in the bar as it scrolls up to it, wherever it is on the page.
 */
@Composable
fun rememberScrollProgress(anchor: Any): ScrollProgress {
    val page = LocalScrollEdge.current
    return remember(page, anchor) {
        ScrollProgress {
            val bounds = page?.anchors?.get(anchor) ?: return@ScrollProgress 0f
            val rowTop = page.barRowTop
            val rowBottom = page.barRowBottom
            if (rowTop.isNaN() || rowBottom.isNaN()) return@ScrollProgress 0f
            val rowCenter = (rowTop + rowBottom) / 2f
            ((rowBottom - bounds.center.y) / (rowBottom - rowCenter)).coerceIn(0f, 1f)
        }
    }
}

/**
 * Marks where something matched by [matchedGeometry] under [key] starts from. Draws nothing of
 * its own beyond its content; it only reports its bounds. An anchor that scrolls out and is
 * disposed is remembered where it was last seen.
 */
@Composable
fun Modifier.matchedGeometryAnchor(key: Any): Modifier {
    val page = LocalScrollEdge.current ?: return this
    return onGloballyPositioned { page.anchors[key] = it.boundsInWindow() }
}

/**
 * Draws this where the [matchedGeometryAnchor] under [key] is at [progress] 0, and where it is
 * laid out at 1, moving and scaling in between, its height matched to the anchor's. Only how it is
 * drawn changes, so following the scroll redraws it but does not lay it out. Until its anchor has
 * been seen, it is not drawn.
 */
@Composable
fun Modifier.matchedGeometry(key: Any, progress: ScrollProgress): Modifier {
    val page = LocalScrollEdge.current ?: return this
    val own = remember { mutableStateOf(Rect.Zero) }
    return onGloballyPositioned { own.value = it.boundsInWindow() }
        .graphicsLayer {
            val anchor = page.anchors[key]
            val self = own.value
            if (anchor == null || self.height <= 0f) {
                alpha = 0f
                return@graphicsLayer
            }
            val p = progress.value
            val scale = lerp(anchor.height / self.height, 1f, p)
            transformOrigin = TransformOrigin(0f, 0f)
            scaleX = scale
            scaleY = scale
            translationX = lerp(anchor.left - self.left, 0f, p)
            translationY = lerp(anchor.top - self.top, 0f, p)
        }
}
