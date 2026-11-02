package com.huanchengfly.tieba.post.repository

import com.huanchengfly.tieba.post.model.BlockRule
import com.huanchengfly.tieba.post.model.BlockRule.Match
import kotlin.test.Test
import kotlin.test.assertEquals

class BlockRuleMappingTest {
    @Test
    fun `every kind of rule is stored and read back as it was`() {
        val rules = listOf(
            BlockRule(id = 1, match = Match.Keyword(listOf("剧透")), expiresAt = 9_000, scope = BlockRule.Scope.Feeds),
            BlockRule(id = 2, match = Match.Keyword(listOf("互关|互粉"), regex = true), action = BlockRule.Action.Hide, titleOnly = true),
            BlockRule(id = 3, match = Match.User(7, "someone"), action = BlockRule.Action.Allow),
            BlockRule(id = 4, match = Match.Forum("抽卡"), enabled = false, hits = 12, lastHitAt = 100, createdAt = 50),
            BlockRule(id = 5, match = Match.Level(4), inForum = "星穹铁道", action = BlockRule.Action.Fold, scope = BlockRule.Scope.Thread),
        )

        assertEquals(rules, rules.map { it.toEntity().toBlockRule() })
    }
}
