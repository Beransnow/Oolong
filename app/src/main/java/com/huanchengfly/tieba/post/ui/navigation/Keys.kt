package com.huanchengfly.tieba.post.ui.navigation

import com.huanchengfly.tieba.post.ui.page.thread.ThreadPageExtra
import kotlinx.serialization.Serializable

/** A page the app can show, with what it needs to open. Kept small, as it is saved with the back stack. */
@Serializable
sealed interface Key

@Serializable
data object MainKey : Key

@Serializable
data class ThreadKey(
    val threadId: Long,
    val forumId: Long? = null,
    val postId: Long = 0,
    val seeLz: Boolean = false,
    val sortType: Int = 0,
    val from: String = "",
    val extra: ThreadPageExtra? = null,
    val scrollToReply: Boolean = false,
) : Key

/** 沉浸阅读 of a thread: its author's floors as one text. */
@Serializable
data class ReaderKey(val threadId: Long, val forumId: Long? = null) : Key

@Serializable
data class SubPostsKey(
    val threadId: Long,
    val forumId: Long = 0L,
    val postId: Long = 0L,
    val subPostId: Long = 0L,
    val loadFromSubPost: Boolean = false,
) : Key

/** [SubPostsKey] shown as a sheet over the thread. */
@Serializable
data class SubPostsSheetKey(
    val threadId: Long,
    val forumId: Long = 0L,
    val postId: Long = 0L,
    val subPostId: Long = 0L,
    val loadFromSubPost: Boolean = false,
) : Key {
    fun asPage() = SubPostsKey(threadId, forumId, postId, subPostId, loadFromSubPost)
}

@Serializable
data class ForumKey(val forumName: String) : Key

@Serializable
data class ForumDetailKey(val forumId: Long) : Key

@Serializable
data class ForumRuleDetailKey(val forumId: Long) : Key

@Serializable
data class ForumSearchPostKey(val forumName: String, val forumId: Long) : Key

@Serializable
data class UserProfileKey(val uid: Long) : Key

@Serializable
data object EditProfileKey : Key

@Serializable
data class NotificationsKey(val initialTab: Int = 0) : Key

/** Searching, for [keyword] at once when it is given. */
@Serializable
data class SearchKey(val keyword: String = "") : Key

@Serializable
data object HistoryKey : Key

@Serializable
data object ThreadStoreKey : Key

@Serializable
data object HotTopicListKey : Key

@Serializable
data class WebViewKey(val initialUrl: String) : Key

@Serializable
data object LoginKey : Key

@Serializable
data object SettingsKey : Key

@Serializable
data object AccountManageKey : Key

@Serializable
data object AppThemeKey : Key

@Serializable
data object CustomSettingsKey : Key

@Serializable
data object FontSizeKey : Key

@Serializable
data object SearchPlacementKey : Key

@Serializable
data object TranslucentThemeKey : Key

@Serializable
data object HabitSettingsKey : Key

@Serializable
data object PostSettingsKey : Key

@Serializable
data object BlockSettingsKey : Key

@Serializable
data object BlockListKey : Key

/** A block rule to edit, or a new one when [id] is 0. */
@Serializable
data class BlockRuleKey(val id: Long = 0) : Key

@Serializable
data object OKSignSettingsKey : Key

@Serializable
data object MoreSettingsKey : Key

@Serializable
data object AboutKey : Key
