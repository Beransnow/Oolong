package com.huanchengfly.tieba.post.repository

import com.huanchengfly.tieba.post.api.interfaces.ITiebaApi
import com.huanchengfly.tieba.post.api.models.protos.BawuRoleInfoPub
import com.huanchengfly.tieba.post.api.models.protos.ForumRule
import com.huanchengfly.tieba.post.api.models.protos.PbContent
import com.huanchengfly.tieba.post.api.models.protos.forumRuleDetail.ForumRuleDetailResponse
import com.huanchengfly.tieba.post.api.models.protos.forumRuleDetail.ForumRuleDetailResponseData
import com.huanchengfly.tieba.post.model.ForumRuleAuthor
import com.huanchengfly.tieba.post.model.ForumRuleDetail
import com.huanchengfly.tieba.post.model.ForumRuleItem
import com.huanchengfly.tieba.post.ui.common.TextContentRender
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ForumRepositoryTest {
    private val api = mockk<ITiebaApi>()
    private val repository = ForumRepository(api)

    @Test
    fun `maps the forum rule response`() = runTest {
        every { api.forumRuleDetailFlow(FORUM_ID) } returns flowOf(forumRuleResponse())

        assertEquals(forumRuleDetail(), repository.forumRuleDetail(FORUM_ID))
    }

    @Test
    fun `merges consecutive text contents of a rule`() = runTest {
        val rule = ForumRule(
            title = "规则",
            content = listOf(PbContent(type = 0, text = "第一句"), PbContent(type = 0, text = "第二句")),
        )
        every { api.forumRuleDetailFlow(FORUM_ID) } returns
                flowOf(forumRuleResponse(rules = listOf(rule)))

        val detail = repository.forumRuleDetail(FORUM_ID)
        assertEquals(persistentListOf(TextContentRender("第一句第二句")), detail.rules.single().content)
    }

    @Test
    fun `fails when the response has no data`() = runTest {
        every { api.forumRuleDetailFlow(FORUM_ID) } returns flowOf(ForumRuleDetailResponse())

        assertFailsWith<IllegalStateException> { repository.forumRuleDetail(FORUM_ID) }
    }

    @Test
    fun `fails when the rule has no author`() = runTest {
        every { api.forumRuleDetailFlow(FORUM_ID) } returns
                flowOf(ForumRuleDetailResponse(data_ = ForumRuleDetailResponseData(title = "本吧吧规")))

        assertFailsWith<IllegalStateException> { repository.forumRuleDetail(FORUM_ID) }
    }
}

internal const val FORUM_ID = 52L

internal fun forumRuleResponse(
    rules: List<ForumRule> = listOf(
        ForumRule(title = "一、发帖规范", content = listOf(PbContent(type = 0, text = "标题需清楚描述帖子内容。"))),
    ),
) = ForumRuleDetailResponse(
    data_ = ForumRuleDetailResponseData(
        title = "本吧吧规",
        preface = "请遵守以下规定。",
        publish_time = "2026-10-31",
        rules = rules,
        bazhu = BawuRoleInfoPub(user_name = "bazhu", name_show = "吧主", portrait = "tb.1.abc"),
    )
)

internal fun forumRuleDetail() = ForumRuleDetail(
    title = "本吧吧规",
    publishTime = "2026-10-31",
    preface = "请遵守以下规定。",
    author = ForumRuleAuthor(name = "bazhu", nameShow = "吧主", portrait = "tb.1.abc"),
    rules = persistentListOf(
        ForumRuleItem("一、发帖规范", persistentListOf(TextContentRender("标题需清楚描述帖子内容。"))),
    ),
)
