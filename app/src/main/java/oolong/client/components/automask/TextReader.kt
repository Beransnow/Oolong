package oolong.client.components.automask

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Reads lines of text off a picture with PP-OCR's recogniser: each line scaled to the model's
 * height and read a window at a time, each character placed across the line where the model
 * saw it.
 */
class TextReader internal constructor(private val model: Interpreter, private val characters: List<String>) {
    private val input = ByteBuffer.allocateDirect(Width * Height * 3 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private val output = ByteBuffer.allocateDirect(Steps * classes * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private val classes get() = characters.size

    /** Reads each of [lines], boxes in [picture]'s pixels; a line it cannot make out comes back empty. */
    fun read(picture: Bitmap, lines: List<RectF>): List<ReadLine> = lines.map { read(picture, it) }

    /** Reads the line [box] in [picture]'s pixels; one line at a time, as its buffers are shared. */
    @Synchronized
    fun read(picture: Bitmap, box: RectF): ReadLine {
        val left = max(0, box.left.roundToInt())
        val top = max(0, box.top.roundToInt())
        val right = min(picture.width, box.right.roundToInt())
        val bottom = min(picture.height, box.bottom.roundToInt())
        if (right - left < 2 || bottom - top < 2) return ReadLine("", emptyList(), box.left, box.top, box.right, box.bottom)
        val scale = Height.toFloat() / (bottom - top)
        val width = max(1, ((right - left) * scale).roundToInt())
        // The line at the model's height, as wide as that makes it
        val line = createBitmap(width, Height)
        Canvas(line).drawBitmap(picture, Rect(left, top, right, bottom), Rect(0, 0, width, Height), Paint(Paint.FILTER_BITMAP_FLAG))
        val pixels = IntArray(width * Height).also { line.getPixels(it, 0, width, 0, 0, width, Height) }
        val text = StringBuilder()
        val spans = mutableListOf<ClosedFloatingPointRange<Float>>()
        var start = 0
        // Characters whose middle falls in a window's own part, the overlap shared with the next
        var ownFrom = 0
        while (true) {
            val part = min(Width, width - start)
            input.clear()
            for (y in 0 until Height) {
                for (x in 0 until Width) {
                    if (x < part) {
                        val c = pixels[y * width + start + x]
                        input.put(((c shr 16) and 0xFF) / 127.5f - 1f)
                        input.put(((c shr 8) and 0xFF) / 127.5f - 1f)
                        input.put((c and 0xFF) / 127.5f - 1f)
                    } else {
                        input.put(0f)
                        input.put(0f)
                        input.put(0f)
                    }
                }
            }
            input.rewind()
            output.clear()
            model.run(input, output)
            val last = start + Width >= width
            val ownTo = if (last) width else start + Width - Overlap / 2
            var previous = 0
            var current = false
            for (t in 0 until Steps) {
                var best = 0
                var bestScore = Float.NEGATIVE_INFINITY
                val base = t * classes
                for (k in 0 until classes) {
                    val v = output.get(base + k)
                    if (v > bestScore) {
                        bestScore = v
                        best = k
                    }
                }
                val from = start + t * StepWidth
                if (best != 0 && best != previous) {
                    val middle = from + StepWidth / 2
                    current = middle in ownFrom until ownTo
                    if (current) {
                        text.append(characters[best])
                        spans += from.toFloat()..(from + StepWidth).toFloat()
                    }
                } else if (best != 0 && current) {
                    spans[spans.lastIndex] = spans.last().start..(from + StepWidth).toFloat()
                }
                if (best == 0) current = false
                previous = best
            }
            if (last) break
            ownFrom = start + Width - Overlap / 2
            start += Width - Overlap
        }
        return ReadLine(
            text.toString(),
            spans.map { (left + it.start / scale)..(left + it.endInclusive / scale) },
            left.toFloat(),
            top.toFloat(),
            right.toFloat(),
            bottom.toFloat(),
        )
    }

    fun close() = model.close()

    companion object {
        private const val Height = 48
        private const val Width = 640
        private const val Steps = 80
        private const val StepWidth = Width / Steps

        /** How much of a long line two windows read twice, so no character is cut in half. */
        private const val Overlap = 64

        /** Loads the model and its characters, the runtime already loaded. */
        internal fun load(model: File, dictionary: File, threads: Int): TextReader {
            val buffer = FileInputStream(model).use { it.channel.map(FileChannel.MapMode.READ_ONLY, 0, it.channel.size()) }
            // The model's classes: none, the dictionary's characters, and a space
            val characters = listOf("") + dictionary.readLines(Charsets.UTF_8) + " "
            return TextReader(Interpreter(buffer, Interpreter.Options().setNumThreads(threads)), characters)
        }
    }
}
