package oolong.core.network.user

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import okhttp3.RequestBody
import oolong.client.api.models.protos.profile.ProfileResponse
import oolong.client.api.models.protos.userPost.UserPostResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.QueryMap

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

    @POST("c/u/user/getUserBlackInfo")
    suspend fun blackInfo(@Body body: RequestBody): BlackInfoResponse

    @POST("c/c/user/setUserBlack")
    suspend fun setBlack(@Body body: RequestBody): JsonObject

    @POST("c/u/follow/followList")
    suspend fun followList(@Body body: RequestBody): FollowListResponse

    @POST("c/u/fans/page")
    suspend fun fans(@Body body: RequestBody): FansResponse
}

/** A page of the users someone follows. */
@Serializable
data class FollowListResponse(
    @SerialName("has_more") val hasMore: String? = null,
    @SerialName("follow_list") val users: List<ListedUser> = emptyList(),
)

/** A page of the users who follow someone. */
@Serializable
data class FansResponse(
    val page: Page? = null,
    @SerialName("user_list") val users: List<ListedUser> = emptyList(),
) {
    @Serializable
    data class Page(@SerialName("has_more") val hasMore: String? = null)
}

/** A user as a list of followers or followed shows them. */
@Serializable
data class ListedUser(
    val id: String? = null,
    val name: String? = null,
    @SerialName("name_show") val nameShow: String? = null,
    val portrait: String? = null,
    val intro: String? = null,
    /** "1" when the viewer follows them. */
    @SerialName("has_concerned") val followed: String? = null,
)

/** What the user has barred another user from; each is "1" when barred. */
@Serializable
data class BlackInfoResponse(
    @SerialName("perm_list") val permissions: BlackPermissions? = null,
)

/** A page of the users the user blacklisted, as the in-app page that lists them asks. */
interface BlackListService {
    @Headers(
        "Accept: application/json, text/plain, */*",
        "X-Requested-With: XMLHttpRequest",
        "Referer: https://tieba.baidu.com/mo/q/hybrid-main-service/blackList/hybrid?nonavigationbar=1&customfullscreen=1",
    )
    @GET("c/u/user/userBlackPage")
    suspend fun page(@QueryMap params: Map<String, String>): BlackListResponse
}

@Serializable
data class BlackListResponse(
    @SerialName("user_perm_list") val users: List<User> = emptyList(),
    @SerialName("is_has_more") val hasMore: String? = null,
) {
    @Serializable
    data class User(
        val uid: String? = null,
        @SerialName("user_name") val name: String? = null,
        @SerialName("name_show") val nameShow: String? = null,
        val portrait: String? = null,
        @SerialName("perm_list") val permissions: BlackPermissions? = null,
    )
}

@Serializable
data class BlackPermissions(
    val follow: String? = null,
    val interact: String? = null,
    val chat: String? = null,
)

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
