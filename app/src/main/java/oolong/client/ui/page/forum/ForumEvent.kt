package oolong.client.ui.page.forum

import oolong.client.model.ForumTab
import oolong.client.model.SharedThread
import oolong.client.model.ThreadSummary

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

    data class OpenThread(val thread: ThreadSummary) : ForumEvent
    data class OpenReplies(val thread: ThreadSummary) : ForumEvent
    data class OpenOriginThread(val origin: SharedThread) : ForumEvent
    data class OpenUser(val userId: Long) : ForumEvent
    data class Agree(val tab: ForumTab, val threadId: Long) : ForumEvent
}
