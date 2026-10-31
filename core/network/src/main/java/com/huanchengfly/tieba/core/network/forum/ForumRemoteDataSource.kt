package com.huanchengfly.tieba.core.network.forum

import com.huanchengfly.tieba.core.network.CommonParams
import com.huanchengfly.tieba.core.network.TiebaNetwork
import com.huanchengfly.tieba.core.network.TiebaProfile
import com.huanchengfly.tieba.core.network.protobuf.ProtobufBody
import com.huanchengfly.tieba.post.api.models.protos.forumRuleDetail.ForumRuleDetailRequest
import com.huanchengfly.tieba.post.api.models.protos.forumRuleDetail.ForumRuleDetailRequestData
import com.huanchengfly.tieba.post.api.models.protos.forumRuleDetail.ForumRuleDetailResponseData

class ForumRemoteDataSource(
    network: TiebaNetwork,
    private val commonParams: CommonParams,
) {
    private val profile = TiebaProfile.PROTO_12_52
    private val service = network.service<ForumService>(profile)

    suspend fun forumRuleDetail(forumId: Long): ForumRuleDetailResponseData {
        val request = ForumRuleDetailRequest(
            ForumRuleDetailRequestData(
                common = commonParams.protobuf(profile),
                forum_id = forumId,
            )
        )
        val response = service.forumRuleDetail(ProtobufBody(request, commonParams.stokenField()))
        return checkNotNull(response.data_)
    }
}
