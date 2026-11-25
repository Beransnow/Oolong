package oolong.core.network.forum

import okhttp3.RequestBody
import oolong.core.network.protos.forumRuleDetail.ForumRuleDetailResponse
import oolong.core.network.protos.frsPage.FrsPageResponse
import oolong.core.network.protos.generalTabList.GeneralTabListResponse
import oolong.core.network.protos.getForumDetail.GetForumDetailResponse
import oolong.core.network.protos.threadList.ThreadListResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface ForumService {
    @POST("c/f/forum/forumRuleDetail?cmd=309690&format=protobuf")
    suspend fun forumRuleDetail(@Body body: RequestBody): ForumRuleDetailResponse

    @POST("c/f/forum/getforumdetail?cmd=303021&format=protobuf")
    suspend fun forumDetail(@Body body: RequestBody): GetForumDetailResponse

    @POST("c/f/frs/page?cmd=301001&format=protobuf")
    suspend fun page(@Body body: RequestBody, @Header("forum_name") forumName: String): FrsPageResponse

    @POST("c/f/frs/threadlist?cmd=301002&format=protobuf")
    suspend fun threadList(@Body body: RequestBody): ThreadListResponse

    @POST("c/f/frs/generalTabList?cmd=309622&format=protobuf")
    suspend fun generalTabList(@Body body: RequestBody): GeneralTabListResponse
}
