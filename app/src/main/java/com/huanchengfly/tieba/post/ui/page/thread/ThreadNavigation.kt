package com.huanchengfly.tieba.post.ui.page.thread

import com.huanchengfly.tieba.post.model.ThreadSummary
import com.huanchengfly.tieba.post.ui.navigation.Navigator
import com.huanchengfly.tieba.post.ui.navigation.ThreadKey

/**
 * Opens [thread]. The page shows what the list knows about it until its first page loads, as the
 * list keeps the thread in the app's cache.
 */
fun Navigator.navigateToThread(thread: ThreadSummary, key: ThreadKey = ThreadKey(thread.id, thread.forum.id)) {
    navigate(key)
}
