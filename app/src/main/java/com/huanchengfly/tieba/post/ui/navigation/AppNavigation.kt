package com.huanchengfly.tieba.post.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.ui.NavDisplay
import com.huanchengfly.tieba.post.ui.common.navigation.FramedSinglePaneSceneStrategy
import com.huanchengfly.tieba.post.ui.common.navigation.LocalIsPop
import com.huanchengfly.tieba.post.ui.common.navigation.PageTransitions
import com.huanchengfly.tieba.post.ui.page.dialogs.CopyTextDialogPage
import com.huanchengfly.tieba.post.ui.page.dialogs.CopyTextDialogProperties
import com.huanchengfly.tieba.post.ui.page.forum.ForumPage
import com.huanchengfly.tieba.post.ui.page.forum.detail.ForumDetailPage
import com.huanchengfly.tieba.post.ui.page.forum.rule.ForumRuleDetailPage
import com.huanchengfly.tieba.post.ui.page.forum.searchpost.ForumSearchPostPage
import com.huanchengfly.tieba.post.ui.page.history.HistoryPage
import com.huanchengfly.tieba.post.ui.page.hottopic.list.HotTopicListPage
import com.huanchengfly.tieba.post.ui.page.login.LoginPage
import com.huanchengfly.tieba.post.ui.page.main.MainPage
import com.huanchengfly.tieba.post.ui.page.main.notifications.NotificationsPage
import com.huanchengfly.tieba.post.ui.page.reply.ReplyPage
import com.huanchengfly.tieba.post.ui.page.search.SearchPage
import com.huanchengfly.tieba.post.ui.page.settings.SettingsPage
import com.huanchengfly.tieba.post.ui.page.settings.about.AboutPage
import com.huanchengfly.tieba.post.ui.page.settings.account.AccountManagePage
import com.huanchengfly.tieba.post.ui.page.settings.block.BlockSettingsPage
import com.huanchengfly.tieba.post.ui.page.settings.block.blocklist.BlockListPage
import com.huanchengfly.tieba.post.ui.page.settings.custom.CustomSettingsPage
import com.huanchengfly.tieba.post.ui.page.settings.habit.HabitSettingsPage
import com.huanchengfly.tieba.post.ui.page.settings.more.MoreSettingsPage
import com.huanchengfly.tieba.post.ui.page.settings.oksign.OKSignSettingsPage
import com.huanchengfly.tieba.post.ui.page.settings.post.PostSettingsPage
import com.huanchengfly.tieba.post.ui.page.settings.theme.AppThemePage
import com.huanchengfly.tieba.post.ui.page.subposts.SubPostsPage
import com.huanchengfly.tieba.post.ui.page.subposts.SubPostsSheetPage
import com.huanchengfly.tieba.post.ui.page.thread.ThreadPage
import com.huanchengfly.tieba.post.ui.page.threadstore.ThreadStorePage
import com.huanchengfly.tieba.post.ui.page.user.UserProfilePage
import com.huanchengfly.tieba.post.ui.page.user.edit.EditProfilePage
import com.huanchengfly.tieba.post.ui.page.webview.WebViewPage

/** Whether the entry being composed is the top of the back stack, as opposed to one below a sheet or dialog. */
val LocalIsTopEntry = compositionLocalOf { true }

/** The page [key] opens. */
@Composable
private fun KeyContent(key: Key) {
    when (key) {
        MainKey -> MainPage()
        is ThreadKey -> ThreadPage(key)
        is SubPostsKey -> SubPostsPage(key)
        is SubPostsSheetKey -> SubPostsSheetPage(key)
        is ReplyKey -> ReplyPage(
            forumId = key.forumId,
            forumName = key.forumName,
            threadId = key.threadId,
            postId = key.postId,
            subPostId = key.subPostId,
            replyUserId = key.replyUserId,
            replyUserName = key.replyUserName,
            replyUserPortrait = key.replyUserPortrait,
            tbs = key.tbs,
        )
        is CopyTextKey -> CopyTextDialogPage(key.text)
        is ForumKey -> ForumPage(key)
        is ForumDetailKey -> ForumDetailPage(key)
        is ForumRuleDetailKey -> ForumRuleDetailPage(key)
        is ForumSearchPostKey -> ForumSearchPostPage(key)
        is UserProfileKey -> UserProfilePage(key)
        EditProfileKey -> EditProfilePage()
        is NotificationsKey -> NotificationsPage(initialTab = key.initialTab)
        SearchKey -> SearchPage()
        HistoryKey -> HistoryPage()
        ThreadStoreKey -> ThreadStorePage()
        HotTopicListKey -> HotTopicListPage()
        is WebViewKey -> WebViewPage(key.initialUrl)
        LoginKey -> LoginPage()
        SettingsKey -> SettingsPage()
        AccountManageKey -> AccountManagePage()
        AppThemeKey -> AppThemePage()
        CustomSettingsKey -> CustomSettingsPage()
        HabitSettingsKey -> HabitSettingsPage()
        PostSettingsKey -> PostSettingsPage()
        BlockSettingsKey -> BlockSettingsPage()
        BlockListKey -> BlockListPage()
        OKSignSettingsKey -> OKSignSettingsPage()
        MoreSettingsKey -> MoreSettingsPage()
        AboutKey -> AboutPage()
    }
}

/** Where [key] goes when two panes fit; null for pages that show over others. */
private val Key.paneRole: PaneRole?
    get() = when (this) {
        MainKey, is ForumKey, SearchKey, HistoryKey, ThreadStoreKey, HotTopicListKey,
        is ForumSearchPostKey, SettingsKey -> PaneRole.List

        is ReplyKey, is SubPostsSheetKey, is CopyTextKey -> null
        else -> PaneRole.Detail
    }

private fun Key.metadata(): Map<String, Any> = when (presentation) {
    Presentation.Sheet -> BottomSheetSceneStrategy.bottomSheet()
    Presentation.Dialog -> DialogSceneStrategy.dialog(CopyTextDialogProperties)
    Presentation.Page -> paneRole(checkNotNull(paneRole))
}

/**
 * Shows [backStack]. Scenes are tried in order: overlays first, then any added before the
 * single pane, which takes whatever is left.
 */
@Composable
fun AppNavDisplay(
    backStack: NavBackStack<NavKey>,
    modifier: Modifier = Modifier,
    sceneStrategies: List<SceneStrategy<NavKey>> = emptyList(),
) {
    val overlap = PageTransitions.rememberOverlap()
    val ids = backStack.map { (it as Page).id }
    val previousIds = remember { mutableStateOf(ids) }
    val isPop = remember(ids) {
        val previous = previousIds.value
        previousIds.value = ids
        ids.size < previous.size && previous.subList(0, ids.size) == ids
    }
    CompositionLocalProvider(LocalIsPop provides isPop) {
        NavDisplay(
            backStack = backStack,
            modifier = modifier,
            onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            sceneStrategies = listOf(DialogSceneStrategy<NavKey>(), BottomSheetSceneStrategy<NavKey>()) +
                sceneStrategies + FramedSinglePaneSceneStrategy(),
            transitionSpec = PageTransitions.transitionSpec(overlap),
            popTransitionSpec = PageTransitions.popTransitionSpec(overlap),
            entryProvider = entryProvider {
                entry<Page>(
                    clazzContentKey = { it.id },
                    metadata = { it.key.metadata() },
                ) { page ->
                    val isTop = backStack.lastOrNull() == page
                    CompositionLocalProvider(LocalIsTopEntry provides isTop) {
                        KeyContent(page.key)
                    }
                }
            },
        )
    }
}
