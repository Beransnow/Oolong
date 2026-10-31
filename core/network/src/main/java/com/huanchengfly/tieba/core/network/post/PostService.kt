package com.huanchengfly.tieba.core.network.post

import com.huanchengfly.tieba.post.api.models.protos.addPost.AddPostResponse
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.POST

interface PostService {
    @POST("c/c/post/add?cmd=309731&format=protobuf")
    suspend fun addPost(@Body body: RequestBody): AddPostResponse
}
