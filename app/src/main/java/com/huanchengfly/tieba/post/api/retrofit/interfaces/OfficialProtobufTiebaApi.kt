package com.huanchengfly.tieba.post.api.retrofit.interfaces

import com.huanchengfly.tieba.post.api.models.protos.getUserInfo.GetUserInfoResponse
import com.huanchengfly.tieba.post.api.retrofit.body.MyMultipartBody
import kotlinx.coroutines.flow.Flow
import retrofit2.http.Body
import retrofit2.http.POST

interface OfficialProtobufTiebaApi {

    @POST("/c/u/user/getuserinfo?cmd=303024&format=protobuf")
    fun getUserInfoFlow(
        @Body body: MyMultipartBody,
    ): Flow<GetUserInfoResponse>
}