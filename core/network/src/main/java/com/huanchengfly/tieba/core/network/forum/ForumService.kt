package com.huanchengfly.tieba.core.network.forum

import com.huanchengfly.tieba.post.api.models.protos.forumRuleDetail.ForumRuleDetailResponse
import com.huanchengfly.tieba.post.api.models.protos.frsPage.FrsPageResponse
import com.huanchengfly.tieba.post.api.models.protos.generalTabList.GeneralTabListResponse
import com.huanchengfly.tieba.post.api.models.protos.getForumDetail.GetForumDetailResponse
import com.huanchengfly.tieba.post.api.models.protos.threadList.ThreadListResponse
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface ForumService {
    @POST("c/f/forum/forumRuleDetail?cmd=309690&format=protobuf")
    suspend fun forumRuleDetail(@Body body: RequestBody): ForumRuleDetailResponse

    @POST("c/f/forum/getforumdetail?cmd=303021&format=protobuf")
    suspend fun forumDetail(@Body body: RequestBody): GetForumDetailResponse

    @POST("c/f/frs/page?cmd=301001&format=protobuf")
    suspend fun page(@Body body: RequestBody, @Header("forum_name") forumName: String): FrsPageResponse

    @POST("c/f/frs/threadlist?cmd=301002&format=protobuf")
    suspend fun threadList(@Body body: RequestBody): ThreadListResponse

    @POST("c/f/frs/generalTabList?cmd=309622&format=protobuf")
    suspend fun generalTabList(@Body body: RequestBody): GeneralTabListResponse
}
