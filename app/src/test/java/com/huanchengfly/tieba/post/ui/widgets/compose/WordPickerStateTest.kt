package com.huanchengfly.tieba.post.ui.widgets.compose

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class WordPickerStateTest {
    private fun state(vararg pieces: String) =
        WordPickerState(pieces.map { WordPiece(it, isWord = it.any(Char::isLetterOrDigit)) })

    @Test
    fun `text is cut into words and what stands between them, losing nothing`() {
        val text = "第三章结尾那段剧透一下：主角其实没死！"
        val pieces = splitWords(text, Locale.SIMPLIFIED_CHINESE)

        assertEquals(text, pieces.joinToString("") { it.text })
        assertFalse(pieces.single { it.text == "：" }.isWord)
        assertTrue(pieces.count { it.isWord } > 3)
    }

    @Test
    fun `picked pieces next to each other make one word`() {
        val state = state("剧", "透", "一下", "，", "第三", "章")

        state.toggle(0)
        state.toggle(1)
        state.toggle(4)

        assertEquals(listOf("剧透", "第三"), state.words)
    }

    @Test
    fun `a slide picks the words between its ends and leaves the rest as they were`() {
        val state = state("甲", "，", "乙", "丙", "丁")
        state.toggle(4)
        val before = state.pieces.map { it.picked }

        state.set(0..2, picked = true, others = before)
        assertEquals(listOf(true, false, true, false, true), state.pieces.map { it.picked })

        state.set(0..0, picked = true, others = before)
        assertEquals(listOf("甲", "丁"), state.words)
    }

    @Test
    fun `a word splits into its characters, each picked as it was`() {
        val state = state("星穹铁道", "好玩")
        state.toggle(0)

        assertTrue(state.split(0))
        assertEquals(listOf("星", "穹", "铁", "道", "好玩"), state.pieces.map { it.text })
        assertTrue(state.pieces.take(4).all { it.picked && it.split })
        assertFalse(state.split(0))
    }
}
