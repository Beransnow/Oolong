package oolong.client.repository

import oolong.client.model.BlockRule
import oolong.client.model.BlockRule.Condition
import oolong.client.model.BlockRule.Pattern
import oolong.client.model.ContentKind
import oolong.client.model.User
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
        kinds: Set<ContentKind> = emptySet(),
        byThreadAuthor: Boolean = false,
    ) = Blockable(author, content, place, title, forum, kinds, byThreadAuthor = byThreadAuthor)

    private fun keyword(vararg words: String, regex: Boolean = false) =
        rule(Condition.Text(if (regex) Pattern.Regex(words.single()) else Pattern.Words(words.toList())))

    private fun rule(condition: Condition) = BlockRule(condition = condition)

    private fun BlockRules.matches(item: Blockable) = check(item) != null

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
            listOf(keyword("广告"), BlockRule(condition = Condition.User(7, "someone"), action = BlockRule.Action.Allow)),
        )

        assertFalse(rules.blocks(someone, "广告"))
        assertTrue(rules.blocks(someone.copy(id = 8, name = "other"), "广告"))
    }

    @Test
    fun `a user rule matches by id or by name, never a missing id or a blank name`() {
        assertTrue(blockRulesOf(listOf(rule(Condition.User(7, "")))).blocks(someone, ""))
        assertTrue(blockRulesOf(listOf(rule(Condition.User(0, "someone")))).blocks(someone, ""))
        assertFalse(blockRulesOf(listOf(rule(Condition.User(0, "")))).blocks(nameless, "内容"))
    }

    @Test
    fun `a forum rule matches its forum with or without 吧, except in the forum itself`() {
        val rules = blockRulesOf(listOf(rule(Condition.Forum("抽卡吧"))))

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
    fun `a forum joined with other tests applies inside the forum too`() {
        val rules = blockRulesOf(listOf(rule(Condition.AllOf(listOf(Condition.Forum("原神"), Condition.Text(Pattern.Words(listOf("剧透"))))))))

        assertTrue(rules.matches(item("剧透", forum = "原神吧", place = BlockPlace.Forum)))
        assertFalse(rules.matches(item("剧透", forum = "星穹铁道")))
        assertFalse(rules.matches(item("正文", forum = "原神")))
    }

    @Test
    fun `text is looked for in the title, the body or either`() {
        fun text(field: BlockRule.TextField) = blockRulesOf(listOf(rule(Condition.Text(Pattern.Words(listOf("剧透")), field))))

        assertTrue(text(BlockRule.TextField.Title).matches(item("正文", title = "剧透预警")))
        assertFalse(text(BlockRule.TextField.Title).matches(item("剧透", title = "标题")))
        assertTrue(text(BlockRule.TextField.Body).matches(item("剧透", title = "标题")))
        assertFalse(text(BlockRule.TextField.Body).matches(item("正文", title = "剧透")))
        assertTrue(text(BlockRule.TextField.Both).matches(item("正文", title = "剧透")))
    }

    @Test
    fun `tests join by and, by or, and turn around`() {
        val ad = Condition.Text(Pattern.Words(listOf("广告")))
        val low = Condition.LevelBelow(3)
        val newbie = someone.copy(level = 1)

        val both = blockRulesOf(listOf(rule(Condition.AllOf(listOf(ad, low)))))
        assertTrue(both.matches(item("广告", author = newbie)))
        assertFalse(both.matches(item("广告")))

        val either = blockRulesOf(listOf(rule(Condition.AnyOf(listOf(ad, low)))))
        assertTrue(either.matches(item("广告")))
        assertTrue(either.matches(item("正文", author = newbie)))
        assertFalse(either.matches(item("正文")))

        val notOp = blockRulesOf(listOf(rule(Condition.AllOf(listOf(ad, Condition.Not(Condition.ThreadAuthor))))))
        assertTrue(notOp.matches(item("广告")))
        assertFalse(notOp.matches(item("广告", byThreadAuthor = true)))
    }

    @Test
    fun `a rule with nothing to test, or a test that can't be judged, matches nothing`() {
        assertFalse(blockRulesOf(listOf(rule(Condition.AllOf(emptyList())))).matches(item("正文")))
        assertFalse(blockRulesOf(listOf(rule(Condition.AnyOf(emptyList())))).matches(item("正文")))
        assertFalse(blockRulesOf(listOf(rule(Condition.Not(Condition.Text(Pattern.Regex("(")))))).matches(item("正文")))
        assertFalse(blockRulesOf(listOf(rule(Condition.Not(Condition.Text(Pattern.Words(listOf(" "))))))).matches(item("正文")))
    }

    @Test
    fun `names of authors and forums are matched by words or patterns`() {
        val author = blockRulesOf(listOf(rule(Condition.AuthorName(Pattern.Words(listOf("抽奖"))))))
        assertTrue(author.matches(item(author = someone.copy(nameShow = "每日抽奖机器人"))))
        assertFalse(author.matches(item()))

        val forum = blockRulesOf(listOf(rule(Condition.ForumName(Pattern.Regex("^原神")))))
        assertTrue(forum.matches(item(forum = "原神内鬼")))
        assertFalse(forum.matches(item(forum = "玩原神")))
        assertFalse(forum.matches(item(forum = null)))
    }

    @Test
    fun `a user named without an id may be either of their names`() {
        val rules = blockRulesOf(listOf(rule(Condition.User(0, "某人"))))

        assertTrue(rules.matches(item(author = someone.copy(nameShow = "某人"))))
        assertFalse(blockRulesOf(listOf(rule(Condition.User(8, "某人")))).matches(item(author = someone.copy(nameShow = "某人"))))
    }

    @Test
    fun `where the author posted from is matched as shown`() {
        val rules = blockRulesOf(listOf(rule(Condition.Ip("广东"))))

        assertTrue(rules.matches(item(author = someone.copy(ipLocation = "广东"))))
        assertFalse(rules.matches(item(author = someone.copy(ipLocation = "广西"))))
        assertFalse(rules.matches(item()))
    }

    @Test
    fun `what content holds, holds only, and how long its words are`() {
        val links = blockRulesOf(listOf(rule(Condition.Has(ContentKind.Link))))
        assertTrue(links.matches(item("看这里", kinds = setOf(ContentKind.Link, ContentKind.Image))))
        assertFalse(links.matches(item("看这里")))

        val emoticons = blockRulesOf(listOf(rule(Condition.Only(ContentKind.Emoticon))))
        assertTrue(emoticons.matches(item("！！ 。", kinds = setOf(ContentKind.Emoticon))))
        assertFalse(emoticons.matches(item("哈", kinds = setOf(ContentKind.Emoticon))))
        assertFalse(emoticons.matches(item("", kinds = setOf(ContentKind.Emoticon, ContentKind.Image))))

        val short = blockRulesOf(listOf(rule(Condition.ShorterThan(3))))
        assertTrue(short.matches(item("顶！！")))
        assertFalse(short.matches(item("顶一下吧")))
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
