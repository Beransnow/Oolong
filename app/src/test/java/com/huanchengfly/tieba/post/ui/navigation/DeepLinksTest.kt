package com.huanchengfly.tieba.post.ui.navigation

import android.net.Uri
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class DeepLinksTest {
    private fun key(link: String) = Uri.parse(link).toDeepLinkKey()

    @Test
    fun `opens the pages the links name`() {
        assertEquals(ThreadKey(8_123_456_789), key("tblite://thread/8123456789"))
        assertEquals(ForumKey("android"), key("tblite://forum/android"))
        assertEquals(NotificationsKey(1), key("tblite://notifications/1"))
        assertEquals(HistoryKey, key("tblite://history"))
        assertEquals(SearchKey(), key("tblite://search"))
        assertEquals(ThreadStoreKey, key("tblite://favorite"))
    }

    @Test
    fun `reads forum names whether encoded or not`() {
        assertEquals(ForumKey("孙笑川"), key("tblite://forum/孙笑川"))
        assertEquals(ForumKey("孙笑川"), key("tblite://forum/%E5%AD%99%E7%AC%91%E5%B7%9D"))
    }

    @Test
    fun `opens the first notifications tab when the link names none`() {
        assertEquals(NotificationsKey(0), key("tblite://notifications"))
    }

    @Test
    fun `ignores links that name no page`() {
        assertNull(key("tblite://thread/abc"))
        assertNull(key("tblite://forum"))
        assertNull(key("tblite://unknown/1"))
        assertNull(key("https://tieba.baidu.com/p/1"))
    }
}
