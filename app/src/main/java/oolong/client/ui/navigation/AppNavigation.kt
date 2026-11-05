package oolong.client.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SceneStrategy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import oolong.client.LocalNotificationCountFlow
import oolong.client.arch.GlobalEvent
import oolong.client.arch.emitGlobalEvent
import oolong.client.dataStore
import oolong.client.ui.common.navigation.FramedSinglePaneSceneStrategy
import oolong.client.ui.common.navigation.LocalIsPop
import oolong.client.ui.common.navigation.LocalPageOrigins
import oolong.client.ui.common.navigation.LocalPagePeek
import oolong.client.ui.common.navigation.PageBackHandler
import oolong.client.ui.common.navigation.PageDisplay
import oolong.client.ui.common.navigation.PagePeek
import oolong.client.ui.common.navigation.PageTransitions
import oolong.client.ui.common.navigation.rememberPageScenes
import oolong.client.ui.common.theme.compose.Motion
import oolong.client.ui.common.windowsizeclass.LocalWindowSizeClass
import oolong.client.ui.common.windowsizeclass.WindowHeightSizeClass
import oolong.client.ui.common.windowsizeclass.WindowWidthSizeClass
import oolong.client.ui.page.LocalNavigator
import oolong.client.ui.page.editor.ImageEditorHost
import oolong.client.ui.page.forum.ForumPage
import oolong.client.ui.page.forum.detail.ForumDetailPage
import oolong.client.ui.page.forum.rule.ForumRuleDetailPage
import oolong.client.ui.page.forum.searchpost.ForumSearchPostPage
import oolong.client.ui.page.history.HistoryPage
import oolong.client.ui.page.hottopic.list.HotTopicListPage
import oolong.client.ui.page.login.LoginPage
import oolong.client.ui.page.login.SignedInNoticeHost
import oolong.client.ui.page.main.LocalMainTabs
import oolong.client.ui.page.main.MainPage
import oolong.client.ui.page.main.MainTabs
import oolong.client.ui.page.main.NavigationRail
import oolong.client.ui.page.main.notifications.NotificationsPage
import oolong.client.ui.page.main.rememberMainTabItems
import oolong.client.ui.page.photoview.PhotoViewerHost
import oolong.client.ui.page.reader.ReaderPage
import oolong.client.ui.page.search.SearchPage
import oolong.client.ui.page.settings.SettingsPage
import oolong.client.ui.page.settings.about.AboutPage
import oolong.client.ui.page.settings.account.AccountManagePage
import oolong.client.ui.page.settings.block.BlockRulePage
import oolong.client.ui.page.settings.block.BlockSettingsPage
import oolong.client.ui.page.settings.block.blocklist.BlockListPage
import oolong.client.ui.page.settings.catalog.ControlCatalogPage
import oolong.client.ui.page.settings.crash.CrashLogPage
import oolong.client.ui.page.settings.crash.CrashLogsPage
import oolong.client.ui.page.settings.crash.DiagnosticsPage
import oolong.client.ui.page.settings.custom.CustomSettingsPage
import oolong.client.ui.page.settings.custom.FontSizePage
import oolong.client.ui.page.settings.custom.SearchPlacementPage
import oolong.client.ui.page.settings.habit.HabitSettingsPage
import oolong.client.ui.page.settings.more.MoreSettingsPage
import oolong.client.ui.page.settings.oksign.OKSignSettingsPage
import oolong.client.ui.page.settings.post.PostSettingsPage
import oolong.client.ui.page.settings.privacy.BlackListPage
import oolong.client.ui.page.settings.privacy.DislikedForumsPage
import oolong.client.ui.page.settings.privacy.PrivacySettingsPage
import oolong.client.ui.page.settings.storage.StoragePage
import oolong.client.ui.page.settings.theme.AppThemePage
import oolong.client.ui.page.settings.theme.TranslucentThemePage
import oolong.client.ui.page.settings.update.UpdatePage
import oolong.client.ui.page.subposts.SubPostsPage
import oolong.client.ui.page.subposts.SubPostsSheetPage
import oolong.client.ui.page.thread.ThreadPage
import oolong.client.ui.page.threadstore.ThreadStorePage
import oolong.client.ui.page.user.UserProfilePage
import oolong.client.ui.page.user.edit.EditProfilePage
import oolong.client.ui.page.user.follows.UserFollowsPage
import oolong.client.ui.page.webview.WebViewPage
import oolong.client.ui.widgets.compose.LocalNavigationRailShown

