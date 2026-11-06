package oolong.core.network.explore

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import oolong.client.api.models.protos.Media
import oolong.client.api.models.protos.PbContent
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.QueryMap

/** The rankings of the day and the hot topics' pages, as the in-app pages showing them ask. */
interface HotListService {
    @Headers(ACCEPT, REQUESTED_WITH, REFERER)
    @GET("c/f/material/threadbang")
    suspend fun threadRanking(@QueryMap params: Map<String, String>): ThreadRankingResponse

    @Headers(ACCEPT, REQUESTED_WITH, REFERER)
    @GET("c/f/material/forumbang")
    suspend fun forumRanking(@QueryMap params: Map<String, String>): ForumRankingResponse

    @Headers(ACCEPT, REQUESTED_WITH, REFERER)
    @GET("c/f/hottopic/topicDetail")
    suspend fun topic(@QueryMap params: Map<String, String>): TopicResponse
}

/** A ranking of threads by one measure over the last day; the tab asked for carries the threads. */
@Serializable
data class ThreadRankingResponse(val data: Data? = null) {
    @Serializable
    data class Data(@SerialName("tab_list") val tabs: List<Tab> = emptyList())

    @Serializable
    data class Tab(
        val name: String = "",
        @SerialName("tab_code") val code: String = "",
        /** What the ranking goes by, in a sentence. */
        @SerialName("sort_rule") val rule: String = "",
        /** Why each thread is on it, such as how many read it, in the order of [threads]. */
        @SerialName("label_list") val labels: List<String> = emptyList(),
        @SerialName("thread_list") val threads: List<WebThread> = emptyList(),
    )
}

/** A ranking of forums by one measure over the last day; the tab asked for carries the forums. */
@Serializable
data class ForumRankingResponse(val data: Data? = null) {
    @Serializable
    data class Data(@SerialName("tab_list") val tabs: List<Tab> = emptyList())

    @Serializable
    data class Tab(
        val name: String = "",
        @SerialName("tab_code") val code: String = "",
        @SerialName("sort_rule") val rule: String = "",
        @SerialName("forum_list") val forums: List<Forum> = emptyList(),
    )

    @Serializable
    data class Forum(
        @SerialName("forum_id") val id: Long = 0,
        @SerialName("forum_name") val name: String = "",
        val avatar: String = "",
        @SerialName("member_count") val members: Long = 0,
        @SerialName("is_like") val liked: Int = 0,
        /** Why the forum is on the ranking, such as how many followed it. */
        @SerialName("score_desc") val score: String = "",
    )
}

/** A hot topic: what it is, then a page of cards, threads and the forums it is talked about in. */
@Serializable
data class TopicResponse(
    @SerialName("topic_info") val topic: Topic? = null,
    @SerialName("area_card_list") val areas: List<Area> = emptyList(),
    @SerialName("has_more") val hasMore: Int = 0,
    @SerialName("theme_color") val themeColor: ThemeColor? = null,
) {
    @Serializable
    data class Topic(
        @SerialName("topic_id") val id: Long = 0,
        @SerialName("topic_name") val name: String = "",
        @SerialName("topic_desc") val description: String = "",
        @SerialName("topic_pic") val picture: String = "",
        @SerialName("discuss_num") val discussCount: Long = 0,
        /** Its place on the hot topics ranking, 0 when off it. */
        @SerialName("idx_num") val rank: Int = 0,
        val tag: Int = 0,
    )

    @Serializable
    data class Area(@SerialName("card_data") val cards: List<Card> = emptyList())

    @Serializable
    data class Card(
        /** `common_thread` for the thread picked to lead, `relate_thread`, or `relate_forum`. */
        @SerialName("card_type") val type: String = "",
        val data: CardData? = null,
    )

    @Serializable
    data class CardData(
        @SerialName("thread_info") val thread: WebThread? = null,
        @SerialName("forum_list") val forums: List<Forum> = emptyList(),
    )

    @Serializable
    data class Forum(
        val id: Long = 0,
        val name: String = "",
        val avatar: String = "",
        @SerialName("member_num") val members: Long = 0,
    )

    @Serializable
    data class ThemeColor(val day: Color? = null, val night: Color? = null, val dark: Color? = null)

    /** A colour as hex without `#`. */
    @Serializable
    data class Color(@SerialName("common_color") val common: String = "")
}

