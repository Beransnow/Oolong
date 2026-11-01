package com.huanchengfly.tieba.post.api.retrofit.interfaces

import com.huanchengfly.tieba.post.api.models.protos.frsPage.FrsPageResponse
import com.huanchengfly.tieba.post.api.models.protos.getUserInfo.GetUserInfoResponse
import com.huanchengfly.tieba.post.api.models.protos.pbPage.PbPageResponse
import com.huanchengfly.tieba.post.api.models.protos.threadList.ThreadListResponse
import com.huanchengfly.tieba.post.api.retrofit.body.MyMultipartBody
import kotlinx.coroutines.flow.Flow
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface OfficialProtobufTiebaApi {




    @POST("/c/f/frs/page?cmd=301001")
    fun frsPageFlow(
        @Body body: MyMultipartBody,
        @Header("forum_name") forumName: String? = null,
    ): Flow<FrsPageResponse>

    @POST("/c/f/frs/threadlist?cmd=301002")
    fun threadListFlow(
        @Body body: MyMultipartBody,
    ): Flow<ThreadListResponse>

    @POST("/c/f/pb/page?cmd=302001&format=protobuf")
    fun pbPageFlow(
        @Body body: MyMultipartBody,
    ): Flow<PbPageResponse>




    @POST("/c/u/user/getuserinfo?cmd=303024&format=protobuf")
    fun getUserInfoFlow(
        @Body body: MyMultipartBody,
    ): Flow<GetUserInfoResponse>
}