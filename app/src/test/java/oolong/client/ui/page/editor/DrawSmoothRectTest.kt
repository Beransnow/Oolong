package oolong.client.ui.page.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals

/** The saved picture draws filled captions, rectangle masks and boxes with smooth corners, where they were drawn. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DrawSmoothRectTest {
    private val rect = RectF(20f, 10f, 180f, 110f)

    private fun draw(style: Paint.Style): Bitmap = Bitmap.createBitmap(200, 120, Bitmap.Config.ARGB_8888).apply {
        Canvas(this).drawSmoothRect(
            rect,
            30f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                this.style = style
                strokeWidth = 4f
            },
        )
    }

    private fun Bitmap.alphaAt(x: Int, y: Int) = Color.alpha(getPixel(x, y))

    @Test
    fun fillsTheRectangleItIsGiven() {
        val bitmap = draw(Paint.Style.FILL)
        assertEquals(255, bitmap.alphaAt(100, 60))
        // Up to each side, in the middle of it
        assertEquals(255, bitmap.alphaAt(100, 11))
        assertEquals(255, bitmap.alphaAt(100, 108))
        assertEquals(255, bitmap.alphaAt(21, 60))
        assertEquals(255, bitmap.alphaAt(178, 60))
        // And no further
        assertEquals(0, bitmap.alphaAt(100, 8))
        assertEquals(0, bitmap.alphaAt(100, 111))
        assertEquals(0, bitmap.alphaAt(18, 60))
        assertEquals(0, bitmap.alphaAt(181, 60))
    }

    @Test
    fun roundsEveryCorner() {
        val bitmap = draw(Paint.Style.FILL)
        assertEquals(0, bitmap.alphaAt(21, 11))
        assertEquals(0, bitmap.alphaAt(178, 11))
        assertEquals(0, bitmap.alphaAt(21, 108))
        assertEquals(0, bitmap.alphaAt(178, 108))
        // Each corner is in where a square one would be, but the shape is whole inside it
        assertEquals(255, bitmap.alphaAt(35, 25))
        assertEquals(255, bitmap.alphaAt(164, 95))
    }

    @Test
    fun outlinesTheSameShape() {
        val bitmap = draw(Paint.Style.STROKE)
        // On the edge, not inside it
        assertEquals(255, bitmap.alphaAt(100, 10))
        assertEquals(0, bitmap.alphaAt(100, 60))
        assertEquals(0, bitmap.alphaAt(21, 11))
    }
}
