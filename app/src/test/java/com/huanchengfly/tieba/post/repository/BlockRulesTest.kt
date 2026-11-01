package com.huanchengfly.tieba.post.repository

import com.huanchengfly.tieba.post.model.User
import com.huanchengfly.tieba.post.models.database.Block
import com.huanchengfly.tieba.post.toJson
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlockRulesTest {
    private val someone = User(id = 7, name = "someone", nameShow = "", portrait = "")
    private val nameless = User(id = 0, name = "", nameShow = "", portrait = "")

    private fun keywords(category: Int, vararg words: String) =
        Block(category = category, type = Block.TYPE_KEYWORD, keywords = words.toList().toJson())

    private fun user(category: Int, uid: String? = null, name: String? = null) =
        Block(category = category, type = Block.TYPE_USER, uid = uid, username = name)

    @Test
    fun `a keyword rule hides content containing all of its keywords`() {
        val rules = blockRulesOf(listOf(keywords(Block.CATEGORY_BLACK_LIST, "广告", "加群")))

        assertTrue(rules.blocks(someone, "广告：快来加群"))
        assertFalse(rules.blocks(someone, "这不是广告"))
    }

    @Test
    fun `an allow keyword rule keeps content a block keyword rule would hide`() {
        val rules = blockRulesOf(
            listOf(keywords(Block.CATEGORY_BLACK_LIST, "广告"), keywords(Block.CATEGORY_WHITE_LIST, "吧务"))
        )

        assertTrue(rules.blocks(someone, "广告"))
        assertFalse(rules.blocks(someone, "吧务发的广告"))
    }

    @Test
    fun `a user rule hides the user by id or by name`() {
        assertTrue(blockRulesOf(listOf(user(Block.CATEGORY_BLACK_LIST, uid = "7"))).blocks(someone, ""))
        assertTrue(blockRulesOf(listOf(user(Block.CATEGORY_BLACK_LIST, name = "someone"))).blocks(someone, ""))
    }

    @Test
    fun `a user rule never matches a missing id or a blank name`() {
        val rules = blockRulesOf(listOf(user(Block.CATEGORY_BLACK_LIST, uid = "0", name = "")))

        assertFalse(rules.blocks(nameless, "内容"))
    }

    @Test
    fun `keywords and users are judged apart`() {
        val rules = blockRulesOf(
            listOf(keywords(Block.CATEGORY_BLACK_LIST, "广告"), user(Block.CATEGORY_WHITE_LIST, uid = "7"))
        )

        // Allowing the author does not keep a post that a keyword hides
        assertTrue(rules.blocks(someone, "广告"))
    }
}
