package com.huanchengfly.tieba.post.repository

import com.huanchengfly.tieba.core.network.forum.ForumRemoteDataSource
import com.huanchengfly.tieba.post.api.models.protos.forumRuleDetail.ForumRuleDetailResponseData
import com.huanchengfly.tieba.post.api.models.protos.renders
import com.huanchengfly.tieba.post.model.ForumRuleAuthor
import com.huanchengfly.tieba.post.model.ForumRuleDetail
import com.huanchengfly.tieba.post.model.ForumRuleItem
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ForumRepository @Inject constructor(
    private val remote: ForumRemoteDataSource,
) {
    suspend fun forumRuleDetail(forumId: Long): ForumRuleDetail = withContext(Dispatchers.IO) {
        remote.forumRuleDetail(forumId).toForumRuleDetail()
    }
}

private fun ForumRuleDetailResponseData.toForumRuleDetail(): ForumRuleDetail {
    val bazhu = checkNotNull(bazhu)
    return ForumRuleDetail(
        title = title,
        publishTime = publish_time,
        preface = preface,
        author = ForumRuleAuthor(
            name = bazhu.user_name,
            nameShow = bazhu.name_show,
            portrait = bazhu.portrait,
        ),
        rules = rules.map { ForumRuleItem(it.title, it.content.renders) }.toImmutableList(),
    )
}
