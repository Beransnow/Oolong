package com.huanchengfly.tieba.post.repository

import kotlin.test.Test
import kotlin.test.assertEquals

class BlockMemoTest {
    private class Item(val name: String)

    @Test
    fun `only items not seen under the same key are checked`() {
        val memo = BlockMemo<Item, String>()
        val checked = mutableListOf<String>()
        fun map(key: Any, items: List<Item>) = memo.map(key, items) {
            checked += it.name
            it.name.uppercase()
        }
        val a = Item("a")
        val b = Item("b")

        assertEquals(listOf("A"), map(1, listOf(a)))
        assertEquals(listOf("A", "B"), map(1, listOf(a, b)))
        // A changed item is a new one
        map(1, listOf(Item("a"), b))
        map(2, listOf(b))

        assertEquals(listOf("a", "b", "a", "b"), checked)
    }
}
