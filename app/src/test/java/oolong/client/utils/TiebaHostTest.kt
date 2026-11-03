package oolong.client.utils

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TiebaHostTest {
    @Test
    fun `tieba's hosts are matched whole`() {
        assertTrue(isTiebaHost("tieba.baidu.com"))
        assertTrue(isTiebaHost("c.tieba.baidu.com"))
        assertFalse(isTiebaHost("tieba.baidu.com.example.com"))
        assertFalse(isTiebaHost("faketieba.baidu.com"))
    }

    @Test
    fun `baidu's login and help pages are internal, look-alikes are not`() {
        assertTrue(isInternalHost("wappass.baidu.com"))
        assertFalse(isInternalHost("wappass.baidu.com.example.com"))
    }
}
