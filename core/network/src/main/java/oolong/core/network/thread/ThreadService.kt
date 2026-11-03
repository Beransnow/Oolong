package oolong.core.network.thread

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import okhttp3.RequestBody
import oolong.client.api.models.protos.pbFloor.PbFloorResponse
import oolong.client.api.models.protos.pbPage.PbPageResponse
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

    @POST("c/f/pb/picpage")
    suspend fun picPage(@Body body: RequestBody): PicPageResponse

    @POST("c/f/ueg/checkjubao")
    suspend fun checkReport(@Body body: RequestBody): CheckReportResponse

    @POST("c/f/post/threadstore")
    suspend fun threadStore(@Body body: RequestBody): ThreadStoreResponse

    @POST("c/c/bawu/delpost")
    suspend fun deletePost(@Body body: RequestBody): JsonObject

    @POST("c/c/bawu/delthread")
    suspend fun deleteThread(@Body body: RequestBody): JsonObject
}

/** A page of the threads the user stored. */
@Serializable
data class ThreadStoreResponse(
    val error: Error? = null,
    @SerialName("store_thread") val storeThread: List<StoredThread> = emptyList(),
) {
    @Serializable
    data class Error(
        val errno: String? = null,
        val errmsg: String? = null,
    )

    @Serializable
    data class StoredThread(
        @SerialName("thread_id") val threadId: String,
        val title: String = "",
        @SerialName("forum_name") val forumName: String = "",
        val author: Author = Author(),
        @SerialName("is_deleted") val isDeleted: String = "0",
        @SerialName("max_pid") val maxPid: String = "0",
        @SerialName("mark_pid") val markPid: String = "0",
        /** The floor of the newest post. */
        @SerialName("post_no") val postNo: String = "0",
        /** How many posts came after the mark. */
        val count: String = "0",
    )

    @Serializable
    data class Author(
        @SerialName("lz_uid") val lzUid: String? = null,
        val name: String? = null,
        @SerialName("name_show") val nameShow: String? = null,
        @SerialName("user_portrait") val userPortrait: String? = null,
    )
}

/** A run of a thread's pictures around one of them. */
@Serializable
data class PicPageResponse(
    @SerialName("pic_amount") val picAmount: String = "0",
    @SerialName("pic_list") val picList: List<Pic> = emptyList(),
) {
    @Serializable
    data class Pic(
        @SerialName("overall_index") val overallIndex: String = "0",
        @SerialName("show_original_btn") val showOriginalBtn: String = "0",
        val img: Img = Img(),
        @SerialName("post_id") val postId: String? = null,
        val author: PicAuthor? = null,
        /** The text of the post the picture is in. */
        val descr: List<Text> = emptyList(),
    )

    @Serializable
    data class PicAuthor(
        val name: String? = null,
        @SerialName("name_show") val nameShow: String? = null,
    )

    @Serializable
    data class Text(val type: Int = 0, val text: String = "")

    @Serializable
    data class Img(val original: Original = Original())

    @Serializable
    data class Original(
        val id: String = "",
        @SerialName("big_cdn_src") val bigCdnSrc: String = "",
        @SerialName("original_src") val originalSrc: String = "",
        val width: Int = 0,
        val height: Int = 0,
    )
}

/** Where to report a post: a web page the server prepares. */
@Serializable
data class CheckReportResponse(
    val errno: Int = 0,
    val errmsg: String = "",
    val data: Data = Data(),
) {
    @Serializable
    data class Data(val url: String = "")
}
