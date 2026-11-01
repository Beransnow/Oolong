package com.huanchengfly.tieba.core.network.account

import com.huanchengfly.tieba.core.network.FakeDevice
import com.huanchengfly.tieba.core.network.testing.FormServer
import com.huanchengfly.tieba.core.network.testing.assertSigned
import com.huanchengfly.tieba.core.network.testing.form
import com.huanchengfly.tieba.core.network.testing.multipartParts
import com.huanchengfly.tieba.core.network.testing.value
import com.huanchengfly.tieba.post.api.models.protos.User
import com.huanchengfly.tieba.post.api.models.protos.getUserInfo.GetUserInfoRequest
import com.huanchengfly.tieba.post.api.models.protos.getUserInfo.GetUserInfoResponse
import com.huanchengfly.tieba.post.api.models.protos.getUserInfo.GetUserInfoResponseData
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import okio.Buffer
import okio.ByteString.Companion.encodeUtf8
import org.junit.After
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AccountRemoteDataSourceTest {
    private val server = FormServer()
    private val dataSource = AccountRemoteDataSource(server.network, server.commonParams, FakeDevice)

    @After
    fun tearDown() = server.close()

    @Test
    fun `logs in with the given BDUSS instead of the current account's`() = runTest {
        server.enqueue("""{"error_code":"0","user":{"id":"7","name":"n","portrait":"p"},"anti":{"tbs":"t"}}""")

        val response = dataSource.login("new-bduss", "new-stoken", firstLogin = true)

        val request = server.takeRequest()
        assertEquals("/c/s/login", request.target)
        val form = request.form()
        assertSigned(form, withSig = false)
        assertEquals("new-bduss|", form.value("bdusstoken"))
        assertEquals("new-stoken", form.value("stoken"))
        assertEquals(listOf("new-stoken"), form.filter { it.first == "stoken" }.map { it.second })
        assertEquals("1", form.value("first_login"))
        assertEquals("0", form.value("send_tb"))
        assertNull(form.value("BDUSS"))
        assertEquals("7", response.user.id)
        assertEquals("t", response.anti.tbs)
    }

    @Test
    fun `initializes the nickname with the given account`() = runTest {
        server.enqueue("""{"error_code":"0"}""")

        dataSource.initNickname("new-bduss", "new-stoken")

        val request = server.takeRequest()
        assertEquals("/c/s/initNickname", request.target)
        val form = request.form()
        assertEquals(listOf("new-bduss"), form.filter { it.first == "BDUSS" }.map { it.second })
        assertEquals("new-stoken", form.value("stoken"))
    }

    @Test
    fun `asks for user info as the given account`() = runTest {
        server.server.enqueue(
            MockResponse.Builder().body(Buffer().write(GetUserInfoResponse(data_ = GetUserInfoResponseData(user = User(id = 7, nameShow = "昵称"))).encode())).build()
        )

        val user = dataSource.userInfo(7, "new-bduss", "new-stoken")

        val request = server.takeRequest()
        assertEquals("/c/u/user/getuserinfo?cmd=303024&format=protobuf", request.target)
        val parts = multipartParts(request.headers["Content-Type"]!!, request.body!!)
        assertEquals("new-stoken", parts.single { it.first == "stoken" }.second.utf8())
        val sent = GetUserInfoRequest.ADAPTER.decode(parts.single { it.first == "data" }.second).data_!!
        assertEquals("new-bduss", sent.common?.BDUSS)
        assertEquals("new-stoken", sent.common?.stoken)
        assertEquals(7L, sent.uid)
        assertEquals("昵称", user.nameShow)
    }

    @Test
    fun `syncs with the device ids under their aliases`() = runTest {
        server.enqueue("""{"error_code":"0","client":{"client_id":"cid"},"wl_config":{"sample_id":"sid"}}""")

        val response = dataSource.sync(
            SyncInfo(
                clientId = null, pushSwitch = "1", lastPushSwitch = "0", lastPushSwitchTime = 5,
                runningAbi = "64", supportAbi = "64", installTime = 6, board = "b", incremental = "i",
            )
        )

        val form = server.takeRequest().form()
        assertSigned(form, withSig = false)
        assertNull(form.value("mac"))
        assertNull(form.value("_phone_imei"))
        assertNull(form.value("android_id"))
        assertEquals(FakeDevice.imei.encodeUtf8().base64(), form.value("iemi"))
        assertEquals(FakeDevice.androidId.encodeUtf8().base64(), form.value("di_diordna"))
        assertEquals("1", form.value("push_system_switch"))
        assertEquals("204734720", form.value("versioncode"))
        assertEquals(1, form.count { it.first == "_client_id" })
        assertEquals("cid", response.client.clientId)
        assertEquals("sid", response.wlConfig.sampleId)
    }
}
