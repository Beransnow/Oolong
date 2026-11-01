package com.huanchengfly.tieba.core.network.agree

import com.huanchengfly.tieba.core.network.testing.FormServer
import com.huanchengfly.tieba.core.network.testing.assertSigned
import com.huanchengfly.tieba.core.network.testing.form
import com.huanchengfly.tieba.core.network.testing.value
import kotlinx.coroutines.test.runTest
import org.junit.After
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AgreeRemoteDataSourceTest {
    private val server = FormServer()
    private val dataSource = AgreeRemoteDataSource(server.network, server.commonParams)

    @After
    fun tearDown() = server.close()

    private suspend fun agree(target: AgreeTarget, undo: Boolean = false, disagree: Boolean = false) =
        server.run {
            enqueue(SUCCESS)
            dataSource.opAgree(target, forumId = 52, undo = undo, disagree = disagree)
            takeRequest()
        }

    @Test
    fun `agrees with a thread`() = runTest {
        val request = agree(AgreeTarget.Thread(threadId = 100, firstPostId = 101))

        assertEquals("/c/c/agree/opAgree", request.target)
        val form = request.form()
        assertSigned(form, withSig = true)
        assertEquals("100", form.value("thread_id"))
        assertEquals("101", form.value("post_id"))
        assertEquals("3", form.value("obj_type"))
        assertEquals("0", form.value("op_type"))
        assertEquals("2", form.value("agree_type"))
        assertEquals("52", form.value("forum_id"))
        assertEquals("tbs-value", form.value("tbs"))
        assertEquals("zid-value", form.value("z_id"))
    }

    @Test
    fun `agrees with a post and a sub-post`() = runTest {
        val post = agree(AgreeTarget.Post(threadId = 100, postId = 200)).form()
        assertEquals("1", post.value("obj_type"))
        assertEquals("200", post.value("post_id"))

        val subPost = agree(AgreeTarget.SubPost(threadId = 100, subPostId = 300)).form()
        assertEquals("2", subPost.value("obj_type"))
        assertEquals("300", subPost.value("post_id"))
    }

    @Test
    fun `takes back an agree and disagrees`() = runTest {
        assertEquals("1", agree(AgreeTarget.Thread(100, 101), undo = true).form().value("op_type"))
        assertEquals("5", agree(AgreeTarget.Thread(100, 101), disagree = true).form().value("agree_type"))
    }

    @Test
    fun `returns the agree result`() = runTest {
        server.enqueue(SUCCESS)

        val response = dataSource.opAgree(AgreeTarget.Thread(100, 101), forumId = null)

        assertEquals("0", response.data?.agree?.score)
        assertEquals("0", response.data?.agree?.isFirstAgree)
        assertNull(server.takeRequest().form().value("forum_id"))
    }

    private companion object {
        // A real response, numbers as strings and empty objects as arrays
        const val SUCCESS = """{"error_code":"0","error_msg":"\u6210\u529f","info":[],"error":{"errno":"0",""" +
            """"errmsg":"success","usermsg":null},"data":{"agree":{"score":"0","is_first_agree":"0"},""" +
            """"toast":"","contri_info":[]},"toast":"","server_time":"636447","time":1793466020,"ctime":0}"""
    }
}
