package com.huanchengfly.tieba.core.network.forum

import com.huanchengfly.tieba.post.api.models.protos.forumRuleDetail.ForumRuleDetailResponse
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.POST

interface ForumService {
    @POST("c/f/forum/forumRuleDetail?cmd=309690&format=protobuf")
    suspend fun forumRuleDetail(@Body body: RequestBody): ForumRuleDetailResponse
}
