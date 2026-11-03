package oolong.core.network.explore

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import mockwebserver3.MockResponse
import okio.Buffer
import okio.ByteString
import oolong.client.api.models.protos.hotThreadList.HotThreadListRequest
import oolong.client.api.models.protos.hotThreadList.HotThreadListResponse
import oolong.client.api.models.protos.hotThreadList.HotThreadListResponseData
import oolong.client.api.models.protos.personalized.PersonalizedRequest
import oolong.client.api.models.protos.personalized.PersonalizedResponse
import oolong.client.api.models.protos.personalized.PersonalizedResponseData
import oolong.client.api.models.protos.topicList.TopicListRequest
import oolong.client.api.models.protos.topicList.TopicListResponse
import oolong.client.api.models.protos.topicList.TopicListResponseData
import oolong.client.api.models.protos.userLike.UserLikeRequest
import oolong.client.api.models.protos.userLike.UserLikeResponse
import oolong.client.api.models.protos.userLike.UserLikeResponseData
import oolong.core.network.FakeDevice
import oolong.core.network.TiebaProfile
import oolong.core.network.testing.FormServer
import oolong.core.network.testing.assertSigned
import oolong.core.network.testing.form
import oolong.core.network.testing.multipartParts
import oolong.core.network.testing.value
import org.junit.After
import kotlin.test.Test
import kotlin.test.assertEquals

class ExploreRemoteDataSourceTest {
    private val server = FormServer()
    private val commonParams = server.commonParams
    private val dataSource = ExploreRemoteDataSource(server.network, commonParams, FakeDevice)

    @After
    fun tearDown() = server.close()

    private fun enqueue(bytes: ByteArray) {
        server.server.enqueue(MockResponse.Builder().body(Buffer().write(bytes)).build())
    }

    private fun sentData(target: String, profile: TiebaProfile = TiebaProfile.PROTO_12_52): ByteString {
        val request = server.takeRequest()
        assertEquals(target, request.target)
        commonParams.headers(profile).forEach { (name, value) -> assertEquals(value, request.headers[name], name) }
        val parts = multipartParts(request.headers["Content-Type"]!!, request.body!!)
        assertEquals(listOf("stoken", "data"), parts.map { it.first })
        return parts[1].second
    }

    @Test
    fun `asks for new recommendations on refresh and the next page otherwise`() = runTest {
        enqueue(PersonalizedResponse(data_ = PersonalizedResponseData()).encode())
        enqueue(PersonalizedResponse(data_ = PersonalizedResponseData()).encode())

        dataSource.personalized(refresh = true, page = 1)
        dataSource.personalized(refresh = false, page = 3)

        val target = "/c/f/excellent/personalized?cmd=309264&format=protobuf"
        val refresh = PersonalizedRequest.ADAPTER.decode(sentData(target, TiebaProfile.PROTO_22_FLOOR)).data_!!
        assertEquals(1, refresh.load_type)
        assertEquals(1, refresh.pn)
        assertEquals(0, refresh.page_thread_count)
        assertEquals(0, refresh.pre_ad_thread_count)
        assertEquals(1, refresh.request_times)
        assertEquals(1, refresh.is_newfeed)
        assertEquals(2, refresh.q_type)
        assertEquals(1, refresh.cache_column_style)
        assertEquals(commonParams.protobuf(TiebaProfile.PROTO_22_FLOOR), refresh.common)
        assertEquals(commonParams.appPos(), refresh.app_pos)
        val more = PersonalizedRequest.ADAPTER.decode(sentData(target, TiebaProfile.PROTO_22_FLOOR)).data_!!
        assertEquals(2, more.load_type)
        assertEquals(3, more.pn)
        assertEquals(12, more.page_thread_count)
        assertEquals(11, more.pre_ad_thread_count)
        assertEquals(2, more.request_times)
    }

    @Test
    fun `loads the followed users' threads from the top without a page tag`() = runTest {
        enqueue(UserLikeResponse(data_ = UserLikeResponseData()).encode())
        enqueue(UserLikeResponse(data_ = UserLikeResponseData()).encode())

        dataSource.userLike(pageTag = "", lastRequestUnix = 100)
        dataSource.userLike(pageTag = "tag", lastRequestUnix = 100)

        val first = UserLikeRequest.ADAPTER.decode(sentData("/c/f/concern/userlike?cmd=309474&format=protobuf")).data_!!
        assertEquals(1, first.loadType)
        assertEquals("", first.pageTag)
        assertEquals(100L, first.lastRequestUnix)
        assertEquals(1, first.followType)
        val next = UserLikeRequest.ADAPTER.decode(sentData("/c/f/concern/userlike?cmd=309474&format=protobuf")).data_!!
        assertEquals(2, next.loadType)
        assertEquals("tag", next.pageTag)
    }

    @Test
    fun `asks for the hot threads of a category`() = runTest {
        enqueue(HotThreadListResponse(data_ = HotThreadListResponseData()).encode())

        dataSource.hotThreads("game")

        val sent = HotThreadListRequest.ADAPTER.decode(sentData("/c/f/forum/hotThreadList?cmd=309661&format=protobuf")).data_!!
        assertEquals("game", sent.tabCode)
        assertEquals("1", sent.tabId)
    }

    @Test
    fun `asks for the whole hot topics ranking`() = runTest {
        enqueue(TopicListResponse(data_ = TopicListResponseData()).encode())

        dataSource.topics()

        val sent = TopicListRequest.ADAPTER.decode(sentData("/c/f/recommend/topicList?cmd=309289&format=protobuf")).data_!!
        assertEquals("newbang", sent.call_from)
        assertEquals("all", sent.list_type)
    }

    @Test
    fun `sends the reasons for disliking a thread as one JSON entry`() = runTest {
        server.enqueue("""{"error_code":"0"}""")

        dataSource.dislike(threadId = 9, forumId = 52, reasonIds = listOf(1, 7), extras = listOf("a", "b"), clickTime = 1700000000000)

        val request = server.takeRequest()
        assertEquals("/c/c/excellent/submitDislike", request.target)
        val form = request.form()
        assertSigned(form, withSig = false)
        assertEquals("homepage", form.value("dislike_from"))
        val entry = Json.parseToJsonElement(form.value("dislike")!!).jsonArray.single()
        assertEquals(
            JsonObject(
                mapOf(
                    "tid" to JsonPrimitive("9"),
                    "dislike_ids" to JsonPrimitive("1,7"),
                    "fid" to JsonPrimitive("52"),
                    "click_time" to JsonPrimitive(1700000000000),
                    "extra" to JsonPrimitive("a,b"),
                )
            ),
            entry,
        )
    }
}
