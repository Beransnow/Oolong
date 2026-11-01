package com.huanchengfly.tieba.core.network.user

import com.huanchengfly.tieba.core.network.FakeDevice
import com.huanchengfly.tieba.core.network.TiebaProfile
import com.huanchengfly.tieba.core.network.testing.FormServer
import com.huanchengfly.tieba.core.network.testing.assertSigned
import com.huanchengfly.tieba.core.network.testing.form
import com.huanchengfly.tieba.core.network.testing.multipartParts
import com.huanchengfly.tieba.core.network.testing.value
import com.huanchengfly.tieba.post.api.models.protos.profile.ProfileRequest
import com.huanchengfly.tieba.post.api.models.protos.profile.ProfileResponse
import com.huanchengfly.tieba.post.api.models.protos.profile.ProfileResponseData
import com.huanchengfly.tieba.post.api.models.protos.userPost.UserPostRequest
import com.huanchengfly.tieba.post.api.models.protos.userPost.UserPostResponse
import com.huanchengfly.tieba.post.api.models.protos.userPost.UserPostResponseData
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import okio.Buffer
import okio.ByteString
import org.junit.After
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UserRemoteDataSourceTest {
    private val server = FormServer()
    private val commonParams = server.commonParams
    private val dataSource = UserRemoteDataSource(server.network, commonParams, FakeDevice)

    @After
    fun tearDown() = server.close()

    private fun enqueue(bytes: ByteArray) {
        server.server.enqueue(MockResponse.Builder().body(Buffer().write(bytes)).build())
    }

    private fun sentData(target: String): ByteString {
        val request = server.takeRequest()
        assertEquals(target, request.target)
        commonParams.headers(TiebaProfile.PROTO_12_52).forEach { (name, value) -> assertEquals(value, request.headers[name], name) }
        val parts = multipartParts(request.headers["Content-Type"]!!, request.body!!)
        assertEquals(listOf("stoken", "data"), parts.map { it.first })
        return parts[1].second
    }

    @Test
    fun `asks for another user's profile as a guest`() = runTest {
        enqueue(ProfileResponse(data_ = ProfileResponseData()).encode())

        dataSource.profile(uid = 777)

        val sent = ProfileRequest.ADAPTER.decode(sentData("/c/u/user/profile?cmd=303012&format=protobuf")).data_!!
        assertEquals(777L, sent.friend_uid)
        assertEquals(1, sent.is_guest)
        assertEquals(12345L, sent.uid)
        assertEquals(commonParams.protobuf(TiebaProfile.PROTO_12_52), sent.common)
    }

    @Test
    fun `asks for the user's own profile as themselves`() = runTest {
        enqueue(ProfileResponse(data_ = ProfileResponseData()).encode())

        dataSource.profile(uid = 12345)

        val sent = ProfileRequest.ADAPTER.decode(sentData("/c/u/user/profile?cmd=303012&format=protobuf")).data_!!
        assertNull(sent.friend_uid)
        assertEquals(0, sent.is_guest)
    }

    @Test
    fun `asks for a page of replies`() = runTest {
        enqueue(UserPostResponse(data_ = UserPostResponseData()).encode())

        dataSource.posts(uid = 777, page = 2, threads = false)

        val sent = UserPostRequest.ADAPTER.decode(sentData("/c/u/feed/userpost?cmd=303002&format=protobuf")).data_!!
        assertEquals(777L, sent.uid)
        assertEquals(2, sent.pn)
        assertEquals(0, sent.is_thread)
        assertEquals(0, sent.is_view_card)
        assertEquals(0, sent.subtype)
    }

    @Test
    fun `lists another user's forums as a guest`() = runTest {
        server.enqueue("""{"error_code":"0","has_more":"1","forum_list":{"non-gconforum":[{"id":"52","name":"吧名","level_id":"7","avatar":"a"}]}}""")

        val response = dataSource.likedForums(uid = 777, page = 2)

        val form = server.takeRequest().form()
        assertSigned(form, withSig = false)
        assertEquals("2", form.value("page_no"))
        assertEquals("50", form.value("page_size"))
        assertEquals("12345", form.value("uid"))
        assertEquals("777", form.value("friend_uid"))
        assertEquals("1", form.value("is_guest"))
        assertEquals("吧名", response.forumList?.forums?.single()?.name)
    }

    @Test
    fun `follows with sig and unfollows without`() = runTest {
        server.enqueue("""{"error_code":"0"}""")
        server.enqueue("""{"error_code":"0"}""")

        dataSource.follow("tb.1.abc")
        dataSource.unfollow("tb.1.abc")

        val follow = server.takeRequest()
        assertEquals("/c/c/user/follow", follow.target)
        assertSigned(follow.form(), withSig = true)
        assertEquals("tb.1.abc", follow.form().value("portrait"))
        assertEquals("tbs-value", follow.form().value("tbs"))
        val unfollow = server.takeRequest()
        assertEquals("/c/c/user/unfollow", unfollow.target)
        assertSigned(unfollow.form(), withSig = false)
        assertEquals("2", unfollow.form().value("from_type"))
    }
}
