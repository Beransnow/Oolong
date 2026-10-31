package com.huanchengfly.tieba.core.network.thread

import com.huanchengfly.tieba.post.api.models.protos.pbFloor.PbFloorResponse
import com.huanchengfly.tieba.post.api.models.protos.pbPage.PbPageResponse
import kotlinx.serialization.json.JsonObject
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.POST

interface ThreadService {
    @POST("c/f/pb/page?cmd=302001&format=protobuf")
    suspend fun page(@Body body: RequestBody): PbPageResponse

    @POST("c/f/pb/floor?cmd=302002&format=protobuf")
    suspend fun floor(@Body body: RequestBody): PbFloorResponse

    @POST("c/c/post/addstore")
    suspend fun addStore(@Body body: RequestBody): JsonObject

    @POST("c/c/post/rmstore")
    suspend fun removeStore(@Body body: RequestBody): JsonObject

    @POST("c/c/bawu/delpost")
    suspend fun deletePost(@Body body: RequestBody): JsonObject

    @POST("c/c/bawu/delthread")
    suspend fun deleteThread(@Body body: RequestBody): JsonObject
}
