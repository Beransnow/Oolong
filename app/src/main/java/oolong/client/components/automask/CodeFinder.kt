package oolong.client.components.automask

import android.graphics.Bitmap
import android.graphics.RectF
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.GenericMultipleBarcodeReader
import kotlin.math.max
import kotlin.math.min

/**
 * Finds the QR codes and barcodes in a picture that can be read, as boxes around each whole:
 * a card to add someone or pay them, a parcel's number.
 */
object CodeFinder {
    private val TwoD = setOf(BarcodeFormat.QR_CODE, BarcodeFormat.DATA_MATRIX, BarcodeFormat.AZTEC, BarcodeFormat.PDF_417)

    private val Hints = mapOf(
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.POSSIBLE_FORMATS to TwoD + listOf(
            BarcodeFormat.CODE_128,
            BarcodeFormat.CODE_39,
            BarcodeFormat.CODE_93,
            BarcodeFormat.EAN_13,
            BarcodeFormat.EAN_8,
            BarcodeFormat.ITF,
            BarcodeFormat.UPC_A,
            BarcodeFormat.CODABAR,
        ),
    )

    fun find(picture: Bitmap): List<RectF> {
        val w = picture.width
        val h = picture.height
        val pixels = IntArray(w * h).also { picture.getPixels(it, 0, w, 0, 0, w, h) }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(w, h, pixels)))
        val results = try {
            GenericMultipleBarcodeReader(MultiFormatReader()).decodeMultiple(bitmap, Hints)
        } catch (_: NotFoundException) {
            return emptyList()
        }
        return results.mapNotNull { result ->
            val points = result.resultPoints?.filterNotNull().orEmpty()
            if (points.isEmpty()) return@mapNotNull null
            var left = points.minOf { it.x }
            var right = points.maxOf { it.x }
            var top = points.minOf { it.y }
            var bottom = points.maxOf { it.y }
            if (result.barcodeFormat in TwoD) {
                // The points are the finder patterns' middles, some way in from the edges
                val grow = max(right - left, bottom - top) * 0.18f
                left -= grow
                top -= grow
                right += grow
                bottom += grow
            } else {
                // A barcode is found along one row: up and down as far as its bars run on
                val y = ((top + bottom) / 2).toInt().coerceIn(0, h - 1)
                val x0 = left.toInt().coerceIn(0, w - 1)
                val x1 = right.toInt().coerceIn(x0 + 1, w)
                val row = bars(pixels, w, y, x0, x1)
                var up = y
                while (up > 0 && same(row, bars(pixels, w, up - 1, x0, x1))) up--
                var down = y
                while (down < h - 1 && same(row, bars(pixels, w, down + 1, x0, x1))) down++
                val grow = (right - left) * 0.08f
                left -= grow
                right += grow
                top = up - grow
                bottom = down + grow
            }
            RectF(max(0f, left), max(0f, top), min(w.toFloat(), right), min(h.toFloat(), bottom))
        }
    }

    /** Which pixels of row [y] from [x0] until [x1] are dark. */
    private fun bars(pixels: IntArray, width: Int, y: Int, x0: Int, x1: Int) = BooleanArray(x1 - x0) { i ->
        val c = pixels[y * width + x0 + i]
        ((c shr 16) and 0xFF) + ((c shr 8) and 0xFF) + (c and 0xFF) < 3 * 128
    }

    private fun same(a: BooleanArray, b: BooleanArray): Boolean = a.indices.count { a[it] == b[it] } >= a.size * 0.9f
}
