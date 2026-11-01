package com.huanchengfly.tieba.core.network.search

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * The search pages answer with `no` and `error` instead of `error_code`, so their responses carry
 * these fields and the data source checks them.
 */
interface SearchResponse {
    val no: Int
    val error: String
}

@Serializable
data class SearchForumsResponse(
    override val no: Int = 0,
    override val error: String = "",
    val data: Data? = null,
) : SearchResponse {
    @Serializable
    data class Data(
        val exactMatch: Forum? = null,
        /** A list, or an object keyed by position. */
        val fuzzyMatch: JsonElement? = null,
    ) {
        val fuzzyForums: List<Forum> get() = fuzzyMatch.entries()
    }

    @Serializable
    data class Forum(
        @SerialName("forum_id") val forumId: Long? = null,
        @SerialName("forum_name") val forumName: String? = null,
        @SerialName("forum_name_show") val forumNameShow: String? = null,
        val avatar: String? = null,
        @SerialName("post_num") val postNum: String? = null,
        @SerialName("concern_num") val concernNum: String? = null,
        @SerialName("has_concerned") val hasConcerned: Int = 0,
        val intro: String? = null,
        val slogan: String? = null,
    )
}

@Serializable
data class SearchUsersResponse(
    override val no: Int = 0,
    override val error: String = "",
    val data: Data? = null,
) : SearchResponse {
    @Serializable
    data class Data(
        val exactMatch: User? = null,
        /** A list, or an object keyed by position. */
        val fuzzyMatch: JsonElement? = null,
    ) {
        val fuzzyUsers: List<User> get() = fuzzyMatch.entries()
    }

    @Serializable
    data class User(
        val id: String? = null,
        val intro: String? = null,
        @SerialName("show_nickname") val showNickname: String? = null,
        val name: String? = null,
        val portrait: String? = null,
        @SerialName("fans_num") val fansNum: String? = null,
        @SerialName("has_concerned") val hasConcerned: Int = 0,
    )
}

@Serializable
data class SearchThreadsResponse(
    override val no: Int = 0,
    override val error: String = "",
    val data: Data? = null,
) : SearchResponse {
    @Serializable
    data class Data(
        @SerialName("has_more") val hasMore: Int = 0,
        @SerialName("current_page") val currentPage: Int = 0,
        @SerialName("post_list") val postList: List<Post> = emptyList(),
    )

    /** A thread, or a reply in one, that matched. */
    @Serializable
    data class Post(
        val tid: String,
        val pid: String = "0",
        /** The id of the matching reply inside a floor, "0" for a thread or a floor. */
        val cid: String = "0",
        val title: String = "",
        val content: String = "",
        val time: String = "0",
        @SerialName("post_num") val postNum: String = "0",
        @SerialName("like_num") val likeNum: String = "0",
        @SerialName("share_num") val shareNum: String = "0",
        @SerialName("forum_id") val forumId: String = "0",
        @SerialName("forum_name") val forumName: String = "",
        val user: User = User(),
        @SerialName("forum_info") val forumInfo: ForumInfo? = null,
        val media: List<Media> = emptyList(),
        /** The thread a matching reply belongs to. */
        @SerialName("main_post") val mainPost: MainPost? = null,
        /** The post a matching reply quotes. */
        @SerialName("post_info") val postInfo: PostInfo? = null,
    )

    @Serializable
    data class Media(
        val type: String = "",
        @SerialName("water_pic") val waterPic: String? = null,
        @SerialName("small_pic") val smallPic: String? = null,
        @SerialName("big_pic") val bigPic: String? = null,
    )

    @Serializable
    data class MainPost(
        val title: String = "",
        val content: String = "",
        val tid: Long = 0,
        val user: User = User(),
    )

    @Serializable
    data class PostInfo(
        val tid: Long = 0,
        val pid: Long = 0,
        val title: String = "",
        val content: String = "",
        val user: User = User(),
    )

    @Serializable
    data class ForumInfo(
        @SerialName("forum_name") val forumName: String = "",
        val avatar: String = "",
    )

    @Serializable
    data class User(
        @SerialName("user_name") val userName: String? = null,
        @SerialName("show_nickname") val showNickname: String? = null,
        /** Missing when sorting oldest first. */
        @SerialName("user_id") val userId: String = "0",
        val portrait: String? = null,
    )
}

private val lenientJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
}

private inline fun <reified T> JsonElement?.entries(): List<T> {
    val elements = when (this) {
        is JsonArray -> this
        is JsonObject -> values
        else -> return emptyList()
    }
    return elements.filterIsInstance<JsonObject>().map { lenientJson.decodeFromJsonElement<T>(it) }
}
