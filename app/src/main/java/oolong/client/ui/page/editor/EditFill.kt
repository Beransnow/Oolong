package oolong.client.ui.page.editor

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min

/**
 * Fills the [hole] in [pixels], [width] by [height], from the colours around it, as smoothly as
 * they allow: as if nothing had been there. A plain or graded background comes back unbroken,
 * edges crossing the hole carry on across it; what has a texture comes out smooth.
 *
 * Each colour is solved for as the hole's own heat would settle with its edge held: first
 * roughly, by averaging down and back up, then closely, by passes over the hole.
 */
internal fun fillHole(pixels: IntArray, width: Int, height: Int, hole: BooleanArray) {
    val n = width * height
    val channels = Array(3) { c -> FloatArray(n) { ((pixels[it] shr (16 - 8 * c)) and 0xFF).toFloat() } }
    val known = FloatArray(n) { if (hole[it]) 0f else 1f }
    for (channel in channels) roughFill(channel, known, width, height)
    // The passes needed grow with how far into the hole its middle lies
    var minX = width
    var maxX = -1
    var minY = height
    var maxY = -1
    for (i in 0 until n) {
        if (!hole[i]) continue
        val x = i % width
        val y = i / width
        minX = min(minX, x)
        maxX = max(maxX, x)
        minY = min(minY, y)
        maxY = max(maxY, y)
    }
    if (maxX < 0) return
    val across = min(maxX - minX, maxY - minY) + 1
    val passes = (across * 1.2f).toInt().coerceIn(30, 160)
    for (channel in channels) settle(channel, hole, width, height, passes)
    for (i in 0 until n) {
        if (!hole[i]) continue
        val r = channels[0][i].toInt().coerceIn(0, 255)
        val g = channels[1][i].toInt().coerceIn(0, 255)
        val b = channels[2][i].toInt().coerceIn(0, 255)
        pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}

/** A first guess for the hole: what is known averaged down until it covers everything, then brought back up. */
private fun roughFill(values: FloatArray, known: FloatArray, width: Int, height: Int) {
    // Each level: colour times weight, and weight, at most 1
    val sums = mutableListOf(FloatArray(values.size) { values[it] * known[it] })
    val weights = mutableListOf(known.copyOf())
    val widths = mutableListOf(width)
    val heights = mutableListOf(height)
    while (widths.last() > 1 || heights.last() > 1) {
        val w = widths.last()
        val h = heights.last()
        val w2 = (w + 1) / 2
        val h2 = (h + 1) / 2
        val s = sums.last()
        val k = weights.last()
        val s2 = FloatArray(w2 * h2)
        val k2 = FloatArray(w2 * h2)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val j = (y / 2) * w2 + x / 2
                s2[j] += s[y * w + x]
                k2[j] += k[y * w + x]
            }
        }
        for (j in s2.indices) {
            val weight = k2[j]
            if (weight > 0f) {
                val capped = min(weight, 1f)
                s2[j] = s2[j] / weight * capped
                k2[j] = capped
            }
        }
        sums += s2
        weights += k2
        widths += w2
        heights += h2
    }
    // Back up: each level's own colour where it knows it, the coarser one's where it doesn't
    var colour = FloatArray(1) { if (weights.last()[0] > 0f) sums.last()[0] / weights.last()[0] else 0f }
    for (level in sums.size - 2 downTo 0) {
        val w = widths[level]
        val h = heights[level]
        val w2 = widths[level + 1]
        val s = sums[level]
        val k = weights[level]
        val next = FloatArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                // Colour times weight already, so the coarser colour makes up the rest
                next[i] = s[i] + colour[(y / 2) * w2 + x / 2] * (1f - k[i])
            }
        }
        colour = next
    }
    for (i in values.indices) if (known[i] == 0f) values[i] = colour[i]
}

/** Passes over the hole, each pixel taking a little past its neighbours' average, alternate pixels in turn. */
private fun settle(values: FloatArray, hole: BooleanArray, width: Int, height: Int, passes: Int) {
    val over = 1.9f
    repeat(passes) {
        for (parity in 0..1) {
            for (y in 0 until height) {
                var x = (y + parity) and 1
                while (x < width) {
                    val i = y * width + x
                    if (hole[i]) {
                        val left = if (x > 0) values[i - 1] else values[i]
                        val right = if (x < width - 1) values[i + 1] else values[i]
                        val up = if (y > 0) values[i - width] else values[i]
                        val down = if (y < height - 1) values[i + width] else values[i]
                        val average = (left + right + up + down) / 4f
                        values[i] += over * (average - values[i])
                    }
                    x += 2
                }
            }
        }
    }
}

/** A copy of [source] with the pixels [hole] marks filled from around them; [hole] is [source]'s size. */
internal fun filled(source: Bitmap, hole: BooleanArray): Bitmap {
    val w = source.width
    val h = source.height
    val pixels = IntArray(w * h)
    source.getPixels(pixels, 0, w, 0, 0, w, h)
    fillHole(pixels, w, h, hole)
    return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
}
