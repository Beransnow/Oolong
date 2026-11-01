package com.huanchengfly.tieba.core.network.user

import com.huanchengfly.tieba.post.api.models.protos.profile.ProfileResponse
import com.huanchengfly.tieba.post.api.models.protos.userPost.UserPostResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.POST

interface UserService {
    @POST("c/u/user/profile?cmd=303012&format=protobuf")
    suspend fun profile(@Body body: RequestBody): ProfileResponse

    @POST("c/u/feed/userpost?cmd=303002&format=protobuf")
    suspend fun userPost(@Body body: RequestBody): UserPostResponse
}

interface UserFormService {
    @POST("c/f/forum/like")
    suspend fun likedForums(@Body body: RequestBody): LikedForumsResponse

    @POST("c/c/user/follow")
    suspend fun follow(@Body body: RequestBody): JsonObject

    @POST("c/c/user/unfollow")
    suspend fun unfollow(@Body body: RequestBody): JsonObject

    @POST("c/c/profile/modify")
    suspend fun modifyProfile(@Body body: RequestBody): JsonObject
}

/** A page of the forums a user follows, as far as they let others see. */
@Serializable
data class LikedForumsResponse(
    @SerialName("has_more") val hasMore: String? = null,
    @SerialName("forum_list") val forumList: ForumList? = null,
) {
    @Serializable
    data class ForumList(
        @SerialName("non-gconforum") val forums: List<Forum> = emptyList(),
    )

    @Serializable
    data class Forum(
        val id: String,
        val name: String? = null,
        @SerialName("level_id") val levelId: String? = null,
        val avatar: String? = null,
        val slogan: String? = null,
    )
}
