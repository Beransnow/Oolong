package com.huanchengfly.tieba.post.ui.navigation

import android.net.Uri

/**
 * The page a tblite:// link opens, or null when it names none. Such links are stored outside
 * the app, in launcher shortcuts, pinned forum shortcuts and notifications, so they must keep
 * parsing as they are: forum names in pinned shortcuts are not percent-encoded.
 */
fun Uri.toDeepLinkKey(): Key? {
    if (scheme?.lowercase() != "tblite") return null
    val argument = pathSegments.firstOrNull()
    return when (host) {
        "forum" -> argument?.takeIf { it.isNotEmpty() }?.let(::ForumKey)
        "thread" -> argument?.toLongOrNull()?.let(::ThreadKey)
        "notifications" -> NotificationsKey(argument?.toIntOrNull() ?: 0)
        "history" -> HistoryKey
        "search" -> SearchKey
        "favorite" -> ThreadStoreKey
        else -> null
    }
}