/** A thread as these pages get it, the same fields as the client's own lists in JSON. */
@Serializable
data class WebThread(
    val id: Long = 0,
    val tid: Long = 0,
    val title: String = "",
    @SerialName("is_ntitle") val noTitle: Int = 0,
    val author: WebUser? = null,
    @SerialName("author_id") val authorId: Long = 0,
    val fid: Long = 0,
    val fname: String = "",
    @SerialName("forum_info") val forum: WebForum? = null,
    @SerialName("create_time") val createTime: Long = 0,
    @SerialName("last_time_int") val lastTime: Long = 0,
    @SerialName("rich_abstract") val richAbstract: List<WebContent> = emptyList(),
    @SerialName("first_post_id") val firstPostId: Long = 0,
    @SerialName("first_post_content") val firstPost: List<WebContent> = emptyList(),
    val media: List<WebMedia> = emptyList(),
    @SerialName("reply_num") val replyCount: Int = 0,
    @SerialName("share_num") val shareCount: Long = 0,
    @SerialName("agree_num") val agreeCount: Long = 0,
    val agree: WebAgree? = null,
    @SerialName("is_good") val good: Int = 0,
    @SerialName("is_top") val top: Int = 0,
    /** Why the thread is shown; one naming a topic links it to that topic. */
    @SerialName("thread_recommend_infos") val reasons: List<Reason> = emptyList(),
) {
    @Serializable
    data class Reason(
        @SerialName("recommend_reason") val text: String = "",
        @SerialName("topic_id") val topicId: Long = 0,
    )
}

@Serializable
data class WebUser(
    val id: Long = 0,
    val name: String = "",
    @SerialName("name_show") val nameShow: String = "",
    val portrait: String = "",
)

@Serializable
data class WebForum(val id: Long = 0, val name: String = "", val avatar: String = "")

@Serializable
data class WebAgree(@SerialName("has_agree") val agreed: Int = 0)

/** A piece of a post's content, as [PbContent] in JSON. */
@Serializable
data class WebContent(
    val type: Int = 0,
    val text: String = "",
    val link: String = "",
    val c: String = "",
    val src: String = "",
    val bsize: String = "",
    @SerialName("cdn_src") val cdnSrc: String = "",
    @SerialName("big_cdn_src") val bigCdnSrc: String = "",
    @SerialName("origin_src") val originSrc: String = "",
    @SerialName("origin_size") val originSize: Int = 0,
    @SerialName("is_long_pic") val longPicture: Int = 0,
    @SerialName("show_original_btn") val showOriginal: Int = 0,
) {
    fun toPbContent() = PbContent(
        type = type,
        text = text,
        link = link,
        c = c,
        src = src,
        bsize = bsize,
        cdnSrc = cdnSrc,
        bigCdnSrc = bigCdnSrc,
        originSrc = originSrc,
        originSize = originSize,
        isLongPic = longPicture,
        showOriginalBtn = showOriginal,
    )
}

/** A picture of a thread, as [Media] in JSON. */
@Serializable
data class WebMedia(
    val type: Int = 0,
    @SerialName("big_pic") val bigPic: String = "",
    @SerialName("src_pic") val srcPic: String = "",
    @SerialName("origin_pic") val originPic: String = "",
    @SerialName("dynamic_pic") val dynamicPic: String = "",
    val width: Int = 0,
    val height: Int = 0,
    @SerialName("origin_size") val originSize: Int = 0,
    @SerialName("post_id") val postId: Long = 0,
    @SerialName("is_long_pic") val longPicture: Int = 0,
    @SerialName("show_original_btn") val showOriginal: Int = 0,
) {
    fun toMedia() = Media(
        type = type,
        bigPic = bigPic,
        srcPic = srcPic,
        originPic = originPic,
        dynamicPic = dynamicPic,
        width = width,
        height = height,
        originSize = originSize,
        postId = postId,
        isLongPic = longPicture,
        showOriginalBtn = showOriginal,
    )
}

private const val ACCEPT = "Accept: application/json, text/plain, */*"
private const val REQUESTED_WITH = "X-Requested-With: XMLHttpRequest"
private const val REFERER = "Referer: https://tieba.baidu.com/mo/q/hybrid-usergrow-base/informativeTab"
