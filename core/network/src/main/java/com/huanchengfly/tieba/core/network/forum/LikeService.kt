package com.huanchengfly.tieba.core.network.forum

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.POST

interface LikeService {
    @POST("c/c/forum/like")
    suspend fun like(@Body body: RequestBody): LikeForumResponse

    @POST("c/c/forum/unfavolike")
    suspend fun unfavolike(@Body body: RequestBody): JsonObject

    @POST("c/f/forum/forumGuide")
    suspend fun forumGuide(@Body body: RequestBody): ForumGuideResponse
}

/** A page of the forums the user follows. */
@Serializable
data class ForumGuideResponse(
    @SerialName("like_forum") val likeForum: List<LikeForum>? = null,
    /** `true` or `false`, sent as either a boolean or a string. */
    @SerialName("like_forum_has_more") val likeForumHasMore: String? = null,
) {
    val hasMore: Boolean get() = likeForumHasMore == "true" || likeForumHasMore == "1"

    @Serializable
    data class LikeForum(
        @SerialName("forum_id") val forumId: String,
        @SerialName("forum_name") val forumName: String,
        @SerialName("level_id") val levelId: String? = null,
        @SerialName("is_sign") val isSign: String? = null,
        val avatar: String? = null,
    )
}

@Serializable
data class LikeForumResponse(
    val info: Info? = null,
    @SerialName("user_perm") val userPerm: UserPerm? = null,
) {
    @Serializable
    data class Info(
        @SerialName("is_black") val isBlack: String? = null,
        @SerialName("like_num") val likeNum: String? = null,
        @SerialName("level_id") val levelId: String? = null,
        @SerialName("level_name") val levelName: String? = null,
        @SerialName("member_sum") val memberSum: String? = null,
        @SerialName("cur_score") val curScore: String? = null,
        @SerialName("levelup_score") val levelUpScore: String? = null,
        @SerialName("block_content") val blockContent: String? = null,
    )

    @Serializable
    data class UserPerm(
        @SerialName("level_id") val levelId: String? = null,
        @SerialName("level_name") val levelName: String? = null,
    )
}
