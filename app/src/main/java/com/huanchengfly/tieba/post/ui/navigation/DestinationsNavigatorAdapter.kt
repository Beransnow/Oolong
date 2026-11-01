package com.huanchengfly.tieba.post.ui.navigation

import com.huanchengfly.tieba.post.ui.page.destinations.AboutPageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.AccountManagePageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.AppThemePageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.BlockListPageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.BlockSettingsPageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.CopyTextDialogPageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.CustomSettingsPageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.EditProfilePageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.ForumDetailPageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.ForumPageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.ForumRuleDetailPageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.ForumSearchPostPageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.HabitSettingsPageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.HistoryPageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.HotTopicListPageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.LoginPageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.MainPageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.MoreSettingsPageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.NotificationsPageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.OKSignSettingsPageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.PostSettingsPageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.ReplyPageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.SearchPageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.SettingsPageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.SubPostsPageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.SubPostsSheetPageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.ThreadPageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.ThreadStorePageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.UserProfilePageDestination
import com.huanchengfly.tieba.post.ui.page.destinations.WebViewPageDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.spec.Direction

/** A [Navigator] over compose-destinations, until the app moves to Navigation 3. */
class DestinationsNavigatorAdapter(private val navigator: DestinationsNavigator) : Navigator {
    override fun navigate(key: Key) = navigator.navigate(key.toDirection())

    override fun navigateUp() {
        navigator.navigateUp()
    }
}

fun Key.toDirection(): Direction = when (this) {
    MainKey -> MainPageDestination
    is ThreadKey -> ThreadPageDestination(
        threadId = threadId,
        forumId = forumId,
        postId = postId,
        seeLz = seeLz,
        sortType = sortType,
        from = from,
        extra = extra,
        threadInfo = threadInfo,
        scrollToReply = scrollToReply,
    )
    is SubPostsKey -> SubPostsPageDestination(threadId, forumId, postId, subPostId, loadFromSubPost)
    is SubPostsSheetKey -> SubPostsSheetPageDestination(threadId, forumId, postId, subPostId, loadFromSubPost)
    is ReplyKey -> ReplyPageDestination(
        forumId = forumId,
        forumName = forumName,
        threadId = threadId,
        postId = postId,
        subPostId = subPostId,
        replyUserId = replyUserId,
        replyUserName = replyUserName,
        replyUserPortrait = replyUserPortrait,
        tbs = tbs,
    )
    is CopyTextKey -> CopyTextDialogPageDestination(text)
    is ForumKey -> ForumPageDestination(forumName)
    is ForumDetailKey -> ForumDetailPageDestination(forumId)
    is ForumRuleDetailKey -> ForumRuleDetailPageDestination(forumId)
    is ForumSearchPostKey -> ForumSearchPostPageDestination(forumName, forumId)
    is UserProfileKey -> UserProfilePageDestination(uid)
    EditProfileKey -> EditProfilePageDestination
    is NotificationsKey -> NotificationsPageDestination(initialTab)
    SearchKey -> SearchPageDestination
    HistoryKey -> HistoryPageDestination
    ThreadStoreKey -> ThreadStorePageDestination
    HotTopicListKey -> HotTopicListPageDestination
    is WebViewKey -> WebViewPageDestination(initialUrl)
    LoginKey -> LoginPageDestination
    SettingsKey -> SettingsPageDestination
    AccountManageKey -> AccountManagePageDestination
    AppThemeKey -> AppThemePageDestination
    CustomSettingsKey -> CustomSettingsPageDestination
    HabitSettingsKey -> HabitSettingsPageDestination
    PostSettingsKey -> PostSettingsPageDestination
    BlockSettingsKey -> BlockSettingsPageDestination
    BlockListKey -> BlockListPageDestination
    OKSignSettingsKey -> OKSignSettingsPageDestination
    MoreSettingsKey -> MoreSettingsPageDestination
    AboutKey -> AboutPageDestination
}
