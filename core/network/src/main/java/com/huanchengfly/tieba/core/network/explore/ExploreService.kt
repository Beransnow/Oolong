package com.huanchengfly.tieba.core.network.explore

import com.huanchengfly.tieba.post.api.models.protos.hotThreadList.HotThreadListResponse
import com.huanchengfly.tieba.post.api.models.protos.personalized.PersonalizedResponse
import com.huanchengfly.tieba.post.api.models.protos.topicList.TopicListResponse
import com.huanchengfly.tieba.post.api.models.protos.userLike.UserLikeResponse
import kotlinx.serialization.json.JsonObject
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.POST

interface ExploreService {
    @POST("c/f/excellent/personalized?cmd=309264&format=protobuf")
    suspend fun personalized(@Body body: RequestBody): PersonalizedResponse

    @POST("c/f/concern/userlike?cmd=309474&format=protobuf")
    suspend fun userLike(@Body body: RequestBody): UserLikeResponse

    @POST("c/f/forum/hotThreadList?cmd=309661&format=protobuf")
    suspend fun hotThreadList(@Body body: RequestBody): HotThreadListResponse

    @POST("c/f/recommend/topicList?cmd=309289&format=protobuf")
    suspend fun topicList(@Body body: RequestBody): TopicListResponse
}

interface ExploreFormService {
    @POST("c/c/excellent/submitDislike")
    suspend fun submitDislike(@Body body: RequestBody): JsonObject
}
