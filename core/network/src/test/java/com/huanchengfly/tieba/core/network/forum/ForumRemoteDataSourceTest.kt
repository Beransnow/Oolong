package com.huanchengfly.tieba.core.network.forum

import com.huanchengfly.tieba.core.network.CommonParams
import com.huanchengfly.tieba.core.network.FakeDevice
import com.huanchengfly.tieba.core.network.FakeInstall
import com.huanchengfly.tieba.core.network.TiebaNetwork
import com.huanchengfly.tieba.core.network.TiebaProfile
import com.huanchengfly.tieba.core.network.TiebaServerException
import com.huanchengfly.tieba.core.network.fakeSession
import com.huanchengfly.tieba.core.network.testing.multipartParts
import com.huanchengfly.tieba.post.api.models.protos.Error
import com.huanchengfly.tieba.post.api.models.protos.forumRuleDetail.ForumRuleDetailRequest
import com.huanchengfly.tieba.post.api.models.protos.forumRuleDetail.ForumRuleDetailResponse
import com.huanchengfly.tieba.post.api.models.protos.forumRuleDetail.ForumRuleDetailResponseData
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Before
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ForumRemoteDataSourceTest {
    private val server = MockWebServer()
    private val commonParams = CommonParams(
        device = FakeDevice,
        install = FakeInstall(),
        sessionProvider = { fakeSession },
        clock = { 1_700_000_000_000L },
        random = Random(0),
    )
    private lateinit var dataSource: ForumRemoteDataSource

    @Before
    fun setUp() {
        server.start()
        val network = TiebaNetwork(commonParams, { fakeSession }, baseUrlOverride = server.url("/"))
        dataSource = ForumRemoteDataSource(network, commonParams)
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun enqueue(response: ForumRuleDetailResponse) {
        server.enqueue(MockResponse.Builder().body(Buffer().write(response.encode())).build())
    }

    @Test
    fun `sends the forum rule request as the 12_52 client`() = runTest {
        enqueue(ForumRuleDetailResponse(data_ = ForumRuleDetailResponseData(title = "本吧吧规")))

        dataSource.forumRuleDetail(52)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/c/f/forum/forumRuleDetail?cmd=309690&format=protobuf", request.target)
        commonParams.headers(TiebaProfile.PROTO_12_52).forEach { (name, value) ->
            assertEquals(value, request.headers[name], name)
        }
        assertEquals("chunked", request.headers["Transfer-Encoding"])

        val parts = multipartParts(request.headers["Content-Type"]!!, request.body!!)
        assertEquals(listOf("stoken", "data"), parts.map { it.first })
        assertEquals("stoken-value", parts[0].second.utf8())
        val sent = ForumRuleDetailRequest.ADAPTER.decode(parts[1].second)
        assertEquals(52, sent.data_?.forum_id)
        assertEquals(commonParams.protobuf(TiebaProfile.PROTO_12_52), sent.data_?.common)
    }

    @Test
    fun `returns the response data`() = runTest {
        val data = ForumRuleDetailResponseData(title = "本吧吧规", preface = "请遵守")
        enqueue(ForumRuleDetailResponse(data_ = data))

        assertEquals(data, dataSource.forumRuleDetail(52))
    }

    @Test
    fun `throws the server error and keeps the response`() = runTest {
        val response = ForumRuleDetailResponse(
            error = Error(error_code = 340001, error_msg = "吧内封禁"),
            data_ = ForumRuleDetailResponseData(title = "本吧吧规"),
        )
        enqueue(response)

        val error = assertFailsWith<TiebaServerException> { dataSource.forumRuleDetail(52) }
        assertEquals(340001, error.code)
        assertEquals("吧内封禁", error.message)
        assertEquals(response, error.response)
    }
}
