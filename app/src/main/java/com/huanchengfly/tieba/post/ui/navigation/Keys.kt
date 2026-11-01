package com.huanchengfly.tieba.post.ui.navigation

import com.huanchengfly.tieba.post.api.models.protos.ThreadInfo
import com.huanchengfly.tieba.post.ui.page.thread.ThreadPageExtra
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

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
    @Transient val threadInfo: ThreadInfo? = null,
    val scrollToReply: Boolean = false,
) : Key

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
) : Key

@Serializable
data class ReplyKey(
    val forumId: Long,
    val forumName: String,
    val threadId: Long,
    val postId: Long? = null,
    val subPostId: Long? = null,
    val replyUserId: Long? = null,
    val replyUserName: String? = null,
    val replyUserPortrait: String? = null,
    val tbs: String? = null,
) : Key

@Serializable
data class CopyTextKey(val text: String) : Key

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

@Serializable
data object SearchKey : Key

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
data object HabitSettingsKey : Key

@Serializable
data object PostSettingsKey : Key

@Serializable
data object BlockSettingsKey : Key

@Serializable
data object BlockListKey : Key

@Serializable
data object OKSignSettingsKey : Key

@Serializable
data object MoreSettingsKey : Key

@Serializable
data object AboutKey : Key
