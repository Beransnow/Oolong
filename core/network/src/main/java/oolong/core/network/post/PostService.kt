package oolong.core.network.post

import okhttp3.RequestBody
import oolong.client.api.models.protos.addPollPost.AddPollPostResponse
import oolong.client.api.models.protos.addPost.AddPostResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface PostService {
    @POST("c/c/post/add?cmd=309731&format=protobuf")
    suspend fun addPost(@Body body: RequestBody): AddPostResponse

    @POST("c/c/post/addPollPost?cmd=309006&format=protobuf")
    suspend fun addPollPost(@Body body: RequestBody): AddPollPostResponse
}
