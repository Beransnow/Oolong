package oolong.client.components.automask

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.core.graphics.scale
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Finds the lines of text in a picture with PP-OCR's detector, as boxes in its pixels. A picture
 * much taller than the detector's frame, as a long screenshot, is looked at in overlapping parts
 * at its full width, so its text is not shrunk past reading.
 */
class TextFinder internal constructor(private val detector: Interpreter) {
    private val input = floats(DetWidth * DetHeight * 3)
    private val output = floats(DetWidth * DetHeight)

    /** The lines of text in [picture]; one picture at a time. */
    @Synchronized
    fun find(picture: Bitmap): List<RectF> {
        val byWidth = DetWidth.toFloat() / picture.width
        if (picture.height * byWidth <= DetHeight * MostSquash) {
            // Fitted into the frame whole, the rest left at the mean
            val scale = min(byWidth, DetHeight.toFloat() / picture.height)
            return look(picture, 0, picture.height, scale).map { it.toRect() }
        }
        // In parts as tall as the frame at the full width, each overlapping the next
        val part = (DetHeight / byWidth).toInt()
        val overlap = (Overlap / byWidth).toInt()
        val parts = mutableListOf<Part>()
        var top = 0
        while (true) {
            val bottom = min(top + part, picture.height)
            parts += Part(top, bottom, look(picture, top, bottom, byWidth))
            if (bottom >= picture.height) break
            top = bottom - overlap
        }
        return merge(parts, picture.height, edge = 2 / byWidth).map { it.toRect() }
    }

    /** The lines in [picture]'s rows [top] until [bottom], at [scale], in picture pixels. */
    private fun look(picture: Bitmap, top: Int, bottom: Int, scale: Float): List<Box> {
        val w = (picture.width * scale).roundToInt().coerceIn(1, DetWidth)
        val h = ((bottom - top) * scale).roundToInt().coerceIn(1, DetHeight)
        val piece = if (top == 0 && bottom == picture.height) picture else Bitmap.createBitmap(picture, 0, top, picture.width, bottom - top)
        val pixels = IntArray(w * h).also { piece.scale(w, h).getPixels(it, 0, w, 0, 0, w, h) }
        // The frame past the picture at the mean: zero once normalised
        for (i in 0 until DetWidth * DetHeight * 3) input.put(i, 0f)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val c = pixels[y * w + x]
                val i = (y * DetWidth + x) * 3
                input.put(i, (((c shr 16) and 0xFF) / 255f - 0.485f) / 0.229f)
                input.put(i + 1, (((c shr 8) and 0xFF) / 255f - 0.456f) / 0.224f)
                input.put(i + 2, ((c and 0xFF) / 255f - 0.406f) / 0.225f)
            }
        }
        input.rewind()
        output.rewind()
        detector.run(input, output)
        return lines(output, w, h).map { r -> Box(r.left / scale, top + r.top / scale, r.right / scale, top + r.bottom / scale) }
    }

    /** The boxes of the text the probability map shows, grown back to the text's own size as DB's 'unclip' does. */
    private fun lines(map: FloatBuffer, w: Int, h: Int): List<Box> {
        val on = BooleanArray(w * h) { map.get((it / w) * DetWidth + it % w) > 0.3f }
        val seen = BooleanArray(w * h)
        val stack = IntArray(w * h)
        val boxes = mutableListOf<Box>()
        for (start in on.indices) {
            if (!on[start] || seen[start]) continue
            var top = 0
            stack[top++] = start
            seen[start] = true
            var minX = w
            var minY = h
            var maxX = 0
            var maxY = 0
            var sum = 0f
            var count = 0
            while (top > 0) {
                val p = stack[--top]
                val px = p % w
                val py = p / w
                minX = min(minX, px)
                maxX = max(maxX, px)
                minY = min(minY, py)
                maxY = max(maxY, py)
                sum += map.get(py * DetWidth + px)
                count++
                for (n in intArrayOf(p - 1, p + 1, p - w, p + w)) {
                    if (n < 0 || n >= on.size || seen[n] || !on[n]) continue
                    if ((n == p - 1 && px == 0) || (n == p + 1 && px == w - 1)) continue
                    seen[n] = true
                    stack[top++] = n
                }
            }
            val bw = maxX - minX + 1f
            val bh = maxY - minY + 1f
            if (min(bw, bh) < 3 || sum / count < 0.6f) continue
            val grow = bw * bh * 1.5f / (2 * (bw + bh))
            boxes += Box(minX - grow, minY - grow, maxX + 1 + grow, maxY + 1 + grow)
        }
        return boxes
    }

    fun close() = detector.close()

    /** A box in picture pixels. */
    internal data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val height: Float get() = bottom - top
        fun toRect() = RectF(left, top, right, bottom)
    }

    /** What one part of a picture, rows [top] until [bottom], showed. */
    internal class Part(val top: Int, val bottom: Int, val boxes: List<Box>)

    companion object {
        private const val DetWidth = 768
        private const val DetHeight = 1664

        /** How much taller than the frame a picture may be and still be looked at whole, shrunk to fit. */
        private const val MostSquash = 1.25f

        /** How much each part shares with the next, in the frame's pixels: more than a line of text is tall. */
        private const val Overlap = 160

        /**
         * The lines of every part as one: a line touching a part's edge where another part
         * carries on is cut off there, and the other part has the whole of it; one found in
         * both parts is kept once.
         */
        internal fun merge(parts: List<Part>, height: Int, edge: Float): List<Box> {
            val kept = parts.flatMap { part ->
                part.boxes.filter { box ->
                    val cutAbove = part.top > 0 && box.top <= part.top + edge
                    val cutBelow = part.bottom < height && box.bottom >= part.bottom - edge
                    !cutAbove && !cutBelow
                }
            }
            val out = mutableListOf<Box>()
            for (box in kept.sortedByDescending { (it.right - it.left) * it.height }) {
                if (out.none { overlapShare(it, box) > 0.5f }) out += box
            }
            return out.sortedWith(compareBy({ it.top }, { it.left }))
        }

        /** How much of the smaller of [a] and [b] the two share. */
        private fun overlapShare(a: Box, b: Box): Float {
            val w = min(a.right, b.right) - max(a.left, b.left)
            val h = min(a.bottom, b.bottom) - max(a.top, b.top)
            if (w <= 0 || h <= 0) return 0f
            val smaller = min((a.right - a.left) * a.height, (b.right - b.left) * b.height)
            return w * h / smaller
        }

        internal fun load(model: File, threads: Int): TextFinder {
            val buffer = FileInputStream(model).use { it.channel.map(FileChannel.MapMode.READ_ONLY, 0, it.channel.size()) }
            return TextFinder(Interpreter(buffer, Interpreter.Options().setNumThreads(threads)))
        }

        private fun floats(count: Int): FloatBuffer = ByteBuffer.allocateDirect(count * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    }
}
