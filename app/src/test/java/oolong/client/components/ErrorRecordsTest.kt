package oolong.client.components

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import oolong.core.network.TiebaServerException
import oolong.core.network.UnreadableResponseException
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okhttp3.Response as RawResponse

@RunWith(RobolectricTestRunner::class)
class ErrorRecordsTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @After
    fun clear() = ErrorRecords.clear(context)

    private fun thrown(): IllegalStateException = runCatching { error("boom 12345") }.exceptionOrNull() as IllegalStateException

    @Test
    fun `the same problem shown again is one record, counted`() {
        val first = ErrorRecords.record(context, thrown(), time = 1_000)
        val second = ErrorRecords.record(context, thrown(), time = 2_000)

        assertEquals(first, second)
        val entry = ErrorRecords.list(context).single()
        assertEquals(2, entry.count)
        assertEquals(2_000, entry.time)
        assertTrue(ErrorRecords.read(context, entry.id)!!.contains("Count: 2"))
    }

    @Test
    fun `one error shown twice counts once`() {
        val error = thrown()
        ErrorRecords.record(context, error)
        ErrorRecords.record(context, error)

        assertEquals(1, ErrorRecords.list(context).single().count)
    }

    @Test
    fun `errors the app can explain are not kept`() {
        assertNull(ErrorRecords.record(context, SocketTimeoutException()))
        assertNull(ErrorRecords.record(context, IOException("reset")))
        assertNull(ErrorRecords.record(context, TiebaServerException(110, "请求过于频繁", null)))
        assertNull(ErrorRecords.record(context, CancellationException()))
        assertTrue(ErrorRecords.list(context).isEmpty())
    }

    @Test
    fun `an HTTP error names its status, path and type, but not the query`() {
        val raw = RawResponse.Builder()
            .request(Request.Builder().url("https://tieba.baidu.com/mo/q/search/thread?word=secret").build())
            .protocol(Protocol.HTTP_1_1)
            .code(403)
            .message("Forbidden")
            .build()
        val error = HttpException(Response.error<Any>("<html></html>".toResponseBody("text/html; charset=utf-8".toMediaType()), raw))

        assertEquals("HTTP 403 · /mo/q/search/thread · text/html", ErrorRecords.detailOf(error))
    }

    @Test
    fun `an unreadable response names its endpoint, what came and why`() {
        val error = UnreadableResponseException("mo/q/search/thread", "text/html", IllegalArgumentException("Unexpected JSON token"))

        assertEquals("Unreadable response · mo/q/search/thread · text/html · IllegalArgumentException", ErrorRecords.detailOf(error))
    }

    @Test
    fun `any other error names its root cause`() {
        val error = RuntimeException("outer", IllegalStateException("inner\nmore"))

        assertEquals("IllegalStateException: inner", ErrorRecords.detailOf(error))
        assertNotNull(ErrorRecords.record(context, error))
    }
}
