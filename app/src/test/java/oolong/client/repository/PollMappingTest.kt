package oolong.client.repository

import oolong.client.api.models.protos.PollInfo
import oolong.client.api.models.protos.PollOption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PollMappingTest {
    private val poll = PollInfo(
        title = "选哪个",
        is_multi = 1,
        options = listOf(PollOption(id = 1, num = 3, text = "甲"), PollOption(id = 2, num = 5, text = "乙")),
        total_num = 6,
        is_polled = 1,
        polled_value = "2",
        end_time = 1_000,
    )

    @Test
    fun `maps the options, the voters and the viewer's choice`() {
        val open = poll.toPoll(now = 999)!!

        assertEquals("选哪个", open.title)
        assertTrue(open.multiple)
        assertEquals(listOf("甲", "乙"), open.options.map { it.text })
        assertEquals(6, open.voters)
        assertEquals(8, open.votes)
        assertEquals(setOf(2), open.chosen)
        assertFalse(open.closed)
    }

    @Test
    fun `closes at its end time or when the server closes it`() {
        assertTrue(poll.toPoll(now = 1_000)!!.closed)
        assertTrue(poll.copy(end_time = 0, status = 1).toPoll(now = 1_000)!!.closed)
        assertFalse(poll.copy(end_time = 0).toPoll(now = 1_000)!!.closed)
    }

    @Test
    fun `a thread without options has no poll`() {
        assertNull(PollInfo().toPoll(now = 0))
    }
}
