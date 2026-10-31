package com.huanchengfly.tieba.core.network.forum

import com.huanchengfly.tieba.core.network.TiebaServerException
import com.huanchengfly.tieba.core.network.testing.FormServer
import com.huanchengfly.tieba.core.network.testing.assertSigned
import com.huanchengfly.tieba.core.network.testing.form
import com.huanchengfly.tieba.core.network.testing.value
import kotlinx.coroutines.test.runTest
import org.junit.After
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class LikeRemoteDataSourceTest {
    private val server = FormServer()
    private val dataSource = LikeRemoteDataSource(server.network, server.commonParams)

    @After
    fun tearDown() = server.close()

    @Test
    fun `follows a forum from the forum page`() = runTest {
        server.enqueue(
            """{"error_code":"0","info":{"is_black":"0","level_id":"1","level_name":"初级粉丝",
            "member_sum":"100","cur_score":"0","levelup_score":"5"},"user_perm":[]}"""
        )

        val response = dataSource.like(forumId = 52, forumName = "吧名", hasHead = true)

        val request = server.takeRequest()
        assertEquals("/c/c/forum/like", request.target)
        val form = request.form()
        assertSigned(form, withSig = true)
        assertEquals("吧名", form.value("kw"))
        assertEquals("吧名", form.value("forum_name"))
        assertEquals("52", form.value("fid"))
        assertEquals("FRS", form.value("pagefrom"))
        assertEquals("null", form.value("st_type"))
        assertEquals("user-name", form.value("user_name"))
        assertEquals("12345", form.value("user_id"))
        assertEquals("1", form.value("has_head"))
        assertEquals("null", form.value("authsid"))
        assertNull(form.value("dev_id"))
        assertEquals("tbs-value", form.value("tbs"))
        assertEquals("初级粉丝", response.info?.levelName)
        assertNull(response.userPerm)
    }

    @Test
    fun `unfollows a forum without sig`() = runTest {
        server.enqueue("""{"error_code":"0","error_msg":""}""")

        dataSource.unfavolike(forumId = 52, forumName = "吧名", tbs = "page-tbs")

        val request = server.takeRequest()
        assertEquals("/c/c/forum/unfavolike", request.target)
        val form = request.form()
        assertSigned(form, withSig = false)
        assertEquals("1", form.value("favo_type"))
        assertEquals("from_frs", form.value("st_type"))
        assertEquals("null", form.value("authsid"))
        assertEquals("page-tbs", form.value("tbs"))
    }

    @Test
    fun `throws when unfollowing fails`() = runTest {
        server.enqueue("""{"error_code":"110003","error_msg":"未关注"}""")

        assertFailsWith<TiebaServerException> { dataSource.unfavolike(forumId = 52, forumName = "吧名") }
    }
}
