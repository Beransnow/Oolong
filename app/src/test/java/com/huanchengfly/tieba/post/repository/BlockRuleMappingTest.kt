package com.huanchengfly.tieba.post.repository

import com.huanchengfly.tieba.post.model.BlockRule
import com.huanchengfly.tieba.post.model.BlockRule.Condition
import com.huanchengfly.tieba.post.model.BlockRule.Pattern
import com.huanchengfly.tieba.post.model.ContentKind
import kotlin.test.Test
import kotlin.test.assertEquals

class BlockRuleMappingTest {
    @Test
    fun `every kind of condition is stored and read back as it was`() {
        val rules = listOf(
            BlockRule(id = 1, condition = Condition.Text(Pattern.Words(listOf("剧透"))), expiresAt = 9_000, scope = BlockRule.Scope.Feeds),
            BlockRule(
                id = 2,
                condition = Condition.Text(Pattern.Regex("互关|互粉"), BlockRule.TextField.Title),
                action = BlockRule.Action.Hide,
            ),
            BlockRule(id = 3, condition = Condition.User(7, "someone", "某人"), action = BlockRule.Action.Allow),
            BlockRule(id = 4, condition = Condition.Forum("抽卡"), enabled = false, hits = 12, lastHitAt = 100, createdAt = 50),
            BlockRule(
                id = 5,
                condition = Condition.AnyOf(
                    listOf(
                        Condition.AllOf(listOf(Condition.Forum("星穹铁道"), Condition.LevelBelow(4))),
                        Condition.Not(Condition.ThreadAuthor),
                        Condition.AuthorName(Pattern.Words(listOf("抽奖"))),
                        Condition.ForumName(Pattern.Regex("^原神")),
                        Condition.Ip("广东"),
                        Condition.Has(ContentKind.Link),
                        Condition.Only(ContentKind.Emoticon),
                        Condition.ShorterThan(5),
                    ),
                ),
                action = BlockRule.Action.Fold,
                scope = BlockRule.Scope.Thread,
            ),
        )

        assertEquals(rules, rules.map { it.toEntity().toBlockRule() })
    }

    @Test
    fun `a condition this version can't read matches nothing`() {
        val rule = BlockRule(id = 1, condition = Condition.Forum("x")).toEntity().copy(condition = """{"type":"future"}""").toBlockRule()

        assertEquals(Condition.AllOf(emptyList()), rule.condition)
    }
}
