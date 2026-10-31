package com.huanchengfly.tieba.post.api.retrofit.interfaces

import com.huanchengfly.tieba.post.api.Header
import com.huanchengfly.tieba.post.api.models.CommonResponse
import com.huanchengfly.tieba.post.api.models.MessageListBean
import com.huanchengfly.tieba.post.api.models.MsgBean
import com.huanchengfly.tieba.post.api.models.ThreadStoreBean
import com.huanchengfly.tieba.post.api.retrofit.ApiResult
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import retrofit2.Call
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Headers
import retrofit2.http.POST


interface NewTiebaApi {
    @Headers("${Header.FORCE_LOGIN}: ${Header.FORCE_LOGIN_TRUE}")
    @POST("/c/u/feed/replyme")
    @FormUrlEncoded
    fun replyMeFlow(
        @Field("pn") page: Int = 0
    ): Flow<MessageListBean>

    @Headers("${Header.FORCE_LOGIN}: ${Header.FORCE_LOGIN_TRUE}")
    @POST("/c/u/feed/atme")
    @FormUrlEncoded
    fun atMeFlow(
        @Field("pn") page: Int = 0
    ): Flow<MessageListBean>
}