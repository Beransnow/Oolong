package com.huanchengfly.tieba.post.ui.page.thread

import com.huanchengfly.tieba.post.model.ThreadSummary
import com.huanchengfly.tieba.post.ui.navigation.Navigator
import com.huanchengfly.tieba.post.ui.navigation.ThreadKey
import java.util.concurrent.ConcurrentHashMap

/**
 * Threads as lists already show them, handed to the thread page so it can show the title and
 * first floor while its first page loads. Kept in memory only: after the process dies the page
 * loads as it does from a link.
 */
object ThreadPreviewCache {
    private val threads = ConcurrentHashMap<Long, ThreadSummary>()

    fun put(thread: ThreadSummary) {
        threads[thread.id] = thread
    }

    /** The thread put for [threadId], once. */
    fun take(threadId: Long): ThreadSummary? = threads.remove(threadId)
}

/** Opens [thread], showing what the list knows about it until its first page loads. */
fun Navigator.navigateToThread(thread: ThreadSummary, key: ThreadKey = ThreadKey(thread.id, thread.forum.id)) {
    ThreadPreviewCache.put(thread)
    navigate(key)
}
