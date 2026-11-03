package oolong.client.ui.common.theme.compose

import android.graphics.Region
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
    fun equalShapesAreEqual() {
        assertEquals(SmoothCornerShape(12.dp), SmoothCornerShape(12.dp))
        assertEquals(SmoothCornerShape(12.dp).hashCode(), SmoothCornerShape(12.dp).hashCode())
    }
}
