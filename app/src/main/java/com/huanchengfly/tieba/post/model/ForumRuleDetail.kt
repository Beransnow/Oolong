package com.huanchengfly.tieba.post.model

import androidx.compose.runtime.Immutable
import com.huanchengfly.tieba.post.ui.common.PbContentRender
import kotlinx.collections.immutable.ImmutableList

@Immutable
data class ForumRuleDetail(
    val title: String,
    val publishTime: String,
    val preface: String,
    val author: ForumRuleAuthor,
    val rules: ImmutableList<ForumRuleItem>,
)

@Immutable
data class ForumRuleAuthor(
    val name: String,
    val nameShow: String,
    val portrait: String,
)

@Immutable
data class ForumRuleItem(
    val title: String,
    val content: ImmutableList<PbContentRender>,
)
