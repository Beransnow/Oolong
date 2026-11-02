package com.huanchengfly.tieba.post.repository

import com.huanchengfly.tieba.post.model.BlockRule
import com.huanchengfly.tieba.post.model.BlockRule.Match
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BlockRuleBackupTest {
    @Test
    fun `rules come back as they went, without hits or dates`() {
        val rules = listOf(
            BlockRule(id = 1, match = Match.Keyword(listOf("剧透")), expiresAt = 9_000, hits = 4, lastHitAt = 3, createdAt = 2),
            BlockRule(id = 2, match = Match.Keyword(listOf("互关|互粉"), regex = true), action = BlockRule.Action.Hide, titleOnly = true),
            BlockRule(id = 3, match = Match.User(7, "someone"), action = BlockRule.Action.Allow, enabled = false),
            BlockRule(id = 4, match = Match.Forum("抽卡"), scope = BlockRule.Scope.Feeds),
            BlockRule(id = 5, match = Match.Level(4), inForum = "星穹铁道"),
        )

        val back = BlockRuleBackup.decode(BlockRuleBackup.encode(rules))

        assertEquals(rules.map { it.copy(id = 0, hits = 0, lastHitAt = null, createdAt = 0) }, back)
        assertTrue(rules.zip(back).all { (a, b) -> a.sameAs(b) })
    }

    @Test
    fun `entries that make no sense are left out, and other files are refused`() {
        val text = """{"version":1,"rules":[{"type":"keyword"},{"type":"unknown","name":"x"},{"type":"forum","name":"吧"}]}"""

        assertEquals(listOf(BlockRule(match = Match.Forum("吧"))), BlockRuleBackup.decode(text))
        assertFailsWith<Exception> { BlockRuleBackup.decode("not json") }
    }
}