/** Whether the entry being composed is the top of the back stack, as opposed to one below a sheet or dialog. */
val LocalIsTopEntry = compositionLocalOf { true }

/** The page [key] opens. */
@Composable
private fun KeyContent(key: Key) {
    when (key) {
        MainKey -> MainPage()
        is ThreadKey -> ThreadPage(key)
        is ReaderKey -> ReaderPage(key)
        is SubPostsKey -> SubPostsPage(key)
        is SubPostsSheetKey -> SubPostsSheetPage(key)
        is ForumKey -> ForumPage(key)
        is ForumDetailKey -> ForumDetailPage(key)
        is ForumRuleDetailKey -> ForumRuleDetailPage(key)
        is ForumSearchPostKey -> ForumSearchPostPage(key)
        is UserProfileKey -> UserProfilePage(key)
        is UserFollowsKey -> UserFollowsPage(key)
        EditProfileKey -> EditProfilePage()
        is NotificationsKey -> NotificationsPage(initialTab = key.initialTab)
        is SearchKey -> SearchPage(keyword = key.keyword)
        HistoryKey -> HistoryPage()
        ThreadStoreKey -> ThreadStorePage()
        HotTopicListKey -> HotTopicListPage()
        is WebViewKey -> WebViewPage(key.initialUrl)
        LoginKey -> LoginPage()
        SettingsKey -> SettingsPage()
        AccountManageKey -> AccountManagePage()
        AppThemeKey -> AppThemePage()
        TranslucentThemeKey -> TranslucentThemePage()
        CustomSettingsKey -> CustomSettingsPage()
        FontSizeKey -> FontSizePage()
        SearchPlacementKey -> SearchPlacementPage()
        HabitSettingsKey -> HabitSettingsPage()
        PostSettingsKey -> PostSettingsPage()
        BlockSettingsKey -> BlockSettingsPage()
        BlockListKey -> BlockListPage()
        DislikedForumsKey -> DislikedForumsPage()
        PrivacySettingsKey -> PrivacySettingsPage()
        BlackListKey -> BlackListPage()
        is BlockRuleKey -> BlockRulePage(key)
        OKSignSettingsKey -> OKSignSettingsPage()
        MoreSettingsKey -> MoreSettingsPage()
        AboutKey -> AboutPage()
        UpdateKey -> UpdatePage()
        StorageKey -> StoragePage()
        CrashLogsKey -> CrashLogsPage()
        is CrashLogKey -> CrashLogPage(key)
        DiagnosticsKey -> DiagnosticsPage()
        ControlCatalogKey -> ControlCatalogPage()
    }
}

/** Where [this] goes when two panes fit; null for pages that show over others. */
internal val Key.paneRole: PaneRole?
    get() = when (this) {
        MainKey, is ForumKey, is SearchKey, HistoryKey, ThreadStoreKey, HotTopicListKey,
        is ForumSearchPostKey, SettingsKey, is NotificationsKey -> PaneRole.List

        // The reader takes the whole window, laying out its own contents beside the text when wide
        LoginKey, TranslucentThemeKey, is ReaderKey -> PaneRole.Full
        is SubPostsSheetKey -> null
        else -> PaneRole.Detail
    }

/**
 * Whether [this] is read rather than scanned, so it keeps to [MaxReadingWidth] on wide windows.
 * Lists lay themselves out for the width they get; web pages and logging in are sized by the web.
 */
