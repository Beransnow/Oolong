package oolong.core.network.explore

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.QueryMap

/** The forums the user keeps out of their recommendations, as the in-app page that lists them asks. */
interface DislikedForumService {
    @Headers(ACCEPT, REQUESTED_WITH, REFERER)
    @GET("c/u/user/getDislikeList")
    suspend fun list(@QueryMap params: Map<String, String>): DislikedForumsResponse

    @Headers(ACCEPT, REQUESTED_WITH, REFERER)
    @GET("c/c/excellent/submitCancelDislike")
    suspend fun cancel(@QueryMap params: Map<String, String>): JsonObject
}

@Serializable
data class DislikedForumsResponse(
    @SerialName("forum_list") val forums: List<Forum> = emptyList(),
    @SerialName("has_more") val hasMore: String? = null,
) {
    @Serializable
    data class Forum(
        @SerialName("forum_id") val forumId: String? = null,
        @SerialName("forum_name") val forumName: String? = null,
        val avatar: String? = null,
        val slogan: String? = null,
    )
}

private const val ACCEPT = "Accept: application/json, text/plain, */*"
private const val REQUESTED_WITH = "X-Requested-With: XMLHttpRequest"
private const val REFERER =
    "Referer: https://tieba.baidu.com/mo/q/hybrid-main-service/forumBlock/hybrid?nonavigationbar=1&customfullscreen=1&nohead=1&skin=default"
