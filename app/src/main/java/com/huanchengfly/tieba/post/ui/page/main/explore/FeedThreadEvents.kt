package com.huanchengfly.tieba.post.ui.page.main.explore

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import com.huanchengfly.tieba.post.api.models.protos.OriginThreadInfo
import com.huanchengfly.tieba.post.api.models.protos.SimpleForum
import com.huanchengfly.tieba.post.api.models.protos.ThreadInfo
import com.huanchengfly.tieba.post.api.models.protos.User
import com.huanchengfly.tieba.post.ui.page.LocalNavigator
import com.huanchengfly.tieba.post.ui.page.destinations.ForumPageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.ThreadPageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.UserProfilePageDestination

/** What tapping the parts of a thread card does, the same in every explore feed. */
@Immutable
class FeedThreadEvents(
    val onOpen: (ThreadInfo) -> Unit = {},
    val onOpenReplies: (ThreadInfo) -> Unit = {},
    val onAgree: (ThreadInfo) -> Unit = {},
    val onOpenForum: (SimpleForum) -> Unit = {},
    val onOpenUser: (User) -> Unit = {},
    val onOpenOrigin: (OriginThreadInfo) -> Unit = {},
)

/** Opens what a thread card's parts lead to; liking is up to the feed. */
@Composable
fun rememberFeedThreadEvents(onAgree: (ThreadInfo) -> Unit): FeedThreadEvents {
    val navigator = LocalNavigator.current
    return remember(navigator, onAgree) {
        FeedThreadEvents(
            onOpen = { navigator.navigate(ThreadPageDestination(it.threadId, it.forumId, threadInfo = it)) },
            onOpenReplies = { navigator.navigate(ThreadPageDestination(it.threadId, it.forumId, scrollToReply = true)) },
            onAgree = onAgree,
            onOpenForum = { navigator.navigate(ForumPageDestination(it.name)) },
            onOpenUser = { navigator.navigate(UserProfilePageDestination(it.id)) },
            onOpenOrigin = { navigator.navigate(ThreadPageDestination(threadId = it.tid.toLong(), forumId = it.fid)) },
        )
    }
}
