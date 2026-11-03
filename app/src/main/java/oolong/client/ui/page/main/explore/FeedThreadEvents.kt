package oolong.client.ui.page.main.explore

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import oolong.client.model.ForumSummary
import oolong.client.model.SharedThread
import oolong.client.model.ThreadSummary
import oolong.client.model.User
import oolong.client.ui.common.navigation.PageOrigin
import oolong.client.ui.navigation.ForumKey
import oolong.client.ui.navigation.ThreadKey
import oolong.client.ui.navigation.UserProfileKey
import oolong.client.ui.page.LocalNavigator
import oolong.client.ui.page.thread.navigateToThread

/** What tapping the parts of a thread card does, the same in every explore feed. */
@Immutable
class FeedThreadEvents(
    val onOpen: (ThreadSummary) -> Unit = {},
    val onOpenReplies: (ThreadSummary) -> Unit = {},
    val onAgree: (ThreadSummary) -> Unit = {},
    val onOpenForum: (ForumSummary) -> Unit = {},
    val onOpenUser: (User) -> Unit = {},
    val onOpenOrigin: (SharedThread) -> Unit = {},
    /**
     * Long-pressing a card, at its bounds and the press in the window, with the card as the
     * origin of the thread's page; null where it does nothing.
     */
    val onPeek: ((ThreadSummary, from: Rect, at: Offset, origin: PageOrigin) -> Unit)? = null,
)

/** Opens what a thread card's parts lead to; liking is up to the feed. */
@Composable
fun rememberFeedThreadEvents(onAgree: (ThreadSummary) -> Unit): FeedThreadEvents {
    val navigator = LocalNavigator.current
    val peeker = rememberThreadPeeker()
    return remember(navigator, onAgree, peeker) {
        FeedThreadEvents(
            onOpen = { navigator.navigateToThread(it) },
            onOpenReplies = { navigator.navigate(ThreadKey(it.id, it.forum.id, scrollToReply = true)) },
            onAgree = onAgree,
            onOpenForum = { navigator.navigate(ForumKey(it.name)) },
            onOpenUser = { navigator.navigate(UserProfileKey(it.id)) },
            onOpenOrigin = { navigator.navigate(ThreadKey(threadId = it.id, forumId = it.forumId)) },
            onPeek = peeker?.let { { thread, from, at, origin -> it.peek(thread, from, at, origin) } },
        )
    }
}
