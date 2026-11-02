package com.huanchengfly.tieba.post.ui.page.forum

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.ScaffoldState
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.huanchengfly.tieba.post.arch.LoadStatus
import com.huanchengfly.tieba.post.model.ForumTab
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.common.theme.compose.Symbols
import com.huanchengfly.tieba.post.ui.widgets.compose.FeedCardPlaceholder
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassScaffold
import com.huanchengfly.tieba.post.ui.widgets.compose.rememberCollapsingScrollBehavior
import com.huanchengfly.tieba.post.ui.widgets.compose.states.DefaultErrorScreen
import com.huanchengfly.tieba.post.ui.widgets.compose.states.StateScreen
import kotlinx.coroutines.launch

/** What the floating button does, as set in the settings. */
enum class ForumFab { Refresh, BackToTop, Hidden }

/**
 * The forum page: the header and tabs in the top bar, collapsing as the threads scroll, and one
 * list of threads per tab.
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ForumScreen(
    state: ForumState,
    onEvent: (ForumEvent) -> Unit,
    modifier: Modifier = Modifier,
    fab: ForumFab = ForumFab.Refresh,
    scaffoldState: ScaffoldState = rememberScaffoldState(),
) {
    val forum = state.forum
    if (forum == null) {
        ForumLoading(state, onEvent, modifier)
        return
    }
    val tabs = state.tabs
    val pagerState = rememberPagerState(initialPage = tabs.indexOf(state.selectedTab).coerceAtLeast(0)) { tabs.size }
    val currentTabs by rememberUpdatedState(tabs)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            currentTabs.getOrNull(page)?.let { onEvent(ForumEvent.SelectTab(it)) }
        }
    }
    val coroutineScope = rememberCoroutineScope()
    // Saved, so each tab keeps its place while another page is open on top
    val listStates = tabs.associateWith { tab -> key(tab) { rememberLazyListState() } }

    // The header and tabs ride in the bar, so the lists scroll under all of them and the header
    // collapses by shrinking the bar, its name docking as the bar's title
    val collapse = rememberCollapsingScrollBehavior()
    val currentList = state.list(state.selectedTab)
    val refreshing = currentList.refresh == LoadStatus.Loading && currentList.items.isNotEmpty()
    val pullRefreshState = rememberPullRefreshState(refreshing, onRefresh = { onEvent(ForumEvent.Refresh) })

    val accent = rememberForumAccent(forum.avatar)
    CompositionLocalProvider(LocalForumAccent provides accent) {
        GlassScaffold(
            scaffoldState = scaffoldState,
            backgroundColor = Color.Transparent,
            modifier = modifier.fillMaxSize(),
            topBar = {
                ForumTopBar(
                    onEvent = onEvent,
                    menu = forumMenu(onEvent, liked = forum.liked),
                    content = {
                        ForumHeader(
                            forum = forum,
                            loggedIn = state.loggedIn,
                            onEvent = onEvent,
                            collapse = collapse.state,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ForumTabRow(
                            tabs = tabs,
                            pagerState = pagerState,
                            sortType = state.sortType,
                            onSelect = { coroutineScope.launch { pagerState.animateScrollToPage(it) } },
                            onEvent = onEvent,
                            modifier = Modifier.align(Alignment.Start),
                        )
                    },
                )
            },
            floatingActionButton = {
                if (fab != ForumFab.Hidden) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .shadow(elevation = 8.dp, shape = CircleShape)
                            .background(forumAccentOrPrimary(), CircleShape)
                            .clickable {
                                coroutineScope.launch {
                                    listStates[state.selectedTab]?.animateScrollToItem(0)
                                    if (fab == ForumFab.Refresh) onEvent(ForumEvent.Refresh)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            tint = onForumAccent(),
                            imageVector = if (fab == ForumFab.Refresh) Symbols.Refresh else Symbols.VerticalAlignTop,
                            contentDescription = null
                        )
                    }
                }
            },
        ) { contentPadding ->
            // Pull to refresh is outside the header, so a pull at the top opens the header before it refreshes
            Box(modifier = Modifier.pullRefresh(pullRefreshState)) {
                Box(modifier = Modifier.nestedScroll(collapse.nestedScrollConnection)) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        key = { tabs[it].toString() },
                        verticalAlignment = Alignment.Top,
                    ) { page ->
                        val tab = tabs[page]
                        val list = state.list(tab)
                        StateScreen(
                            isEmpty = list.items.isEmpty(),
                            isError = list.refresh is LoadStatus.Error,
                            isLoading = list.refresh == LoadStatus.Loading || list.refresh == LoadStatus.Idle,
                            onReload = { onEvent(ForumEvent.Refresh) },
                            modifier = Modifier.fillMaxSize(),
                            loadingScreen = {
                                Column(modifier = Modifier
                                    .fillMaxSize()
                                    .padding(contentPadding)) {
                                    repeat(4) { FeedCardPlaceholder() }
                                }
                            },
                            errorScreen = {
                                Box(modifier = Modifier.padding(contentPadding)) { DefaultErrorScreen(this@StateScreen) }
                            },
                        ) {
                            ForumThreadList(
                                tab = tab,
                                list = list,
                                onEvent = onEvent,
                                listState = listStates.getValue(tab),
                                contentPadding = contentPadding,
                                ruleTitle = forum.ruleTitle.takeIf { tab == ForumTab.Latest },
                                goodClasses = forum.goodClasses.takeIf { tab == ForumTab.Good },
                                goodClassId = state.goodClassId,
                            )
                        }
                    }
                }
                PullRefreshIndicator(
                    refreshing = refreshing,
                    state = pullRefreshState,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = contentPadding.calculateTopPadding()),
                    backgroundColor = ExtendedTheme.colors.refreshIndicator,
                    contentColor = ExtendedTheme.colors.emphasis,
                )
            }
        }
    }
}

/** The page before the forum is known: placeholders while it loads, or the error it failed with. */
@Composable
private fun ForumLoading(state: ForumState, onEvent: (ForumEvent) -> Unit, modifier: Modifier) {
    GlassScaffold(
        backgroundColor = Color.Transparent,
        modifier = modifier.fillMaxSize(),
        topBar = {
            ForumTopBar(
                onEvent = onEvent,
                canSearch = false,
                menu = forumMenu(onEvent, liked = false),
            )
        },
    ) { contentPadding ->
        val error = state.firstLoad as? LoadStatus.Error
        if (error != null) {
            StateScreen(
                isEmpty = true,
                isError = true,
                isLoading = false,
                onReload = { onEvent(ForumEvent.Refresh) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            ) {}
        } else {
            Column(modifier = Modifier.padding(contentPadding)) {
                ForumHeaderPlaceholder(
                    forumName = state.forumName,
                    loggedIn = state.loggedIn,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                )
                ForumTabRowPlaceholder()
                repeat(4) { FeedCardPlaceholder() }
            }
        }
    }
}