private val Key.readsAtWidth: Boolean
    get() = paneRole == PaneRole.Detail && this !is WebViewKey

private fun Key.metadata(): Map<String, Any> = when (presentation) {
    Presentation.Sheet -> BottomSheetSceneStrategy.bottomSheet()
    Presentation.Page -> paneRole(checkNotNull(paneRole))
}

/**
 * Shows [backStack], with the navigation rail beside it on windows wider than a phone. Scenes
 * are tried in order: overlays first, then any added before the panes, two panes where they
 * fit, and the single pane, which takes whatever is left.
 */
@Composable
fun AppNavDisplay(
    backStack: NavBackStack<NavKey>,
    modifier: Modifier = Modifier,
    sceneStrategies: List<SceneStrategy<NavKey>> = emptyList(),
) {
    val windowSizeClass = LocalWindowSizeClass.current
    // Two panes need the width for both and the height for their bars; a phone held sideways
    // is wide enough but too short, and keeps one pane
    val twoPane = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded &&
        windowSizeClass.heightSizeClass != WindowHeightSizeClass.Compact
    val showRail = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact &&
        backStack.last().key.paneRole != PaneRole.Full

    val tabs = rememberSaveable(saver = MainTabs.Saver) { MainTabs() }
    val notificationCountFlow = LocalNotificationCountFlow.current
    LaunchedEffect(notificationCountFlow) {
        notificationCountFlow.collect { tabs.messageCount = it }
    }

    val listDetailState = rememberListDetailState()
    val onBack: () -> Unit = remember(backStack) { { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) } }
    val strategies = remember(twoPane, sceneStrategies, listDetailState, onBack) {
        listOf(DialogSceneStrategy<NavKey>(onBack), BottomSheetSceneStrategy<NavKey>(onBack)) +
            sceneStrategies +
            listOfNotNull(if (twoPane) listDetailState.strategy else null) +
            FramedSinglePaneSceneStrategy()
    }

    val navigator = remember(backStack) { BackStackNavigator(backStack) }
    SideEffect { navigator.twoPane = twoPane }
    val overlap = PageTransitions.rememberOverlap()
    val ids = backStack.map { (it as Page).id }
    val previousIds = remember { mutableStateOf(ids) }
    val railJump = remember { RailJump() }
    val change = remember(ids) {
        val previous = previousIds.value
        previousIds.value = ids
        BackStackChange(before = previous.toSet(), after = ids.toSet(), jump = railJump.pending)
            .also { railJump.pending = false }
    }
    val isPop = remember(ids) {
        change.before.size > ids.size && change.before.containsAll(ids)
    }
    // The window changing between one pane and two lays the same pages out again; that is not
    // a page opening, so it does not slide
    val previousTwoPane = remember { mutableStateOf(twoPane) }
    val relayout = remember(ids, twoPane) {
        val changed = previousTwoPane.value != twoPane
        previousTwoPane.value = twoPane
        changed
    }
    CompositionLocalProvider(
        LocalMainTabs provides tabs,
        LocalNavigationRailShown provides showRail,
        LocalIsPop provides isPop,
        LocalBackStackChange provides change,
    ) {
        ImageEditorHost {
            PhotoViewerHost {
                // Over the pages, under the viewers: a login page's card stays as it goes back
                SignedInNoticeHost {
                    Row(modifier = modifier.fillMaxSize()) {
                        AnimatedVisibility(
                            visible = showRail,
                            enter = expandHorizontally(Motion.settle()),
                            exit = shrinkHorizontally(Motion.dismiss()),
                        ) {
                            MainRail(backStack, tabs, railJump, showLabels = windowSizeClass.heightSizeClass != WindowHeightSizeClass.Compact)
                        }
                        // The display lays out no container of its own: sheets and dialogs are emitted beside
                        // the pages, so they need a box here, not the row, to cover them. Pages sliding out
                        // end at the rail, rather than passing over it.
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clipToBounds()
                                .then(
                                    if (showRail) {
                                        Modifier.consumeWindowInsets(
                                            WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Start)
                                        )
                                    } else Modifier
                                ),
                        ) {
                            val pagePeek = remember { PagePeek() }
                            val pageOrigins = LocalPageOrigins.current
                            val entries = rememberDecoratedNavEntries(
                                backStack = backStack,
                                entryDecorators = listOf(
                                    rememberSaveableStateHolderNavEntryDecorator(),
                                    rememberViewModelStoreNavEntryDecorator(),
                                ),
                                entryProvider = entryProvider {
                                    entry<Page>(
                                        clazzContentKey = { it.id },
                                        metadata = { it.key.metadata() },
                                    ) { page ->
                                        val isTop = backStack.lastOrNull() == page
                                        val pageNavigator = remember(navigator, page) { navigator.forPage(page) }
                                        CompositionLocalProvider(
                                            LocalIsTopEntry provides isTop,
                                            LocalNavigator provides pageNavigator,
                                        ) {
                                            if (page.key.readsAtWidth) {
                                                ReadingWidth { KeyContent(page.key) }
                                            } else {
                                                KeyContent(page.key)
                                            }
                                        }
                                    }
                                },
                            )
                            val scenes = rememberPageScenes(entries, strategies)
                            PageBackHandler(scenes, pagePeek, onBack)
                            CompositionLocalProvider(LocalPagePeek provides pagePeek) {
                                PageDisplay(
                                    scenes = scenes,
                                    peek = pagePeek,
                                    modifier = Modifier.fillMaxSize(),
                                ) { from, to, peek ->
                                    PageTransitions.between(from, to, overlap, relayout, peek, pageOrigins)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The pane layout, its list width kept across launches once the divider has been moved. */
@Composable
private fun rememberListDetailState(): ListDetailStateHolder {
    val dataStore = LocalContext.current.dataStore
    val coroutineScope = rememberCoroutineScope()
    val holder = remember(dataStore) {
        val state = ListDetailState()
        ListDetailStateHolder(
            state = state,
            strategy = ListDetailSceneStrategy(state) { width ->
                coroutineScope.launch {
                    dataStore.edit {
                        if (width == null) it.remove(ListPaneWidthKey) else it[ListPaneWidthKey] = width.value
                    }
                }
            },
        )
    }
    LaunchedEffect(holder) {
        dataStore.data.first()[ListPaneWidthKey]?.let { holder.state.listWidth = it.dp }
    }
    return holder
}

private class ListDetailStateHolder(val state: ListDetailState, val strategy: ListDetailSceneStrategy<NavKey>)

private val ListPaneWidthKey = floatPreferencesKey("list_pane_width")

/** Set by the rail just before it clears the stack, so the panes switch across instead of stepping back. */
private class RailJump {
    var pending = false
}

/**
 * The rail beside the panes. Choosing a tab closes every open page and shows the main page on
 * that tab; choosing the tab already shown, with nothing open, refreshes it.
 */
@Composable
private fun MainRail(backStack: NavBackStack<NavKey>, tabs: MainTabs, jump: RailJump, showLabels: Boolean) {
    val coroutineScope = rememberCoroutineScope()
    val items = rememberMainTabItems(tabs)
    val selectedIndex = items.indexOfFirst { it.id == tabs.selected }.coerceAtLeast(0)
    NavigationRail(
        currentPosition = selectedIndex,
        onSelect = { index ->
            val item = items[index]
            if (backStack.size == 1 && index == selectedIndex) {
                coroutineScope.emitGlobalEvent(GlobalEvent.Refresh(item.id))
            } else {
                jump.pending = backStack.size > 1
                while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                tabs.selected = item.id
            }
            item.onClick?.invoke()
        },
        navigationItems = items,
        showLabels = showLabels,
    )
}
