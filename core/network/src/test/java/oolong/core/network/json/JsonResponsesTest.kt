package oolong.core.network.json

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import oolong.core.network.TiebaServerException
import oolong.core.network.VerificationChallenge
import oolong.core.network.VerificationRequiredException
import oolong.core.network.fakeSession
import oolong.core.network.identity.Session
import oolong.core.network.identity.SessionProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class JsonResponsesTest {
    private var refreshedTbs: String? = null
    private val sessionProvider = object : SessionProvider {
        override fun currentSession(): Session = fakeSession
        override fun updateTbs(tbs: String) {
            refreshedTbs = tbs
        }
    }

    private fun check(json: String) = checkJsonResponse(Json.parseToJsonElement(json).jsonObject, sessionProvider)

    @Test
    fun `accepts a successful response and refreshes tbs`() {
        check("""{"error_code":"0","error_msg":"","anti_stat":{"tbs":"new-tbs","need_vcode":0}}""")

        assertEquals("new-tbs", refreshedTbs)
    }

    @Test
    fun `ignores an empty tbs`() {
        check("""{"error_code":"0","anti_stat":{"tbs":""}}""")

        assertNull(refreshedTbs)
    }

    @Test
    fun `throws a server error with a numeric code`() {
        val error = assertFailsWith<TiebaServerException> { check("""{"error_code":340001,"error_msg":"吧内封禁"}""") }

        assertEquals(340001, error.code)
        assertEquals("吧内封禁", error.message)
    }

    @Test
    fun `asks for a captcha and still refreshes tbs`() {
        val error = assertFailsWith<VerificationRequiredException> {
            check(
                """
                {
                  "error_code": "5",
                  "error_msg": "需要验证码",
                  "info": {
                    "need_vcode": 1,
                    "vcode_md5": "md5-value",
                    "vcode_type": "4",
                    "vcode_pic_url": "https://example.com/vcode",
                    "vcode_extra": {"slideimg": "https://example.com/slide"}
                  },
                  "anti_stat": {"tbs": "new-tbs", "need_vcode": 1, "vcode_md5": "md5-value"}
                }
                """
            )
        }

        assertEquals(5, error.code)
        val captcha = assertIs<VerificationChallenge.Captcha>(error.challenge)
        assertEquals("md5-value", captcha.vcodeMd5)
        assertEquals("4", captcha.vcodeType)
        assertEquals("https://example.com/vcode", captcha.picUrl)
        assertIs<JsonObject>(captcha.extra)
        assertEquals("new-tbs", refreshedTbs)
    }

    @Test
    fun `asks for a captcha when need_vcode is set without an error code`() {
        val error = assertFailsWith<VerificationRequiredException> {
            check("""{"error_code":"0","info":{"need_vcode":1,"vcode_md5":"md5-value","vcode_type":"5"}}""")
        }

        assertIs<VerificationChallenge.Captcha>(error.challenge)
    }

    @Test
    fun `asks for an account access check`() {
        val error = assertFailsWith<VerificationRequiredException> {
            check("""{"error_code":"227001","error_msg":"","info":{"access_state":{"type":"login"}}}""")
        }

        assertIs<VerificationChallenge.AccountAccess>(error.challenge)
    }

    @Test
    fun `asks for a security check`() {
        val error = assertFailsWith<VerificationRequiredException> { check("""{"error_code":"3250023"}""") }

        assertEquals(VerificationChallenge.Security(3250023), error.challenge)
    }

    @Test
    fun `does not read sign-in info as a verification`() {
        check("""{"error_code":"0","info":{"block_content":"","block_confirm":""}}""")
    }
}
