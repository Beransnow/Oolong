package com.huanchengfly.tieba.core.network

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class ProfileInterceptorTest {
    private val commonParams = CommonParams(
        device = FakeDevice,
        install = FakeInstall(),
        sessionProvider = { fakeSession },
        clock = { 1_700_000_000_000L },
        random = Random(0),
    )

    private fun send(request: Request): Request {
        lateinit var sent: Request
        val client = OkHttpClient.Builder()
            .addInterceptor(ProfileInterceptor(TiebaProfile.PROTO_12_52, commonParams))
            .addInterceptor { chain ->
                sent = chain.request()
                Response.Builder()
                    .request(sent)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("".toResponseBody())
                    .build()
            }
            .build()
        client.newCall(request).execute().close()
        return sent
    }

    @Test
    fun `adds the common headers of the profile`() {
        val sent = send(Request.Builder().url("https://tiebac.baidu.com/c/f/forum/forumRuleDetail").build())

        assertEquals(commonParams.headers(TiebaProfile.PROTO_12_52), sent.headers.toList())
    }

    @Test
    fun `keeps headers the request sets itself`() {
        val sent = send(
            Request.Builder()
                .url("https://tiebac.baidu.com/c/f/forum/forumRuleDetail")
                .header("User-Agent", "custom")
                .build()
        )

        assertEquals(listOf("custom"), sent.headers("User-Agent"))
    }
}
