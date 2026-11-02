package com.huanchengfly.tieba.post.repository

import com.huanchengfly.tieba.post.model.BlockRule
import com.huanchengfly.tieba.post.model.BlockRule.Condition
import com.huanchengfly.tieba.post.model.BlockRule.Pattern
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BlockRuleBackupTest {
    @Test
    fun `rules come back as they went, without hits or dates`() {
        val rules = listOf(
            BlockRule(id = 1, condition = Condition.Text(Pattern.Words(listOf("剧透"))), expiresAt = 9_000, hits = 4, lastHitAt = 3, createdAt = 2),
            BlockRule(
                id = 2,
                condition = Condition.Text(Pattern.Regex("互关|互粉"), BlockRule.TextField.Title),
                action = BlockRule.Action.Hide,
            ),
            BlockRule(id = 3, condition = Condition.User(7, "someone"), action = BlockRule.Action.Allow, enabled = false),
            BlockRule(id = 4, condition = Condition.Forum("抽卡"), scope = BlockRule.Scope.Feeds),
            BlockRule(id = 5, condition = Condition.AllOf(listOf(Condition.Forum("星穹铁道"), Condition.Not(Condition.LevelBelow(4))))),
        )

        val back = BlockRuleBackup.decode(BlockRuleBackup.encode(rules))

        assertEquals(rules.map { it.copy(id = 0, hits = 0, lastHitAt = null, createdAt = 0) }, back)
        assertTrue(rules.zip(back).all { (a, b) -> a.sameAs(b) })
    }

    @Test
    fun `files from before conditions are read as they were meant`() {
        val text = """
            {"rules":[
              {"type":"keyword","words":["互关|互粉"],"regex":true,"titleOnly":true,"action":"Hide"},
              {"type":"level","level":4,"inForum":"星穹铁道","scope":"Thread"},
              {"type":"user","uid":7,"name":"someone","action":"Allow"}
            ]}
        """.trimIndent()

        assertEquals(
            listOf(
                BlockRule(condition = Condition.Text(Pattern.Regex("互关|互粉"), BlockRule.TextField.Title), action = BlockRule.Action.Hide),
                BlockRule(
                    condition = Condition.AllOf(listOf(Condition.Forum("星穹铁道"), Condition.LevelBelow(4))),
                    scope = BlockRule.Scope.Thread,
                ),
                BlockRule(condition = Condition.User(7, "someone"), action = BlockRule.Action.Allow),
            ),
            BlockRuleBackup.decode(text),
        )
    }

    @Test
    fun `entries that make no sense are left out, and other files are refused`() {
        val text = """{"version":1,"rules":[{"type":"keyword"},{"type":"unknown","name":"x"},{"type":"forum","name":"吧"}]}"""

        assertEquals(listOf(BlockRule(condition = Condition.Forum("吧"))), BlockRuleBackup.decode(text))
        assertFailsWith<Exception> { BlockRuleBackup.decode("not json") }
    }
}
