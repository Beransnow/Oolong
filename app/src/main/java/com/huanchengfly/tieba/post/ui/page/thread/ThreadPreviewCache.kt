package com.huanchengfly.tieba.post.ui.page.thread

import com.huanchengfly.tieba.post.api.models.protos.ThreadInfo
import com.huanchengfly.tieba.post.model.ThreadSummary
import com.huanchengfly.tieba.post.repository.EmoticonRegistry
import com.huanchengfly.tieba.post.repository.ThreadPreview
import com.huanchengfly.tieba.post.repository.toThreadPreview
import com.huanchengfly.tieba.post.ui.navigation.Navigator
import com.huanchengfly.tieba.post.ui.navigation.ThreadKey
import java.util.concurrent.ConcurrentHashMap

/**
 * What lists already show of threads, handed to the thread page so it can show the title and
 * first post while its first page loads. Kept in memory only: after the process dies the page
 * loads as it does from a link.
 */
object ThreadPreviewCache {
    private val threads = ConcurrentHashMap<Long, ThreadPreview>()

    fun put(threadId: Long, preview: ThreadPreview) {
        threads[threadId] = preview
    }

    /** The preview put for [threadId], once. */
    fun take(threadId: Long): ThreadPreview? = threads.remove(threadId)
}

/** Opens [thread], showing what the list knows about it until its first page loads. */
fun Navigator.navigateToThread(thread: ThreadSummary, key: ThreadKey = ThreadKey(thread.id, thread.forum.id)) {
    ThreadPreviewCache.put(thread.id, thread.toThreadPreview())
    navigate(key)
}

/** Opens [thread], showing what the list knows about it until its first page loads. */
fun Navigator.navigateToThread(thread: ThreadInfo, key: ThreadKey = ThreadKey(thread.threadId, thread.forumId)) {
    // Its emoticons were learnt when the list loaded
    ThreadPreviewCache.put(thread.threadId, thread.toThreadPreview(EmoticonRegistry { _, _ -> }))
    navigate(key)
}
