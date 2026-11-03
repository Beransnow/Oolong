package oolong.client.ui.navigation

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
        assertEquals(ThreadKey(8_123_456_789), key("oolong://thread/8123456789"))
        assertEquals(ForumKey("android"), key("oolong://forum/android"))
        assertEquals(NotificationsKey(1), key("oolong://notifications/1"))
        assertEquals(HistoryKey, key("oolong://history"))
        assertEquals(SearchKey(), key("oolong://search"))
        assertEquals(ThreadStoreKey, key("oolong://favorite"))
    }

    @Test
    fun `reads forum names whether encoded or not`() {
        assertEquals(ForumKey("孙笑川"), key("oolong://forum/孙笑川"))
        assertEquals(ForumKey("孙笑川"), key("oolong://forum/%E5%AD%99%E7%AC%91%E5%B7%9D"))
    }

    @Test
    fun `opens the first notifications tab when the link names none`() {
        assertEquals(NotificationsKey(0), key("oolong://notifications"))
    }

    @Test
    fun `ignores links that name no page`() {
        assertNull(key("oolong://thread/abc"))
        assertNull(key("oolong://forum"))
        assertNull(key("oolong://unknown/1"))
        assertNull(key("https://tieba.baidu.com/p/1"))
    }
}
