package oolong.client.utils

import android.net.Uri
import oolong.client.ui.navigation.ForumKey
import oolong.client.ui.navigation.Key
import oolong.client.ui.navigation.ThreadKey

/** Whether [host] is Tieba's: matched whole, so that a look-alike such as `tieba.baidu.com.example.com` is not. */
fun isTiebaHost(host: String): Boolean =
    host == "wapp.baidu.com" || host.isHostOf("tieba.baidu.com") || host == "tiebac.baidu.com"

/** Tieba's hosts and Baidu's login and help pages: opened in the app, with its login. */
fun isInternalHost(host: String): Boolean =
    isTiebaHost(host) || host.isHostOf("wappass.baidu.com") || host.isHostOf("ufosdk.baidu.com") || host.isHostOf("m.help.baidu.com")

/** Whether this host is [domain] or one under it. */
private fun String.isHostOf(domain: String): Boolean = equals(domain, ignoreCase = true) || endsWith(".$domain", ignoreCase = true)

/**
 * Maps a Tieba web URL or a Tieba app link to the native page showing the same content, or
 * returns null if there is no native counterpart.
 */
fun Uri.toTiebaKey(): Key? {
    // Some links are written as "scheme://tieba.baidu.com//kw=..."
    val uri = Uri.parse(toString().replace("://tieba.baidu.com//", "://tieba.baidu.com/?"))
    val scheme = uri.scheme?.lowercase() ?: return null
    val host = uri.host?.lowercase().orEmpty()
    val path = uri.path?.lowercase().orEmpty()

    fun forum(vararg keys: String) = keys.firstNotNullOfOrNull { uri.getQueryParameter(it) }
        ?.takeIf { it.isNotEmpty() }
        ?.let { ForumKey(it) }

    fun thread(key: String) = uri.getQueryParameter(key)?.toLongOrNull()
        ?.let { ThreadKey(it) }

    return when (scheme) {
        "http", "https" -> when {
            !isTiebaHost(host) -> null
            path == "/f" || path == "/mo/q/m" -> thread("kz") ?: forum("kw", "word")
            path.startsWith("/p/") -> path.substring(3).toLongOrNull()?.let { ThreadKey(it) }
            else -> null
        }

        "tbfrs" -> forum("kw")
        "tbpb" -> thread("tid")
        "com.baidu.tieba" -> when {
            host != "unidispatch" -> null
            path == "/frs" -> forum("kw")
            path == "/pb" -> thread("tid")
            else -> null
        }

        else -> null
    }
}
