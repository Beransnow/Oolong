package oolong.client.components.images

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UploadPictureTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun write(bitmap: Bitmap, format: Bitmap.CompressFormat, name: String): File =
        folder.newFile(name).also { file -> file.outputStream().use { bitmap.compress(format, 95, it) } }

    private fun halves(width: Int, height: Int): Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
        Canvas(this).apply {
            drawColor(Color.RED)
            drawRect(width / 2f, 0f, width.toFloat(), height.toFloat(), Paint().apply { color = Color.BLUE })
        }
    }

    @Test
    fun `a small upright JPEG goes as it is, without where it was taken`() {
        val source = write(halves(400, 200), Bitmap.CompressFormat.JPEG, "a.jpg")
        ExifInterface(source).apply {
            setLatLong(31.2, 121.5)
            setAttribute(ExifInterface.TAG_MODEL, "Phone")
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            saveAttributes()
        }
        val picture = preparePictureForUpload(source, folder.newFile(), maxBytes = 5 shl 20, maxPixels = 16_000_000)

        assertEquals(400 to 200, picture.width to picture.height)
        val exif = ExifInterface(picture.file)
        assertNull(exif.latLong)
        assertNull(exif.getAttribute(ExifInterface.TAG_MODEL))
        // Not drawn again: the same pixels
        val before = BitmapFactory.decodeFile(source.path)
        val after = BitmapFactory.decodeFile(picture.file.path)
        assertEquals(before.getPixel(10, 10), after.getPixel(10, 10))
        assertEquals(before.getPixel(390, 190), after.getPixel(390, 190))
    }

    @Test
    fun `a picture turned by its EXIF is drawn again upright`() {
        val source = write(halves(400, 200), Bitmap.CompressFormat.JPEG, "turned.jpg")
        ExifInterface(source).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        val picture = preparePictureForUpload(source, folder.newFile(), maxBytes = 5 shl 20, maxPixels = 16_000_000)

        assertEquals(200 to 400, picture.width to picture.height)
        val bitmap = BitmapFactory.decodeFile(picture.file.path)
        assertEquals(200 to 400, bitmap.width to bitmap.height)
        // Turned a quarter clockwise, the left half, red, comes to the top
        assertTrue(Color.red(bitmap.getPixel(100, 50)) > 200 && Color.blue(bitmap.getPixel(100, 50)) < 60)
        assertTrue(Color.blue(bitmap.getPixel(100, 350)) > 200 && Color.red(bitmap.getPixel(100, 350)) < 60)
    }

    @Test
    fun `a picture too large is drawn again smaller, its lines averaged in linear light`() {
        val width = 1080
        val height = 20_000
        val stripes = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
            val paint = Paint().apply { color = Color.BLACK }
            val canvas = Canvas(this)
            // One black row in ten, as lines of text
            for (y in 0 until height step 10) canvas.drawRect(0f, y.toFloat(), width.toFloat(), y + 1f, paint)
        }
        val source = write(stripes, Bitmap.CompressFormat.PNG, "long.png")
        // Just over the limit, so it is drawn again, a tenth as wide: each pixel then covers ten rows
        val maxBytes = source.length() - 1
        val picture = preparePictureForUpload(source, folder.newFile(), maxBytes = maxBytes, maxPixels = width * height / 100L)

        assertTrue(picture.file.length() <= maxBytes)
        assertEquals(108 to 2000, picture.width to picture.height)
        assertEquals(width.toDouble() / height, picture.width.toDouble() / picture.height, 0.002)
        val bitmap = BitmapFactory.decodeFile(picture.file.path)
        // A tenth black in linear light is about 243 in sRGB; averaging sRGB values would give 230
        val grey = Color.red(bitmap.getPixel(bitmap.width / 2, bitmap.height / 2))
        assertTrue(grey in 238..248, "grey was $grey")
    }

    @Test
    fun `formats are told by their first bytes`() {
        assertEquals(PictureFormat.Jpeg, PictureFormat.of(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0)))
        assertEquals(PictureFormat.Png, PictureFormat.of(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)))
        assertEquals(PictureFormat.Gif, PictureFormat.of("GIF89a".toByteArray()))
        assertEquals(PictureFormat.WebP, PictureFormat.of("RIFF\u0000\u0000\u0000\u0000WEBP".toByteArray()))
        assertEquals(PictureFormat.Heif, PictureFormat.of("\u0000\u0000\u0000\u0018ftypheic".toByteArray()))
        assertEquals(PictureFormat.Avif, PictureFormat.of("\u0000\u0000\u0000\u001cftypavif".toByteArray()))
        assertNull(PictureFormat.of("hello".toByteArray()))
    }
}
