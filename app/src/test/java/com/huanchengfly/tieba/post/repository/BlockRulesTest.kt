package com.huanchengfly.tieba.post.repository

import com.huanchengfly.tieba.post.model.BlockRule
import com.huanchengfly.tieba.post.model.BlockRule.Match
import com.huanchengfly.tieba.post.model.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlockRulesTest {
    private val someone = User(id = 7, name = "someone", nameShow = "", portrait = "", level = 5)
    private val nameless = User(id = 0, name = "", nameShow = "", portrait = "")

    private fun item(
        content: String = "",
        author: User = someone,
        place: BlockPlace = BlockPlace.Thread,
        title: String = "",
        forum: String? = "吧名",
    ) = Blockable(author, content, place, title, forum)

    private fun keyword(vararg words: String, regex: Boolean = false) = BlockRule(match = Match.Keyword(words.toList(), regex))

    @Test
    fun `a keyword rule blocks content containing all of its keywords`() {
        val rules = blockRulesOf(listOf(keyword("广告", "加群")))

        assertTrue(rules.blocks(someone, "广告：快来加群"))
        assertFalse(rules.blocks(someone, "这不是广告"))
    }

    @Test
    fun `keywords ignore case, full width forms and what is put between their characters`() {
        val rules = blockRulesOf(listOf(keyword("剧透"), keyword("ABC")))

        assertTrue(rules.blocks(someone, "第三章剧 透一下"))
        assertTrue(rules.blocks(someone, "剧·透"))
        assertTrue(rules.blocks(someone, "ａｂｃ"))
    }

    @Test
    fun `a regex rule matches its pattern and a broken pattern matches nothing`() {
        val rules = blockRulesOf(listOf(keyword("互关|互粉", regex = true)))

        assertTrue(rules.blocks(someone, "求互粉"))
        assertFalse(rules.blocks(someone, "互相关注"))
        assertFalse(blockRulesOf(listOf(keyword("(", regex = true))).blocks(someone, "("))
    }

    @Test
    fun `an exception shows content whatever else matches`() {
        val rules = blockRulesOf(
            listOf(keyword("广告"), BlockRule(match = Match.User(7, "someone"), action = BlockRule.Action.Allow)),
        )

        assertFalse(rules.blocks(someone, "广告"))
        assertTrue(rules.blocks(someone.copy(id = 8, name = "other"), "广告"))
    }

    @Test
    fun `a user rule matches by id or by name, never a missing id or a blank name`() {
        assertTrue(blockRulesOf(listOf(BlockRule(match = Match.User(7, "")))).blocks(someone, ""))
        assertTrue(blockRulesOf(listOf(BlockRule(match = Match.User(0, "someone")))).blocks(someone, ""))
        assertFalse(blockRulesOf(listOf(BlockRule(match = Match.User(0, "")))).blocks(nameless, "内容"))
    }

    @Test
    fun `a forum rule matches its forum with or without 吧, except in the forum itself`() {
        val rules = blockRulesOf(listOf(BlockRule(match = Match.Forum("抽卡吧"))))

        assertTrue(rules.check(item(forum = "抽卡")) != null)
        assertNull(rules.check(item(forum = "抽卡", place = BlockPlace.Forum)))
        assertNull(rules.check(item(forum = "原神")))
        assertNull(rules.check(item(forum = null)))
    }

    @Test
    fun `a rule applies only where its scope says`() {
        val rules = blockRulesOf(listOf(keyword("广告").copy(scope = BlockRule.Scope.Feeds)))

        assertTrue(rules.check(item("广告", place = BlockPlace.Feeds)) != null)
        assertNull(rules.check(item("广告", place = BlockPlace.Thread)))
    }

    @Test
    fun `conditions narrow a rule to a forum and to titles`() {
        val inForum = blockRulesOf(listOf(keyword("原神").copy(inForum = "星穹铁道")))
        assertTrue(inForum.check(item("原神", forum = "星穹铁道吧")) != null)
        assertNull(inForum.check(item("原神", forum = "原神")))

        val titleOnly = blockRulesOf(listOf(keyword("剧透").copy(titleOnly = true)))
        assertTrue(titleOnly.check(item("正文", title = "剧透预警")) != null)
        assertNull(titleOnly.check(item("剧透", title = "标题")))
    }

    @Test
    fun `disabled and expired rules do nothing`() {
        val rules = blockRulesOf(
            listOf(keyword("甲").copy(enabled = false), keyword("乙").copy(expiresAt = 1_000)),
            now = 2_000,
        )

        assertFalse(rules.blocks(someone, "甲乙"))
        assertTrue(blockRulesOf(listOf(keyword("乙").copy(expiresAt = 3_000)), now = 2_000).blocks(someone, "乙"))
    }

    @Test
    fun `content is folded or hidden as the rule or the setting says`() {
        val rule = keyword("广告")

        assertEquals(false, blockRulesOf(listOf(rule)).check(item("广告"))?.hidden)
        assertEquals(true, blockRulesOf(listOf(rule), BlockOptions(hideByDefault = true)).check(item("广告"))?.hidden)
        assertEquals(false, blockRulesOf(listOf(rule.copy(action = BlockRule.Action.Fold)), BlockOptions(hideByDefault = true)).check(item("广告"))?.hidden)
        assertEquals(true, blockRulesOf(listOf(rule.copy(action = BlockRule.Action.Hide))).check(item("广告"))?.hidden)
    }

    @Test
    fun `the low level setting blocks users below it whose level is known`() {
        val rules = blockRulesOf(emptyList(), BlockOptions(lowLevel = 6))

        assertTrue(rules.check(item(author = someone)) != null)
        assertNull(rules.check(item(author = someone.copy(level = 6))))
        assertNull(rules.check(item(author = someone.copy(level = 0))))
    }

    @Test
    fun `each block is reported with its rule`() {
        val hits = mutableListOf<String>()
        val rules = blockRulesOf(listOf(keyword("广告").copy(id = 3)), onHit = { rule, item -> hits += "${rule.id}:${item.content}" })

        rules.check(item("广告"))
        rules.check(item("正文"))

        assertEquals(listOf("3:广告"), hits)
    }
}
