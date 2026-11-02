package com.huanchengfly.tieba.core.network.sign

import com.huanchengfly.tieba.core.network.testing.FormServer
import com.huanchengfly.tieba.core.network.testing.assertSigned
import com.huanchengfly.tieba.core.network.testing.form
import com.huanchengfly.tieba.core.network.testing.value
import kotlinx.coroutines.test.runTest
import org.junit.After
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MSignTest {
    private val server = FormServer()
    private val dataSource = SignRemoteDataSource(server.network, server.commonParams)

    @After
    fun tearDown() = server.close()

    @Test
    fun `signs in to several forums at once`() = runTest {
        server.enqueue(
            """{"error_code":"0","info":[{"forum_id":"1","forum_name":"a","signed":"1","sign_day_count":"3",
            "cur_score":"8","error":{"err_no":"0","errmsg":"","usermsg":""}}]}"""
        )

        val response = dataSource.mSign(listOf(1L, 2L, 3L))

        val request = server.takeRequest()
        assertEquals("/c/c/forum/msign", request.target)
        val form = request.form()
        assertSigned(form, withSig = true)
        assertEquals("1,2,3", form.value("forum_ids"))
        assertEquals("12345", form.value("user_id"))
        assertEquals("null", form.value("authsid"))
        assertEquals("tbs-value", form.value("tbs"))
        assertEquals("1", response.info.single().signed)
    }

    @Test
    fun `decodes empty objects sent as empty arrays`() = runTest {
        server.enqueue(
            """{"error_code":"0","error":[],"info":[{"forum_id":1,"signed":1,"error":[]}]}"""
        )

        val response = dataSource.mSign(listOf(1L))

        assertEquals("1", response.info.single().signed)
        assertEquals(null, response.info.single().error)
    }

    @Test
    fun `decodes an empty result list`() = runTest {
        server.enqueue("""{"error_code":"0","info":[]}""")

        assertEquals(emptyList(), dataSource.mSign(listOf(1L)).info)
    }

    @Test
    fun `decodes a refusal sent with the result list as a string`() = runTest {
        server.enqueue(
            """{"error_code":"0","info":"","sign_notice":"","error":{"errno":1,"errmsg":"x","usermsg":"操作过于频繁"}}"""
        )

        val response = dataSource.mSign(listOf(1L))

        assertEquals(emptyList(), response.info)
        assertEquals("操作过于频繁", response.refusal)
    }

    @Test
    fun `refuses more than 50 forums`() = runTest {
        assertFailsWith<IllegalArgumentException> { dataSource.mSign((1L..51L).toList()) }
    }
}
