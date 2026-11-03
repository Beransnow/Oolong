package oolong.client.repository

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class WordFinderTest {
    @Test
    fun `finds every word the text contains, nested ones too`() {
        val words = listOf("乙", "甲乙", "甲乙丙", "丙丁", "乙丙丁")
        val hits = WordFinder(words).find("x甲乙丙丁y")

        assertEquals(words.indices.toSet(), hits.stream().toArray().toSet())
    }

    @Test
    fun `finds what contains finds, for any text and words`() {
        val random = Random(11)
        val alphabet = "abcd"
        fun text(max: Int) = buildString { repeat(random.nextInt(1, max)) { append(alphabet.random(random)) } }
        repeat(500) {
            val words = List(random.nextInt(1, 12)) { text(5) }
            val finder = WordFinder(words)
            repeat(10) {
                val text = text(30)
                val expected = words.indices.filter { text.contains(words[it]) }.toSet()
                assertEquals(expected, finder.find(text).stream().toArray().toSet(), "$words in $text")
            }
        }
    }
}
