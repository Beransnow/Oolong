package oolong.client.models

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/** JSON older versions wrote with Gson, which leaves out null fields, still reads. */
class StoredJsonTest {
    @Test
    fun `reads history extras as Gson wrote them`() {
        val json = Json { ignoreUnknownKeys = true }
        assertEquals(
            ThreadHistoryInfoBean(isSeeLz = true, pid = "1", forumName = "x", floor = "5"),
            json.decodeFromString<ThreadHistoryInfoBean>("""{"isSeeLz":true,"pid":"1","forumName":"x","floor":"5"}"""),
        )
        assertEquals(ThreadHistoryInfoBean(), json.decodeFromString<ThreadHistoryInfoBean>("""{"isSeeLz":false}"""))
    }

    @Test
    fun `reads the emoticon cache as Gson wrote it`() {
        assertEquals(
            EmoticonCache(ids = listOf("a"), mapping = mapOf("a" to "b")),
            Json.decodeFromString<EmoticonCache>("""{"ids":["a"],"mapping":{"a":"b"}}"""),
        )
    }
}
