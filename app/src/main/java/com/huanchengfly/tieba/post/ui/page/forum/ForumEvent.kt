package com.huanchengfly.tieba.post.ui.page.forum

import com.huanchengfly.tieba.post.api.models.protos.OriginThreadInfo
import com.huanchengfly.tieba.post.api.models.protos.ThreadInfo
import com.huanchengfly.tieba.post.model.ForumTab

/** What the user does on the forum page. */
sealed interface ForumEvent {
    data object Back : ForumEvent
    data object Search : ForumEvent
    data object OpenDetail : ForumEvent
    data object OpenRule : ForumEvent
    data object Share : ForumEvent
    data object SendToDesktop : ForumEvent

    /** Follows the forum, or signs in to it when it is followed. */
    data object FollowOrSignIn : ForumEvent

    /** Asks to unfollow, which the page confirms first. */
    data object Unfollow : ForumEvent

    data class SelectTab(val tab: ForumTab) : ForumEvent
    data class SetSort(val sortType: Int) : ForumEvent
    data class SetGoodClass(val classId: Int) : ForumEvent
    data object Refresh : ForumEvent
    data object LoadMore : ForumEvent

    data class OpenThread(val thread: ThreadInfo) : ForumEvent
    data class OpenReplies(val thread: ThreadInfo) : ForumEvent
    data class OpenOriginThread(val origin: OriginThreadInfo) : ForumEvent
    data class OpenUser(val userId: Long) : ForumEvent
    data class Agree(val tab: ForumTab, val threadId: Long) : ForumEvent
}
