package oolong.client.ui.common.theme.compose

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/**
 * A rectangle with smooth corners: each corner leaves the straight edge on a curve whose curvature
 * starts at zero, runs through a circular arc of the corner's radius and leaves the same way, so
 * the outline has no visible kink where edge and corner meet. [smoothing] is how much of the
 * corner the easing curves take, from 0 (a plain circular corner) to 1.
 *
 * Each corner takes its own size, as a [RoundedCornerShape]'s do, start and end following the
 * layout direction; one of size 0 is square. Each side is shared between its two corners by their
 * radii, and where a corner's share is too short for the full corner, the smoothing gives way
 * first, on that side only, and then the radii. A capsule ([Capsule]) so keeps its easing along
 * the long sides, and the curvature still builds up gradually into its round ends.
 */
@Immutable
class SmoothCornerShape(
    topStart: CornerSize,
    topEnd: CornerSize,
    bottomEnd: CornerSize,
    bottomStart: CornerSize,
    val smoothing: Float = DefaultSmoothing,
) : CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart) {
    /** Every corner [corner]. */
    constructor(corner: CornerSize, smoothing: Float = DefaultSmoothing) : this(corner, corner, corner, corner, smoothing)

    /**
     * Every corner [radius], except that without [top] or [bottom] the corners on that side are
     * square, as for the first or last row of a group whose rows are shaped one by one.
     */
    constructor(radius: Dp, smoothing: Float = DefaultSmoothing, top: Boolean = true, bottom: Boolean = true) : this(
        topStart = CornerSize(if (top) radius else 0.dp),
        topEnd = CornerSize(if (top) radius else 0.dp),
        bottomEnd = CornerSize(if (bottom) radius else 0.dp),
        bottomStart = CornerSize(if (bottom) radius else 0.dp),
        smoothing = smoothing,
    )

    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection,
    ): Outline {
        if (topStart + topEnd + bottomEnd + bottomStart <= 0f) return Outline.Rectangle(Rect(Offset.Zero, size))
        val ltr = layoutDirection == LayoutDirection.Ltr
        return Outline.Generic(
            Path().apply {
                addSmoothRect(
                    width = size.width,
                    height = size.height,
                    topLeft = if (ltr) topStart else topEnd,
                    topRight = if (ltr) topEnd else topStart,
                    bottomRight = if (ltr) bottomEnd else bottomStart,
                    bottomLeft = if (ltr) bottomStart else bottomEnd,
                    smoothing = smoothing,
                )
            }
        )
    }

    override fun copy(topStart: CornerSize, topEnd: CornerSize, bottomEnd: CornerSize, bottomStart: CornerSize): SmoothCornerShape =
        SmoothCornerShape(topStart, topEnd, bottomEnd, bottomStart, smoothing)

    /**
     * The same shape with plain circular corners, for drawing that takes only those, such as
     * Haze's glass. A smooth corner lies just inside it.
     */
    fun toCircular(): RoundedCornerShape = RoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart)

    override fun equals(other: Any?): Boolean =
        other is SmoothCornerShape && other.topStart == topStart && other.topEnd == topEnd &&
            other.bottomEnd == bottomEnd && other.bottomStart == bottomStart && other.smoothing == smoothing

    override fun hashCode(): Int {
        var result = topStart.hashCode()
        result = 31 * result + topEnd.hashCode()
        result = 31 * result + bottomEnd.hashCode()
        result = 31 * result + bottomStart.hashCode()
        return 31 * result + smoothing.hashCode()
    }

    override fun toString(): String =
        "SmoothCornerShape(topStart=$topStart, topEnd=$topEnd, bottomEnd=$bottomEnd, bottomStart=$bottomStart, smoothing=$smoothing)"

    companion object {
        /** The smoothing every corner in the app uses. */
        const val DefaultSmoothing = 0.6f

        /** Round ends across the shorter side: buttons, bars, switches. */
        val Capsule = SmoothCornerShape(CornerSize(percent = 50))
    }
}

/** Adds a [width] × [height] rectangle at the origin with smooth corners of [radius], going clockwise. */
fun Path.addSmoothRect(width: Float, height: Float, radius: Float, smoothing: Float) =
    addSmoothRect(width, height, radius, radius, radius, radius, smoothing)

/**
 * Adds a [width] × [height] rectangle at the origin with a smooth corner of its own radius at each
 * corner, going clockwise. Radii too large for a side are scaled down together until every side
 * fits its two corners, and each side is shared between its corners by their radii.
 */
fun Path.addSmoothRect(
    width: Float,
    height: Float,
    topLeft: Float,
    topRight: Float,
    bottomRight: Float,
    bottomLeft: Float,
    smoothing: Float,
) {
    fun fit(length: Float, a: Float, b: Float) = if (a + b > length) length / (a + b) else 1f
    val scale = minOf(
        fit(width, topLeft, topRight),
        fit(height, topRight, bottomRight),
        fit(width, bottomLeft, bottomRight),
        fit(height, topLeft, bottomLeft),
    )
    val tl = max(0f, topLeft * scale)
    val tr = max(0f, topRight * scale)
    val br = max(0f, bottomRight * scale)
    val bl = max(0f, bottomLeft * scale)

    // The smoothing corner [r] can have along a side of [length] it shares with a corner of [other]
    fun side(length: Float, r: Float, other: Float): Float {
        if (r <= 0f) return 0f
        val share = length * r / (r + other)
        return max(0f, min(smoothing.coerceIn(0f, 1f), share / r - 1))
    }
    smoothCorner(Offset(width, 0f), Offset(1f, 0f), Offset(0f, 1f), tr, side(width, tr, tl), side(height, tr, br), first = true)
    smoothCorner(Offset(width, height), Offset(0f, 1f), Offset(-1f, 0f), br, side(height, br, tr), side(width, br, bl), first = false)
    smoothCorner(Offset(0f, height), Offset(-1f, 0f), Offset(0f, -1f), bl, side(width, bl, br), side(height, bl, tl), first = false)
    smoothCorner(Offset(0f, 0f), Offset(0f, -1f), Offset(1f, 0f), tl, side(height, tl, bl), side(width, tl, tr), first = false)
    close()
}

/**
 * One corner of [radius] at [corner], square at 0, reached along [inward] and left along [outward] (unit
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
    if (r <= 0f) {
        if (first) moveTo(corner.x, corner.y) else lineTo(corner.x, corner.y)
        return
    }
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
