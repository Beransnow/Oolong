package com.huanchengfly.tieba.post.repository

import java.util.BitSet

/**
 * Finds which of [words] a text contains in one pass over it, however many words there are
 * (Aho–Corasick). A word's number is its index in [words].
 */
internal class WordFinder(words: List<String>) {
    // Each node's ways on, as characters in order and the nodes they lead to, searched by halves:
    // a map would box every character looked up
    private val keys: Array<CharArray>
    private val targets: Array<IntArray>
    private val fail: IntArray
    private val found: Array<IntArray>

    init {
        val next = mutableListOf(sortedMapOf<Char, Int>())
        val own = mutableListOf(mutableListOf<Int>())
        words.forEachIndexed { index, word ->
            var node = 0
            word.forEach { c ->
                node = next[node].getOrPut(c) {
                    next += sortedMapOf()
                    own += mutableListOf<Int>()
                    next.lastIndex
                }
            }
            own[node] += index
        }
        keys = Array(next.size) { next[it].keys.toCharArray() }
        targets = Array(next.size) { next[it].values.toIntArray() }
        fail = IntArray(next.size)
        val all = arrayOfNulls<IntArray>(next.size)
        all[0] = own[0].toIntArray()
        // Breadth first, so a node's fallback, which is shallower, is done before it
        val queue = ArrayDeque<Int>()
        targets[0].forEach { child ->
            all[child] = own[child].toIntArray()
            queue += child
        }
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            keys[node].forEachIndexed { i, c ->
                val child = targets[node][i]
                var back = fail[node]
                while (back != 0 && step(back, c) < 0) back = fail[back]
                fail[child] = step(back, c).takeIf { it >= 0 && it != child } ?: 0
                // A word ending here ends every shorter word that is a suffix of it too
                all[child] = own[child].toIntArray() + all[fail[child]]!!
                queue += child
            }
        }
        found = Array(next.size) { all[it]!! }
    }

    /** Where [c] leads from [node], or -1 for nowhere. */
    private fun step(node: Int, c: Char): Int {
        val at = keys[node].binarySearch(c)
        return if (at >= 0) targets[node][at] else -1
    }

    /** The numbers of the words [text] contains. */
    fun find(text: String): BitSet {
        val hits = BitSet()
        var node = 0
        for (c in text) {
            var to = step(node, c)
            while (to < 0 && node != 0) {
                node = fail[node]
                to = step(node, c)
            }
            node = if (to < 0) 0 else to
            found[node].forEach(hits::set)
        }
        return hits
    }
}
