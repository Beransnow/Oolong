package com.huanchengfly.tieba.post.ui.page.thread

import com.huanchengfly.tieba.post.api.models.protos.ThreadInfo
import com.huanchengfly.tieba.post.ui.navigation.Navigator
import com.huanchengfly.tieba.post.ui.navigation.ThreadKey
import java.util.concurrent.ConcurrentHashMap

/**
 * Threads a list already shows, handed to the thread page so it can show the title and first
 * post while its first page loads. Kept in memory only: after the process dies the page loads
 * as it does from a link.
 */
object ThreadPreviewCache {
    private val threads = ConcurrentHashMap<Long, ThreadInfo>()

    fun put(thread: ThreadInfo) {
        threads[thread.threadId] = thread
    }

    /** The thread put for [threadId], once. */
    fun take(threadId: Long): ThreadInfo? = threads.remove(threadId)
}

/** Opens [thread], showing what the list knows about it until its first page loads. */
fun Navigator.navigateToThread(thread: ThreadInfo, key: ThreadKey = ThreadKey(thread.threadId, thread.forumId)) {
    ThreadPreviewCache.put(thread)
    navigate(key)
}
