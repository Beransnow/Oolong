package oolong.client.ui.page.thread

import oolong.client.model.ThreadSummary
import oolong.client.repository.ThreadQuery
import oolong.client.repository.ThreadSort
import oolong.client.ui.navigation.Navigator
import oolong.client.ui.navigation.ThreadKey

/**
 * Opens [thread]. The page shows what the list knows about it until its first page loads, as the
 * list keeps the thread in the app's cache.
 */
fun Navigator.navigateToThread(thread: ThreadSummary, key: ThreadKey = thread.key()) {
    navigate(key)
}

/** Where opening [this] thread from a list goes. */
fun ThreadSummary.key() = ThreadKey(id, forum.id)

/** What the page opened at this key asks for. */
fun ThreadKey.query() = ThreadQuery(
    threadId = threadId,
    sort = ThreadSort.entries.firstOrNull { it.value == sortType } ?: ThreadSort.OldestFirst,
    seeLz = seeLz,
    forumId = forumId,
    fromStore = from == ThreadPageFrom.FROM_STORE,
)
