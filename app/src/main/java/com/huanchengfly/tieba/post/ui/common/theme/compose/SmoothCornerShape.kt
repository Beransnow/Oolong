package com.huanchengfly.tieba.post.ui.common.theme.compose

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * A rectangle with smooth corners: each corner leaves the straight edge on a curve whose curvature
 * starts at zero, runs through a circular arc of [radius] and leaves the same way, so the outline
 * has no visible kink where edge and corner meet. [smoothing] is how much of the corner the
 * easing curves take, from 0 (a plain circular corner) to 1.
 *
 * When the shape is too small for the full corner, the smoothing gives way first and then the
 * radius, so a capsule ends in plain semicircles.
 *
 * Without [top] or [bottom], the corners on that side are square, as for the first or last row of
 * a group whose rows are shaped one by one.
 */
@Immutable
class SmoothCornerShape(
    private val radius: Dp,
    private val smoothing: Float = DefaultSmoothing,
    private val top: Boolean = true,
    private val bottom: Boolean = true,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { radius.toPx() }
        if (r <= 0f || size.isEmpty() || (!top && !bottom)) return Outline.Rectangle(size.toRect())
        if (top && bottom) return Outline.Generic(Path().apply { addSmoothRect(size.width, size.height, r, smoothing) })
        // The whole corner reaches past the square side and is cut off there
        val reach = r * 2
        val corners = Path().apply {
            addSmoothRect(size.width, size.height + reach, r, smoothing)
            if (!top) translate(Offset(0f, -reach))
        }
        val bounds = Path().apply { addRect(size.toRect()) }
        return Outline.Generic(Path.combine(PathOperation.Intersect, corners, bounds))
    }

    override fun equals(other: Any?): Boolean =
        other is SmoothCornerShape && other.radius == radius && other.smoothing == smoothing &&
            other.top == top && other.bottom == bottom

    override fun hashCode(): Int = ((31 * radius.hashCode() + smoothing.hashCode()) * 31 + top.hashCode()) * 31 + bottom.hashCode()

    override fun toString(): String = "SmoothCornerShape(radius=$radius, smoothing=$smoothing, top=$top, bottom=$bottom)"

    companion object {
        /** The smoothing every corner in the app uses. */
        const val DefaultSmoothing = 0.6f
    }
}

private fun Size.toRect() = androidx.compose.ui.geometry.Rect(Offset.Zero, this)

/** Adds a [width] × [height] rectangle at the origin with smooth corners, going clockwise. */
internal fun Path.addSmoothRect(width: Float, height: Float, radius: Float, smoothing: Float) {
    val budget = min(width, height) / 2
    smoothCorner(Offset(width, 0f), Offset(1f, 0f), Offset(0f, 1f), radius, smoothing, budget, first = true)
    smoothCorner(Offset(width, height), Offset(0f, 1f), Offset(-1f, 0f), radius, smoothing, budget, first = false)
    smoothCorner(Offset(0f, height), Offset(-1f, 0f), Offset(0f, -1f), radius, smoothing, budget, first = false)
    smoothCorner(Offset(0f, 0f), Offset(0f, -1f), Offset(1f, 0f), radius, smoothing, budget, first = false)
    close()
}

/**
 * One corner at [corner], reached along [inward] and left along [outward] (unit vectors): a cubic
 * from the straight edge into the arc, the arc itself, and a mirrored cubic back out.
 */
private fun Path.smoothCorner(
    corner: Offset,
    inward: Offset,
    outward: Offset,
    radius: Float,
    smoothing: Float,
    budget: Float,
    first: Boolean,
) {
    val r = min(radius, budget)
    var s = smoothing.coerceIn(0f, 1f)
    // How far along each edge the corner reaches
    var reach = (1 + s) * r
    if (reach > budget) {
        s = max(0f, min(s, budget / r - 1))
        reach = min(reach, budget)
    }
    val arcAngle = 90f * (1 - s)
    val arcChord = sin(rad(arcAngle / 2)) * r * sqrt(2f)
    val alpha = (90f - arcAngle) / 2
    val beta = 45f * s
    val c = r * tan(rad(alpha / 2)) * cos(rad(beta))
    val d = c * tan(rad(beta))
    val b = (reach - arcChord - c - d) / 3
    val a = 2 * b

    val start = corner - inward * reach
    val end = corner + outward * reach
    val arcStart = start + inward * (a + b + c) + outward * d
    val arcEnd = end - outward * (a + b + c) - inward * d
    // The arc as a cubic, with handles along its end tangents
    val handle = 4f / 3f * tan(rad(arcAngle) / 4) * r
    val startTangent = inward * cos(rad(beta)) + outward * sin(rad(beta))
    val endTangent = inward * sin(rad(beta)) + outward * cos(rad(beta))

    if (first) moveTo(start.x, start.y) else lineTo(start.x, start.y)
    cubicTo(start + inward * a, start + inward * (a + b), arcStart)
    cubicTo(arcStart + startTangent * handle, arcEnd - endTangent * handle, arcEnd)
    cubicTo(end - outward * (a + b), end - outward * a, end)
}

private fun Path.cubicTo(c1: Offset, c2: Offset, to: Offset) = cubicTo(c1.x, c1.y, c2.x, c2.y, to.x, to.y)

private fun rad(degrees: Float) = (degrees * Math.PI / 180.0).toFloat()
