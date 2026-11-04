package oolong.client.components.automask

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The text read off a picture, laid out to be read and picked from: its lines in reading order,
 * and every character in that order with the box it takes, so a selection runs from one
 * character to another as a reader would, across lines.
 */
class PictureText(lines: List<ReadLine>) {
    /** A character and the box it takes in the picture, in pixels. */
    class Glyph(val text: String, val line: Int, val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val centerX: Float get() = (left + right) / 2
        val centerY: Float get() = (top + bottom) / 2
    }

    /** The lines read, in reading order: rows top to bottom, each row's lines left to right. */
    val lines: List<ReadLine> = order(lines.filter { it.text.isNotBlank() && it.spans.size == it.chars.size })

    /** Every character, in reading order. */
    val glyphs: List<Glyph> = this.lines.flatMapIndexed { index, line -> glyphsOf(line, index) }

    /** What goes between line [i] and the one after it: nothing, a space or a new line. */
    private val joins: List<String> = this.lines.zipWithNext { a, b -> join(a, b) }

    val isEmpty: Boolean get() = glyphs.isEmpty()

    /** The characters [from] to [to], both included, as text: lines of a paragraph run on, paragraphs on their own lines. */
    fun text(from: Int = 0, to: Int = glyphs.lastIndex): String {
        if (glyphs.isEmpty() || from > to) return ""
        val out = StringBuilder()
        for (i in from..to) {
            if (i > from && glyphs[i].line != glyphs[i - 1].line) out.append(joins[glyphs[i - 1].line])
            out.append(glyphs[i].text)
        }
        return out.toString()
    }

    /** The character nearest [x], [y], within [reach] of its line; null when no line is that near. */
    fun glyphAt(x: Float, y: Float, reach: Float = 0f): Int? {
        val line = lines.indices.minByOrNull { gap(lines[it], x, y) }?.takeIf { gap(lines[it], x, y) <= reach } ?: return null
        return glyphs.indices.filter { glyphs[it].line == line }.minByOrNull { abs(glyphs[it].centerX - x) }
    }

    /** The character nearest [x], [y] wherever it is: a handle dragged anywhere picks the one it is nearest. */
    fun nearestGlyph(x: Float, y: Float): Int? {
        if (glyphs.isEmpty()) return null
        // The nearest line by height first, then the nearest character along it
        val line = lines.indices.minBy { abs((lines[it].top + lines[it].bottom) / 2 - y) + max(0f, max(lines[it].left - x, x - lines[it].right)) * 0.25f }
        return glyphs.indices.filter { glyphs[it].line == line }.minBy { abs(glyphs[it].centerX - x) }
    }

    /** The first and last characters of the line [glyph] is on. */
    fun lineOf(glyph: Int): IntRange {
        val line = glyphs[glyph].line
        val first = glyphs.indexOfFirst { it.line == line }
        val last = glyphs.indexOfLast { it.line == line }
        return first..last
    }

    private fun gap(line: ReadLine, x: Float, y: Float): Float =
        max(max(line.left - x, 0f), x - line.right) + max(max(line.top - y, 0f), y - line.bottom)

    companion object {
        /** Lines into rows, each sharing at least half the height of the smaller, then by row and from the left. */
        internal fun order(lines: List<ReadLine>): List<ReadLine> {
            val rows = mutableListOf<MutableList<ReadLine>>()
            for (line in lines.sortedBy { it.top }) {
                val row = rows.lastOrNull()?.takeIf { row -> row.any { sharesRow(it, line) } }
                if (row != null) row += line else rows += mutableListOf(line)
            }
            return rows.flatMap { row -> row.sortedBy { it.left } }
        }

        private fun sharesRow(a: ReadLine, b: ReadLine): Boolean =
            min(a.bottom, b.bottom) - max(a.top, b.top) >= 0.5f * min(a.height, b.height)

        /**
         * What joins line [a] to [b] after it: a space beside it on its row; nothing, or a space
         * between two Latin words, when [b] carries on its paragraph just below, starting about
         * where it does; otherwise a new line.
         */
        internal fun join(a: ReadLine, b: ReadLine): String {
            if (sharesRow(a, b)) return " "
            val height = min(a.height, b.height)
            val below = b.top - a.bottom <= 0.8f * height && b.top >= a.top
            val aligned = abs(b.left - a.left) <= height
            if (!below || !aligned) return "\n"
            val end = a.text.trimEnd().lastOrNull()
            val start = b.text.trimStart().firstOrNull()
            return if (end != null && start != null && end.isLatin() && start.isLatin()) " " else ""
        }

        private fun Char.isLatin() = this.code < 0x2E80 && (isLetterOrDigit() || this in ",.;:!?)")

        /**
         * Each character's box: from halfway to the one before to halfway to the one after, the
         * first and last reaching the line's ends, so they meet with no gap between.
         */
        private fun glyphsOf(line: ReadLine, index: Int): List<Glyph> {
            val spans = line.spans
            return line.chars.indices.map { i ->
                val left = if (i == 0) line.left else (spans[i - 1].endInclusive + spans[i].start) / 2
                val right = if (i == spans.lastIndex) line.right else (spans[i].endInclusive + spans[i + 1].start) / 2
                Glyph(line.chars[i], index, left, line.top, max(right, left), line.bottom)
            }
        }
    }
}
