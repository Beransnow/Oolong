package com.huanchengfly.tieba.core.network.message

import com.huanchengfly.tieba.core.network.TiebaProfile
import com.huanchengfly.tieba.core.network.testing.FormServer
import com.huanchengfly.tieba.core.network.testing.assertSigned
import com.huanchengfly.tieba.core.network.testing.form
import com.huanchengfly.tieba.core.network.testing.value
import kotlinx.coroutines.test.runTest
import org.junit.After
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MessageRemoteDataSourceTest {
    private val server = FormServer()
    private val dataSource = MessageRemoteDataSource(server.network, server.commonParams)

    @After
    fun tearDown() = server.close()

    @Test
    fun `asks for unread counts as the message task stack`() = runTest {
        server.enqueue("""{"error_code":"0","message":{"replyme":"2","atme":"0","fans":"1","agree":"3"}}""")

        val response = dataSource.reminder()

        val request = server.takeRequest()
        assertEquals("/c/s/msg", request.target)
        assertEquals("UTF-8", request.headers["Charset"])
        assertEquals(null, request.headers["x_bd_data_type"])
        val form = request.form()
        assertSigned(form, withSig = false)
        assertNull(form.value("bookmark"))
        assertNull(form.value("tbs"))
        assertEquals("1", form.value("personalized_rec_switch"))
        assertEquals(server.commonParams.form(TiebaProfile.MESSAGE_FORM_12_52).map { it.first }.sorted() + "sign",
            form.map { it.first }.filter { it != "sign" }.sorted() + "sign")
        assertEquals("2", response.message?.replyMe)
        assertEquals("1", response.message?.fans)
    }
}
