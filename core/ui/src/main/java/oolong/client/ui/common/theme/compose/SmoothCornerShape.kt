package oolong.client.ui.common.theme.compose

import androidx.compose.foundation.shape.RoundedCornerShape
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
import kotlin.math.tan

/**
 * A rectangle with smooth corners: each corner leaves the straight edge on a curve whose curvature
 * starts at zero, runs through a circular arc of [radius] and leaves the same way, so the outline
 * has no visible kink where edge and corner meet. [smoothing] is how much of the corner the
 * easing curves take, from 0 (a plain circular corner) to 1.
 *
 * Where a side is too short for the full corner, the smoothing gives way first, on that side
 * only, and then the radius. A capsule ([Capsule]) so keeps its easing along the long sides, and
 * the curvature still builds up gradually into its round ends.
 *
 * Without [top] or [bottom], the corners on that side are square, as for the first or last row of
 * a group whose rows are shaped one by one.
 */
@Immutable
class SmoothCornerShape(
    val radius: Dp,
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

    /**
     * The same shape with plain circular corners, for drawing that takes only those, such as
     * Haze's glass. A smooth corner lies just inside it.
     */
    fun toCircular(): RoundedCornerShape =
        if (radius == Dp.Infinity) RoundedCornerShape(percent = 50) else RoundedCornerShape(radius)

    companion object {
        /** The smoothing every corner in the app uses. */
        const val DefaultSmoothing = 0.6f

        /** Round ends across the shorter side: buttons, bars, switches. */
        val Capsule = SmoothCornerShape(Dp.Infinity)
    }
}

private fun Size.toRect() = androidx.compose.ui.geometry.Rect(Offset.Zero, this)

/** Adds a [width] × [height] rectangle at the origin with smooth corners, going clockwise. */
fun Path.addSmoothRect(width: Float, height: Float, radius: Float, smoothing: Float) {
    val r = min(radius, min(width, height) / 2)
    // Each side gives each of its two corners half its length
    val across = sideSmoothing(width / 2, r, smoothing)
    val down = sideSmoothing(height / 2, r, smoothing)
    smoothCorner(Offset(width, 0f), Offset(1f, 0f), Offset(0f, 1f), r, across, down, first = true)
    smoothCorner(Offset(width, height), Offset(0f, 1f), Offset(-1f, 0f), r, down, across, first = false)
    smoothCorner(Offset(0f, height), Offset(-1f, 0f), Offset(0f, -1f), r, across, down, first = false)
    smoothCorner(Offset(0f, 0f), Offset(0f, -1f), Offset(1f, 0f), r, down, across, first = false)
    close()
}

/** The smoothing a corner of [radius] can have along a side that gives it [budget] of its length. */
private fun sideSmoothing(budget: Float, radius: Float, smoothing: Float): Float =
    max(0f, min(smoothing.coerceIn(0f, 1f), budget / radius - 1))

/**
 * One corner of [radius] at [corner], reached along [inward] and left along [outward] (unit
 * vectors): a cubic easing from the straight edge into the arc, the arc itself, and a cubic easing
 * back out. Each easing takes the smoothing of its own side, [sIn] and [sOut], turning 45° times
 * it, and reaches (1 + its smoothing) × [radius] along that side.
 */
private fun Path.smoothCorner(
    corner: Offset,
    inward: Offset,
    outward: Offset,
    radius: Float,
    sIn: Float,
    sOut: Float,
    first: Boolean,
) {
    val r = radius
    val turnIn = 45f * sIn
    val turnOut = 45f * sOut
    val arcAngle = 90f - turnIn - turnOut

    // The easing on one side: from the edge [reach] before the corner, a cubic with handles a and
    // a + b along the edge, ending at the arc c further along the edge and d in from it
    fun easing(s: Float, turn: Float): FloatArray {
        val reach = (1 + s) * r
        val c = r * tan(rad(turn / 2)) * cos(rad(turn))
        val d = c * tan(rad(turn))
        // The arc starts r × (1 - sin turn) short of the corner along the edge
        val b = (reach - r * (1 - sin(rad(turn))) - c) / 3
        return floatArrayOf(reach, 2 * b, b, c, d)
    }
    val (reachIn, aIn, bIn, cIn, dIn) = easing(sIn, turnIn)
    val (reachOut, aOut, bOut, cOut, dOut) = easing(sOut, turnOut)

    val start = corner - inward * reachIn
    val end = corner + outward * reachOut
    val arcStart = start + inward * (aIn + bIn + cIn) + outward * dIn
    val arcEnd = end - outward * (aOut + bOut + cOut) - inward * dOut
    // The arc as a cubic, with handles along its end tangents
    val handle = 4f / 3f * tan(rad(arcAngle) / 4) * r
    val startTangent = inward * cos(rad(turnIn)) + outward * sin(rad(turnIn))
    val endTangent = inward * sin(rad(turnOut)) + outward * cos(rad(turnOut))

    if (first) moveTo(start.x, start.y) else lineTo(start.x, start.y)
    cubicTo(start + inward * aIn, start + inward * (aIn + bIn), arcStart)
    cubicTo(arcStart + startTangent * handle, arcEnd - endTangent * handle, arcEnd)
    cubicTo(end - outward * (aOut + bOut), end - outward * aOut, end)
}

private operator fun FloatArray.component5() = this[4]

private fun Path.cubicTo(c1: Offset, c2: Offset, to: Offset) = cubicTo(c1.x, c1.y, c2.x, c2.y, to.x, to.y)

private fun rad(degrees: Float) = (degrees * Math.PI / 180.0).toFloat()
