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
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Finds what to hide where a picture is tapped: the line of text there, found by a text
 * detector, or else the smallest thing MobileSAM makes out around the tap.
 *
 * The models and the runtime are downloaded, by [AutoMaskFiles].
 */
class AutoMask internal constructor(
    private val detector: Interpreter,
    private val encoder: Interpreter,
    private val decoder: Interpreter,
    private val gaussian: FloatArray,
) {
    /** A picture made ready for taps: its lines of text and its embedding. */
    class Prepared(
        val width: Int,
        val height: Int,
        val lines: List<RectF>,
        internal val src: FloatBuffer,
        internal val scale: Float,
        val detectMillis: Long,
        val encodeMillis: Long,
    )

    /** What a tap found, in picture pixels; null when it found nothing small enough to be one thing. */
    class Found(val box: RectF?, val text: Boolean, val millis: Long)

    // The positional encoding of every cell of the embedding, made once
    private val pos: FloatBuffer = denseEncoding()

    fun prepare(picture: Bitmap): Prepared {
        val t0 = System.nanoTime()
        val lines = detect(picture)
        return prepare(picture, lines, (System.nanoTime() - t0) / 1_000_000)
    }

    /** Readies [picture] for taps, its [lines] of text found already by [detect]. */
    fun prepare(picture: Bitmap, lines: List<RectF>, detectMillis: Long = 0): Prepared {
        val t0 = System.nanoTime()
        val (src, scale) = encode(picture)
        return Prepared(picture.width, picture.height, lines, src, scale, detectMillis, (System.nanoTime() - t0) / 1_000_000)
    }

    fun close() {
        detector.close()
        encoder.close()
        decoder.close()
    }

    /**
     * Looks into a tap at [x], [y]; a line of text within [reach] of it counts as tapped, at least
     * about 20px at a 1080 width.
     */
    fun tap(prepared: Prepared, x: Float, y: Float, reach: Float = 0f): Found {
        val t0 = System.nanoTime()
        val reach = max(reach, 20f * prepared.width / 1080)
        fun gap(r: RectF) = max(max(r.left - x, 0f), x - r.right) + max(max(r.top - y, 0f), y - r.bottom)
        val line = prepared.lines.filter { gap(it) <= reach }.minByOrNull { gap(it) * 1e6f + it.width() * it.height() }
        val box = segment(prepared, x, y)?.takeIf { it.width() * it.height() <= MostOfPicture * prepared.width * prepared.height }
        if (line != null) {
            // "Lines" wholly inside something compact and not much bigger are likely parts of a
            // picture, as the rows of a group avatar's tiles: the whole of it is hidden instead
            val whole = box?.takeIf { b ->
                b.contains(line.inset(1f)) &&
                    max(b.width(), b.height()) <= MostCompact * min(b.width(), b.height()) &&
                    b.width() * b.height() <= LineInThing * line.width() * line.height() &&
                    prepared.lines.all { !RectF.intersects(it, b) || b.contains(it.inset(1f)) }
            }
            return Found(whole ?: line, text = whole == null, millis = (System.nanoTime() - t0) / 1_000_000)
        }
        return Found(box, text = false, millis = (System.nanoTime() - t0) / 1_000_000)
    }

    /** The lines of text a finger moving from [x0], [y0] to [x1], [y1] passes within [reach] of. */
    fun linesAlong(prepared: Prepared, x0: Float, y0: Float, x1: Float, y1: Float, reach: Float): List<RectF> {
        val step = max(reach / 2, 1f)
        val steps = (hypot(x1 - x0, y1 - y0) / step).toInt() + 1
        return prepared.lines.filter { line ->
            (0..steps).any { i ->
                val t = i.toFloat() / steps
                val x = x0 + (x1 - x0) * t
                val y = y0 + (y1 - y0) * t
                x >= line.left - reach && x <= line.right + reach && y >= line.top - reach && y <= line.bottom + reach
            }
        }
    }

    // Text detection

    /** The lines of text in [picture], in its pixels: quicker than the rest of preparing it. */
    fun detect(picture: Bitmap): List<RectF> {
        // Fitted into the detector's frame, the rest left at the mean
        val scale = min(DetWidth.toFloat() / picture.width, DetHeight.toFloat() / picture.height)
        val w = (picture.width * scale).roundToInt().coerceIn(1, DetWidth)
        val h = (picture.height * scale).roundToInt().coerceIn(1, DetHeight)
        val input = floats(DetWidth * DetHeight * 3)
        val pixels = IntArray(w * h).also { picture.scale(w, h).getPixels(it, 0, w, 0, 0, w, h) }
        for (y in 0 until h) {
            for (x in 0 until w) {
                val c = pixels[y * w + x]
                val i = (y * DetWidth + x) * 3
                input.put(i, (((c shr 16) and 0xFF) / 255f - 0.485f) / 0.229f)
                input.put(i + 1, (((c shr 8) and 0xFF) / 255f - 0.456f) / 0.224f)
                input.put(i + 2, ((c and 0xFF) / 255f - 0.406f) / 0.225f)
            }
        }
        val output = floats(DetWidth * DetHeight)
        detector.run(input, output)
        return lines(output, w, h).map { r -> RectF(r.left / scale, r.top / scale, r.right / scale, r.bottom / scale) }
    }

    /** The boxes of the text the probability map shows, grown back to the text's own size as DB's 'unclip' does. */
    private fun lines(map: FloatBuffer, w: Int, h: Int): List<RectF> {
        val on = BooleanArray(w * h) { map.get((it / w) * DetWidth + it % w) > 0.3f }
        val seen = BooleanArray(w * h)
        val stack = IntArray(w * h)
        val boxes = mutableListOf<RectF>()
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
            boxes += RectF(minX - grow, minY - grow, maxX + 1 + grow, maxY + 1 + grow)
        }
        return boxes
    }

    // MobileSAM

    private fun encode(picture: Bitmap): Pair<FloatBuffer, Float> {
        // The longer side to 1024, padded at the right and bottom
        val scale = SamSide.toFloat() / max(picture.width, picture.height)
        val w = (picture.width * scale).roundToInt().coerceIn(1, SamSide)
        val h = (picture.height * scale).roundToInt().coerceIn(1, SamSide)
        val input = floats(SamSide * SamSide * 3)
        val pixels = IntArray(w * h).also { picture.scale(w, h).getPixels(it, 0, w, 0, 0, w, h) }
        for (y in 0 until h) {
            for (x in 0 until w) {
                val c = pixels[y * w + x]
                val i = (y * SamSide + x) * 3
                input.put(i, (((c shr 16) and 0xFF) - 123.675f) / 58.395f)
                input.put(i + 1, (((c shr 8) and 0xFF) - 116.28f) / 57.12f)
                input.put(i + 2, ((c and 0xFF) - 103.53f) / 57.375f)
            }
        }
        val channelsFirst = floats(Channels * Cells * Cells)
        encoder.run(input, channelsFirst)
        // The decoder takes it channels last
        val src = floats(Cells * Cells * Channels)
        for (c in 0 until Channels) for (i in 0 until Cells * Cells) src.put(i * Channels + c, channelsFirst.get(c * Cells * Cells + i))
        return src to scale
    }

    private fun segment(prepared: Prepared, x: Float, y: Float): RectF? {
        val point = floats(2).apply {
            put(0, x * prepared.scale)
            put(1, y * prepared.scale)
        }
        val masks = floats(3 * MaskSide * MaskSide)
        val scores = floats(3)
        prepared.src.rewind()
        pos.rewind()
        val inputs = arrayOfNulls<Any>(3)
        inputs[decoder.getInputIndex("src")] = prepared.src
        inputs[decoder.getInputIndex("pos")] = pos
        inputs[decoder.getInputIndex("point")] = point
        decoder.runForMultipleInputsOutputs(inputs, mapOf(decoder.getOutputIndex("masks") to masks, decoder.getOutputIndex("scores") to scores))
        // Each of the three as its box, in mask cells, smallest first
        val levels = (0 until 3).mapNotNull { k ->
            var minX = MaskSide
            var minY = MaskSide
            var maxX = -1
            var maxY = -1
            var area = 0
            for (my in 0 until MaskSide) {
                for (mx in 0 until MaskSide) {
                    if (masks.get((k * MaskSide + my) * MaskSide + mx) <= 0f) continue
                    area++
                    minX = min(minX, mx)
                    maxX = max(maxX, mx)
                    minY = min(minY, my)
                    maxY = max(maxY, my)
                }
            }
            if (area == 0) null else Level(RectF(minX.toFloat(), minY.toFloat(), maxX + 1f, maxY + 1f), area, scores.get(k))
        }.sortedBy { it.area }
        // The smallest, unless what holds it is surer and not much bigger: a picture made of
        // pictures, as a group's avatar, is one thing, not its first tile
        var chosen = levels.firstOrNull() ?: return null
        for (next in levels.drop(1)) {
            if (next.score > chosen.score && next.area <= chosen.area * MostGrowth && next.box.holds(chosen.box)) chosen = next
        }
        // Back in picture pixels. The mask is coarse, a cell some pixels across, and leaves out
        // the cells only partly on the thing: grown by a cell and a little more, so all of it is hidden
        val f = SamSide.toFloat() / MaskSide / prepared.scale
        val box = RectF(chosen.box.left * f, chosen.box.top * f, chosen.box.right * f, chosen.box.bottom * f)
        val grow = f + max(box.width(), box.height()) * 0.03f
        return RectF(
            max(0f, box.left - grow),
            max(0f, box.top - grow),
            min(prepared.width.toFloat(), box.right + grow),
            min(prepared.height.toFloat(), box.bottom + grow),
        )
    }

    private fun RectF.inset(by: Float) = RectF(left + by, top + by, right - by, bottom - by)

    private class Level(val box: RectF, val area: Int, val score: Float)

    /** Whether this holds [other], give or take a cell. */
    private fun RectF.holds(other: RectF): Boolean =
        left <= other.left + 1 && top <= other.top + 1 && right >= other.right - 1 && bottom >= other.bottom - 1

    /** The decoder's positional encoding of each cell of the 64 by 64 embedding, channels last. */
    private fun denseEncoding(): FloatBuffer {
        val out = floats(Cells * Cells * Channels)
        val half = Channels / 2
        for (y in 0 until Cells) {
            for (x in 0 until Cells) {
                val cx = 2 * ((x + 0.5f) / Cells) - 1
                val cy = 2 * ((y + 0.5f) / Cells) - 1
                for (j in 0 until half) {
                    val p = 2 * PI.toFloat() * (cx * gaussian[j] + cy * gaussian[half + j])
                    val i = (y * Cells + x) * Channels
                    out.put(i + j, sin(p))
                    out.put(i + half + j, cos(p))
                }
            }
        }
        return out
    }

    companion object {
        private const val DetWidth = 768
        private const val DetHeight = 1664
        private const val SamSide = 1024
        private const val Cells = 64
        private const val Channels = 256
        private const val MaskSide = 256

        /** A tap's thing taking more of the picture than this is likely not one thing. */
        private const val MostOfPicture = 0.35f

        /**
         * How many times bigger than a line of text the thing holding it may be, and how much
         * longer than wide, to be hidden whole.
         */
        private const val LineInThing = 5
        private const val MostCompact = 2f

        /** How many times bigger than the smallest mask a surer one holding it may be, and still be taken. */
        private const val MostGrowth = 5

        /** Loads the models in [folder], the runtime already loaded. */
        internal fun load(folder: File, threads: Int): AutoMask {
            fun model(name: String): MappedByteBuffer = FileInputStream(File(folder, name)).use {
                it.channel.map(FileChannel.MapMode.READ_ONLY, 0, it.channel.size())
            }
            val options = Interpreter.Options().setNumThreads(threads)
            val gaussian = File(folder, AutoMaskFiles.Gaussian).readBytes()
                .let { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer() }
                .let { b -> FloatArray(b.remaining()).also { b.get(it) } }
            return AutoMask(
                Interpreter(model(AutoMaskFiles.Detector), options),
                Interpreter(model(AutoMaskFiles.Encoder), options),
                Interpreter(model(AutoMaskFiles.Decoder), options),
                // Stored 2 by 128: the x row, then the y row
                gaussian,
            )
        }

        private fun floats(count: Int): FloatBuffer = ByteBuffer.allocateDirect(count * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    }
}
