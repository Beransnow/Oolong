package oolong.client.ui.common.theme.compose

import android.graphics.Region
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SmoothCornerShapeTest {
    private val density = Density(1f)

    private fun outline(shape: SmoothCornerShape, size: Size) =
        shape.createOutline(size, LayoutDirection.Ltr, density)

    @Test
    fun fillsItsBounds() {
        val size = Size(200f, 120f)
        val bounds = (outline(SmoothCornerShape(24.dp), size) as Outline.Generic).path.getBounds()
        assertEquals(0f, bounds.left, 0.01f)
        assertEquals(0f, bounds.top, 0.01f)
        assertEquals(size.width, bounds.right, 0.01f)
        assertEquals(size.height, bounds.bottom, 0.01f)
    }

    @Test
    fun cutsTheCornerInsideTheCircularOne() {
        // Smoothing starts bending earlier, so near the corner the shape lies inside a circular corner
        val path = (outline(SmoothCornerShape(400.dp), Size(2000f, 2000f)) as Outline.Generic).path.asAndroidPath()
        val region = Region().apply { setPath(path, Region(0, 0, 2000, 2000)) }
        // A circular 400px corner covers x >= 2 at y = 360; the smooth one starts near x = 5.4
        assertFalse(region.contains(4, 360))
        assertTrue(region.contains(8, 360))
        assertTrue(region.contains(1000, 1))
    }

    @Test
    fun noRadiusIsARectangle() {
        assertTrue(outline(SmoothCornerShape(0.dp), Size(10f, 10f)) is Outline.Rectangle)
    }

    @Test
    fun aCapsuleStaysInItsBounds() {
        val size = Size(120f, 40f)
        val bounds = (outline(SmoothCornerShape(100.dp), size) as Outline.Generic).path.getBounds()
        assertEquals(size.width, bounds.width, 0.01f)
        assertEquals(size.height, bounds.height, 0.01f)
    }

    @Test
    fun aCapsuleEasesAlongItsLongSidesIntoRoundEnds() {
        val path = (outline(SmoothCornerShape.Capsule, Size(400f, 100f)) as Outline.Generic).path.asAndroidPath()
        val region = Region().apply { setPath(path, Region(0, 0, 400, 100)) }
        // Its ends still reach the middle of each short side
        assertTrue(region.contains(1, 50))
        assertTrue(region.contains(398, 50))
        // Easing in along the long side, it bends away before a circular end would: one covers
        // x >= 40 at y = 1, the smooth one starts near x = 44
        assertTrue(region.contains(46, 1))
        assertFalse(region.contains(42, 1))
    }

    @Test
    fun aSquareCapsuleIsACircle() {
        val path = (outline(SmoothCornerShape.Capsule, Size(100f, 100f)) as Outline.Generic).path.asAndroidPath()
        val region = Region().apply { setPath(path, Region(0, 0, 100, 100)) }
        // On the circle of radius 50 at 45°: 50 - 35.4 = 14.6 from each side
        assertTrue(region.contains(16, 16))
        assertFalse(region.contains(13, 13))
    }

    @Test
    fun eachCornerTakesItsOwnRadiusByLayoutDirection() {
        val shape = SmoothCornerShape(CornerSize(40.dp), CornerSize(0.dp), CornerSize(0.dp), CornerSize(0.dp))
        fun region(direction: LayoutDirection): Region {
            val path = (shape.createOutline(Size(200f, 100f), direction, density) as Outline.Generic).path.asAndroidPath()
            return Region().apply { setPath(path, Region(0, 0, 200, 100)) }
        }
        // Rounded at the start of the top only: top left in LTR, top right in RTL
        val ltr = region(LayoutDirection.Ltr)
        assertFalse(ltr.contains(2, 2))
        assertTrue(ltr.contains(197, 2))
        assertTrue(ltr.contains(2, 97))
        val rtl = region(LayoutDirection.Rtl)
        assertTrue(rtl.contains(2, 2))
        assertFalse(rtl.contains(197, 2))
    }

    @Test
    fun squareSidesStaySquare() {
        val path = (outline(SmoothCornerShape(24.dp, bottom = false), Size(200f, 60f)) as Outline.Generic).path.asAndroidPath()
        val region = Region().apply { setPath(path, Region(0, 0, 200, 60)) }
        assertFalse(region.contains(1, 1))
        assertTrue(region.contains(0, 59))
        assertTrue(region.contains(199, 59))
    }

    @Test
    fun equalShapesAreEqual() {
        assertEquals(SmoothCornerShape(12.dp), SmoothCornerShape(12.dp))
        assertEquals(SmoothCornerShape(12.dp).hashCode(), SmoothCornerShape(12.dp).hashCode())
    }
}
