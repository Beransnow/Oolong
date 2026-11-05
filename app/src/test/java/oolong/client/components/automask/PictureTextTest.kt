package oolong.client.components.automask

import org.junit.Assert.assertEquals
import org.junit.Test

class PictureTextTest {
    /** A line of [text] with its top at [top], 30 tall, each character 10 wide from [left]. */
    private fun line(text: String, top: Float, left: Float = 0f) =
        ReadLine(text, text.indices.map { (left + it * 10f + 2f)..(left + it * 10f + 8f) }, left, top, left + text.length * 10f, top + 30f)

    @Test
    fun linesReadByRowThenFromTheLeft() {
        val text = PictureText(listOf(line("右边", 2f, left = 500f), line("第二行", 40f), line("左边", 0f)))
        assertEquals(listOf("左边", "右边", "第二行"), text.lines.map { it.text })
        assertEquals("左边 右边\n第二行", text.text())
    }

    @Test
    fun aParagraphRunsOnAcrossItsLines() {
        // Just below, starting where it does: the same paragraph
        val text = PictureText(listOf(line("今天天气很好，我们", 0f), line("一起去公园。", 34f), line("另一段", 120f)))
        assertEquals("今天天气很好，我们一起去公园。\n另一段", text.text())
        // Latin words keep their space
        assertEquals("hello world", PictureText(listOf(line("hello", 0f), line("world", 34f))).text())
    }

    @Test
    fun aSelectionRunsAcrossLinesInReadingOrder() {
        val text = PictureText(listOf(line("abc", 0f), line("def", 100f)))
        assertEquals("c\nde", text.text(2, 4))
        assertEquals(1, text.glyphAt(15f, 15f))
        assertEquals(3..5, text.lineOf(4))
    }

    @Test
    fun glyphsMeetWithNoGap() {
        val glyphs = PictureText(listOf(line("abcd", 0f))).glyphs
        glyphs.zipWithNext { a, b -> assertEquals(a.right, b.left, 0.001f) }
        assertEquals(0f, glyphs.first().left, 0f)
        assertEquals(40f, glyphs.last().right, 0f)
    }

    @Test
    fun chineseAndLatinAreSpacedApart() {
        assertEquals("与 Google 服务器通信，共 3 次", PictureText(listOf(line("与Google服务器通信，共3次", 0f))).text())
        // A space read already is not doubled, punctuation is left as it is
        assertEquals("用 iPhone 拍的：OK", PictureText(listOf(line("用 iPhone拍的：OK", 0f))).text())
        // Nor beside an emoji
        assertEquals("ok\uD83D\uDE00", PictureText(listOf(ReadLine("ok\uD83D\uDE00", listOf(0f..10f, 10f..20f, 20f..30f), 0f, 0f, 30f, 30f))).text())
    }

    @Test
    fun aRareCharacterKeepsItsLine() {
        // 𠀀 takes two places in the string but is one character read, with one span
        val rare = "a\uD840\uDC00b"
        val line = ReadLine(rare, listOf(0f..10f, 10f..20f, 20f..30f), 0f, 0f, 30f, 30f)
        val text = PictureText(listOf(line))
        assertEquals(3, text.glyphs.size)
        // A Chinese character all the same, spaced from the letters
        assertEquals("a \uD840\uDC00 b", text.text())
        assertEquals(20f..30f, line.spanAt(3))
    }

    @Test
    fun partsOfATallPictureMergeIntoItsLines() {
        val box = { top: Float, bottom: Float -> TextFinder.Box(10f, top, 300f, bottom) }
        val parts = listOf(
            // The second line is cut by the first part's lower edge
            TextFinder.Part(0, 1000, listOf(box(100f, 130f), box(950f, 975f), box(980f, 1000f))),
            // ...and whole in the second, which also finds the first part's last whole line again
            TextFinder.Part(900, 1900, listOf(box(950f, 975f), box(980f, 1010f), box(1500f, 1530f))),
        )
        val merged = TextFinder.merge(parts, height = 1900, edge = 2f)
        assertEquals(listOf(100f, 950f, 980f, 1500f), merged.map { it.top })
        assertEquals(1010f, merged[2].bottom, 0f)
    }
}
