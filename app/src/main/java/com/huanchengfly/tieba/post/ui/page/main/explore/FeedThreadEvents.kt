package com.huanchengfly.tieba.post.ui.page.main.explore

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.huanchengfly.tieba.post.model.ForumSummary
import com.huanchengfly.tieba.post.model.SharedThread
import com.huanchengfly.tieba.post.model.ThreadSummary
import com.huanchengfly.tieba.post.model.User
import com.huanchengfly.tieba.post.ui.navigation.ForumKey
import com.huanchengfly.tieba.post.ui.navigation.ThreadKey
import com.huanchengfly.tieba.post.ui.navigation.UserProfileKey
import com.huanchengfly.tieba.post.ui.page.LocalNavigator
import com.huanchengfly.tieba.post.ui.page.thread.navigateToThread

/** What tapping the parts of a thread card does, the same in every explore feed. */
@Immutable
class FeedThreadEvents(
    val onOpen: (ThreadSummary) -> Unit = {},
    val onOpenReplies: (ThreadSummary) -> Unit = {},
    val onAgree: (ThreadSummary) -> Unit = {},
    val onOpenForum: (ForumSummary) -> Unit = {},
    val onOpenUser: (User) -> Unit = {},
    val onOpenOrigin: (SharedThread) -> Unit = {},
    /** Long-pressing a card, at its bounds and the press in the window; null where it does nothing. */
    val onPeek: ((ThreadSummary, from: Rect, at: Offset) -> Unit)? = null,
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
            onPeek = peeker?.let { { thread, from, at -> it.peek(thread, from, at) } },
        )
    }
}
