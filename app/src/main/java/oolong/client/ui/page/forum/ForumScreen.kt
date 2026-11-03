package oolong.client.ui.page.forum

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import oolong.client.arch.LoadStatus
import oolong.client.model.ForumTab
import oolong.client.ui.common.theme.compose.ExtendedTheme
import oolong.client.ui.common.theme.compose.Symbols
import oolong.client.ui.widgets.compose.FeedCardPlaceholder
import oolong.client.ui.widgets.compose.GlassScaffold
import oolong.client.ui.widgets.compose.LocalScrollEdge
import oolong.client.ui.widgets.compose.ScrollEdgeState
import oolong.client.ui.widgets.compose.scrollsPageContent
import oolong.client.ui.widgets.compose.states.DefaultErrorScreen
import oolong.client.ui.widgets.compose.states.StateScreen

/** What the floating button does, as set in the settings. */
enum class ForumFab { Refresh, BackToTop, Hidden }

/**
 * The forum page: the header over the tabs' lists, going up under the bar as they scroll, and one
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

    // The header is shared by the tabs' lists and drawn over them, moving up as the list in view
    // scrolls until it has gone under the bar; the tabs follow it up and then stay under the bar.
    // The lists are padded for both, so they start below them.
    var headerHeight by remember { mutableIntStateOf(0) }
    var tabsHeight by remember { mutableIntStateOf(0) }
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
                val pageScroll = LocalScrollEdge.current
                ForumTopBar(
                    onEvent = onEvent,
                    menu = forumMenu(onEvent, liked = forum.liked),
                    title = { ForumBarTitle(forum, onEvent) },
                    pinnedOffset = { headerHeight - headerCollapsed(pageScroll, headerHeight) },
                    pinnedContent = {
                        ForumTabRow(
                            tabs = tabs,
                            pagerState = pagerState,
                            sortType = state.sortType,
                            goodClasses = forum.goodClasses,
                            goodClassId = state.goodClassId,
                            onSelect = { coroutineScope.launch { pagerState.animateScrollToPage(it) } },
                            onEvent = onEvent,
                            modifier = Modifier
                                .align(Alignment.Start)
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .onSizeChanged { tabsHeight = it.height },
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
            val pageScroll = LocalScrollEdge.current
            val density = LocalDensity.current
            val listPadding = remember(contentPadding, density) {
                PaddingAbove(contentPadding) { with(density) { (headerHeight + tabsHeight).toDp() } }
            }
            // Lists near their top, out of view, follow the header as the one in view is scrolled,
            // so swiping to them shows it where it was. Ones further down keep their place: the
            // header is gone over them anyway. Only the viewer's scrolling moves them, not a list
            // that has just loaded at its top.
            LaunchedEffect(pageScroll, pagerState) {
                var wasScrolling = false
                snapshotFlow {
                    val scrolling = listStates[tabs.getOrNull(pagerState.currentPage)]?.isScrollInProgress == true
                    scrolling to headerCollapsed(pageScroll, headerHeight)
                }.collect { (scrolling, collapsed) ->
                    // While scrolling, and once more as it stops, for where it stopped
                    val follow = scrolling || wasScrolling
                    wasScrolling = scrolling
                    if (!follow || pagerState.isScrollInProgress) return@collect
                    tabs.forEachIndexed { index, tab ->
                        if (index == pagerState.currentPage) return@forEachIndexed
                        val other = listStates[tab] ?: return@forEachIndexed
                        val scrolled = other.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }?.offset?.let { -it }
                        if (scrolled != null && scrolled < headerHeight && scrolled != collapsed) other.requestScrollToItem(0, collapsed)
                    }
                }
            }
            // Where the header was when a swipe to another tab began, for a list that only loads
            // once it is there and would otherwise open the header from the top
            var keptCollapse by remember { mutableIntStateOf(0) }
            LaunchedEffect(pageScroll, pagerState) {
                snapshotFlow { pagerState.isScrollInProgress }.filter { it }.collect {
                    keptCollapse = headerCollapsed(pageScroll, headerHeight)
                }
            }
            val accent = LocalForumAccent.current
            Box(modifier = Modifier.pullRefresh(pullRefreshState)) {
                // The forum's colour from the top of the page down the header, behind everything
                if (accent.isSpecified) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .layout { measurable, constraints ->
                                val height = contentPadding.calculateTopPadding().roundToPx() + headerHeight
                                val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
                                layout(placeable.width, placeable.height) {
                                    placeable.place(0, -headerCollapsed(pageScroll, headerHeight))
                                }
                            }
                            .background(
                                Brush.verticalGradient(
                                    listOf(accent.copy(alpha = if (ExtendedTheme.colors.isNightMode) ForumWashNight else ForumWash), Color.Transparent)
                                )
                            )
                    )
                }
                Box {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        key = { tabs[it].toString() },
                        verticalAlignment = Alignment.Top,
                    ) { page ->
                        val tab = tabs[page]
                        val list = state.list(tab)
                        val listState = listStates.getValue(tab)
                        val loaded = list.items.isNotEmpty()
                        LaunchedEffect(tab, loaded) {
                            val atTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                            if (loaded && page == pagerState.currentPage && atTop && keptCollapse > 0) {
                                listState.requestScrollToItem(0, keptCollapse)
                            }
                        }
                        StateScreen(
                            isEmpty = list.items.isEmpty(),
                            isError = list.refresh is LoadStatus.Error,
                            isLoading = list.refresh == LoadStatus.Loading || list.refresh == LoadStatus.Idle,
                            onReload = { onEvent(ForumEvent.Refresh) },
                            modifier = Modifier.fillMaxSize(),
                            loadingScreen = {
                                Column(modifier = Modifier
                                    .fillMaxSize()
                                    .padding(listPadding)) {
                                    repeat(4) { FeedCardPlaceholder() }
                                }
                            },
                            errorScreen = {
                                Box(modifier = Modifier.padding(listPadding)) { DefaultErrorScreen(this@StateScreen) }
                            },
                        ) {
                            ForumThreadList(
                                tab = tab,
                                list = list,
                                onEvent = onEvent,
                                listState = listState,
                                contentPadding = listPadding,
                                ruleTitle = forum.ruleTitle.takeIf { tab == ForumTab.Latest },
                            )
                        }
                    }
                }
                ForumHeader(
                    forum = forum,
                    loggedIn = state.loggedIn,
                    onEvent = onEvent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(0, contentPadding.calculateTopPadding().roundToPx() - headerCollapsed(pageScroll, headerHeight)) }
                        .onSizeChanged { headerHeight = it.height }
                        .scrollsPageContent(),
                )
                PullRefreshIndicator(
                    refreshing = refreshing,
                    state = pullRefreshState,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = listPadding.calculateTopPadding()),
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

/** How much of the header has gone under the bar: how far the list in view has scrolled, up to all of it. */
private fun headerCollapsed(page: ScrollEdgeState?, headerHeight: Int): Int =
    (page?.scrolled ?: 0).coerceIn(0, headerHeight)

/** [base], with [extra] more room above. */
private class PaddingAbove(private val base: PaddingValues, private val extra: () -> Dp) : PaddingValues {
    override fun calculateTopPadding(): Dp = base.calculateTopPadding() + extra()
    override fun calculateBottomPadding(): Dp = base.calculateBottomPadding()
    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = base.calculateLeftPadding(layoutDirection)
    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = base.calculateRightPadding(layoutDirection)
}

/** The forum's colour at the top of its page. */
private const val ForumWash = 0.18f
private const val ForumWashNight = 0.28f
