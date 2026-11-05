package oolong.client.components.web

import android.webkit.CookieManager
import oolong.client.models.database.Account
import oolong.client.utils.bdussCookie
import oolong.client.utils.parseCookie
import org.json.JSONObject

/**
 * The cookies of Baidu's sites, read out of a cookie store and written back into one, so a login
 * can be kept, moved or put back. Only names and values can be read back; written back, a cookie
 * every site of baidu.com gets is set for all of baidu.com, and one only a single site gets for
 * that site alone, both kept for long.
 */
internal object BaiduCookies {
    /** The whole of baidu.com, whose cookies every site of it gets. */
    private const val ROOT = "https://baidu.com"

    /** The sites whose own cookies are kept besides those of the whole of baidu.com. */
    private val SITES = listOf(
        "https://tieba.baidu.com",
        "https://tiebac.baidu.com",
        "https://passport.baidu.com",
        "https://wappass.baidu.com",
        "https://www.baidu.com",
        "https://m.baidu.com",
    )

    private const val ATTRIBUTES = "Path=/; Max-Age=31536000"

    /** What [manager] holds for Baidu's sites, as text to keep; null when it holds nothing. */
    fun read(manager: CookieManager): String? {
        val root = parseCookie(manager.getCookie(ROOT).orEmpty())
        val sites = SITES.associateWith { site ->
            // Only what this site has beyond the whole of baidu.com
            parseCookie(manager.getCookie(site).orEmpty()).filter { (name, value) -> root[name] != value }
        }.filterValues { it.isNotEmpty() }
        if (root.isEmpty() && sites.isEmpty()) return null
        return JSONObject()
            .put(ROOT, JSONObject(root))
            .apply { sites.forEach { (site, cookies) -> put(site, JSONObject(cookies)) } }
            .toString()
    }

    /** Writes back into [manager] what [read] gave. */
    fun write(manager: CookieManager, kept: String) {
        val json = runCatching { JSONObject(kept) }.getOrNull() ?: return
        json.keys().forEach { site ->
            val cookies = json.getJSONObject(site)
            cookies.keys().forEach { name ->
                val value = cookies.getString(name)
                val cookie = if (site == ROOT) "$name=$value; Domain=.baidu.com; $ATTRIBUTES" else "$name=$value; $ATTRIBUTES"
                manager.setCookie(site, cookie)
            }
        }
        manager.flush()
    }

    /**
     * Writes into [manager] the login [account] was saved with, for an account that has none kept
     * yet: its BDUSS for all of baidu.com, and the rest of the cookies it logged in with for Tieba,
     * as they were read there.
     */
    fun seed(manager: CookieManager, account: Account) {
        if (account.bduss.isEmpty()) return
        manager.setCookie(ROOT, bdussCookie(account.bduss))
        parseCookie(account.cookie.orEmpty())
            .filterKeys { !it.equals("BDUSS", ignoreCase = true) }
            .forEach { (name, value) ->
                // The browser id is the whole of baidu.com's; the rest were Tieba's own
                if (name.equals("BAIDUID", ignoreCase = true)) {
                    manager.setCookie(ROOT, "$name=$value; Domain=.baidu.com; $ATTRIBUTES")
                } else {
                    manager.setCookie("https://tieba.baidu.com", "$name=$value; $ATTRIBUTES")
                }
            }
        manager.flush()
    }
}
