package com.huanchengfly.tieba.post.api.retrofit.interfaces

import com.huanchengfly.tieba.post.api.Header
import com.huanchengfly.tieba.post.api.getScreenDensity
import com.huanchengfly.tieba.post.api.getScreenHeight
import com.huanchengfly.tieba.post.api.getScreenWidth
import com.huanchengfly.tieba.post.api.models.MessageListBean
import com.huanchengfly.tieba.post.utils.AccountUtil
import kotlinx.coroutines.flow.Flow
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Headers
import retrofit2.http.POST


interface NewTiebaApi {
    @Headers("${Header.FORCE_LOGIN}: ${Header.FORCE_LOGIN_TRUE}")
    @POST("/c/u/feed/replyme")
    @FormUrlEncoded
    fun replyMeFlow(
        @Field("pn") page: Int = 1,
        @Field("uid") uid: String? = AccountUtil.getUid(),
        @Field("time") time: String = "0",
        @Field("tid") threadId: String = "0",
        @Field("is_first") isFirst: String = "0",
        @Field("call_from") callFrom: String = "",
        @Field("scr_dip") scrDip: String = getScreenDensity().toString(),
        @Field("scr_h") scrH: String = getScreenHeight().toString(),
        @Field("scr_w") scrW: String = getScreenWidth().toString(),
    ): Flow<MessageListBean>

    @Headers("${Header.FORCE_LOGIN}: ${Header.FORCE_LOGIN_TRUE}")
    @POST("/c/u/feed/atme")
    @FormUrlEncoded
    fun atMeFlow(
        @Field("pn") page: Int = 1,
        @Field("uid") uid: String? = AccountUtil.getUid(),
        @Field("time") time: String = "0",
        @Field("tid") threadId: String = "0",
        @Field("is_first") isFirst: String = "0",
        @Field("call_from") callFrom: String = "",
        @Field("scr_dip") scrDip: String = getScreenDensity().toString(),
        @Field("scr_h") scrH: String = getScreenHeight().toString(),
        @Field("scr_w") scrW: String = getScreenWidth().toString(),
    ): Flow<MessageListBean>
}