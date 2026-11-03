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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.ScaffoldState
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
import oolong.client.arch.LoadStatus
import oolong.client.model.ForumTab
import oolong.client.ui.common.theme.compose.ExtendedTheme
import oolong.client.ui.common.theme.compose.Symbols
import oolong.client.ui.widgets.compose.EndPull
import oolong.client.ui.widgets.compose.FeedCardPlaceholder
import oolong.client.ui.widgets.compose.GlassScaffold
import oolong.client.ui.widgets.compose.GlassTopBarRowHeight
import oolong.client.ui.widgets.compose.LocalPageAccent
import oolong.client.ui.widgets.compose.LocalPageScroll
import oolong.client.ui.widgets.compose.PageScrollState
import oolong.client.ui.widgets.compose.PullRefresh
import oolong.client.ui.widgets.compose.onPageAccent
import oolong.client.ui.widgets.compose.pageAccentOrPrimary
import oolong.client.ui.widgets.compose.rememberAvatarAccent
import oolong.client.ui.widgets.compose.rememberPageScrollState
import oolong.client.ui.widgets.compose.rememberScrollProgress
import oolong.client.ui.widgets.compose.scrollsPageContent
import oolong.client.ui.widgets.compose.states.DefaultErrorScreen
import oolong.client.ui.widgets.compose.states.StateScreen
import kotlinx.coroutines.launch

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
    // The header's place is the tabs' shared state: the viewer's scrolling of the open tab's list
    // moves it, and every other list is kept in line with it (see below)
    var sharedCollapse by remember { mutableIntStateOf(0) }
    val forumScroll = rememberPageScrollState()
    // Once the header has gone, the bar's row follows the scroll away and back; the tabs stay
    val away = rememberScrollProgress(
        away = GlassTopBarRowHeight,
        enabled = { headerHeight > 0 && sharedCollapse >= headerHeight },
        page = forumScroll,
    )
    val currentList = state.list(state.selectedTab)
    val refreshing = currentList.refresh == LoadStatus.Loading && currentList.items.isNotEmpty()

    val accent = rememberAvatarAccent(forum.avatar)
    CompositionLocalProvider(LocalPageAccent provides accent) {
        GlassScaffold(
            scaffoldState = scaffoldState,
            backgroundColor = Color.Transparent,
            pageScroll = forumScroll,
            modifier = modifier.fillMaxSize(),
            topBar = {
                ForumTopBar(
                    onEvent = onEvent,
                    menu = forumMenu(onEvent, liked = forum.liked),
                    title = { ForumBarTitle(forum, onEvent) },
                    hiddenFraction = { away.value },
                    pinnedOffset = { headerHeight - headerCollapsed(forumScroll, headerHeight, sharedCollapse) },
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
                                // The room the row takes, its margins too, is what the lists leave for it
                                .onSizeChanged { tabsHeight = it.height }
                                .padding(horizontal = 12.dp, vertical = 4.dp),
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
                            .background(pageAccentOrPrimary(), CircleShape)
                            .clickable {
                                coroutineScope.launch {
                                    listStates[state.selectedTab]?.animateScrollToItem(0)
                                    if (fab == ForumFab.Refresh) onEvent(ForumEvent.Refresh)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            tint = onPageAccent(),
                            imageVector = if (fab == ForumFab.Refresh) Symbols.Refresh else Symbols.VerticalAlignTop,
                            contentDescription = null
                        )
                    }
                }
            },
        ) { contentPadding ->
            val density = LocalDensity.current
            val listPadding = remember(contentPadding, density) {
                PaddingAbove(contentPadding) { with(density) { (headerHeight + tabsHeight).toDp() } }
            }
            // Every other list is kept in line with the header as the open one scrolls, so swiping
            // to another tab neither moves the header nor shows that list at some other place
            // under it. Lists not loaded yet are put in place too, and load there.
            LaunchedEffect(forumScroll, pagerState) {
                var wasScrolling = false
                snapshotFlow {
                    val scrolling = listStates[tabs.getOrNull(pagerState.currentPage)]?.isScrollInProgress == true
                    scrolling to headerCollapsed(forumScroll, headerHeight, sharedCollapse)
                }.collect { (scrolling, collapsed) ->
                    // While scrolling, and once more as it stops, for where it stopped; not when a
                    // list that has just loaded reports its top
                    val follow = scrolling || wasScrolling
                    wasScrolling = scrolling
                    if (!follow || pagerState.isScrollInProgress) return@collect
                    sharedCollapse = collapsed
                    tabs.forEachIndexed { index, tab ->
                        if (index != pagerState.currentPage) listStates[tab]?.alignWithHeader(collapsed, headerHeight)
                    }
                }
            }
            val accent = LocalPageAccent.current
            Box {
                // The forum's colour from the top of the page down the header, behind everything
                if (accent.isSpecified) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .layout { measurable, constraints ->
                                val height = contentPadding.calculateTopPadding().roundToPx() + headerHeight
                                val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
                                layout(placeable.width, placeable.height) {
                                    placeable.place(0, -headerCollapsed(forumScroll, headerHeight, sharedCollapse))
                                }
                            }
                            .background(
                                Brush.verticalGradient(
                                    listOf(accent.copy(alpha = if (ExtendedTheme.colors.isNightMode) ForumWashNight else ForumWash), Color.Transparent)
                                )
                            )
                    )
                }
                // The header and the lists follow a pull; the colour behind them stays
                PullRefresh(
                    refreshing = refreshing,
                    onRefresh = { onEvent(ForumEvent.Refresh) },
                    indicatorTop = contentPadding.calculateTopPadding(),
                    endPull = if (currentList.hasMore) EndPull.None else EndPull.Tease,
                ) {
                    Box {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            key = { tabs[it].toString() },
                            verticalAlignment = Alignment.Top,
                        ) { page ->
                            // Only the open tab's list tells the page how far it has scrolled
                            CompositionLocalProvider(LocalPageScroll provides forumScroll.takeIf { page == pagerState.currentPage }) {
                                val tab = tabs[page]
                                val list = state.list(tab)
                                val listState = listStates.getValue(tab)
                                val loaded = list.items.isNotEmpty()
                                // A list that loads once its tab is open starts in line with the header too
                                LaunchedEffect(tab, loaded) {
                                    if (loaded) listState.alignWithHeader(sharedCollapse, headerHeight)
                                }
                                StateScreen(
                                    isEmpty = list.items.isEmpty(),
                                    isError = list.refresh is LoadStatus.Error,
                                    isLoading = list.refresh == LoadStatus.Loading || list.refresh == LoadStatus.Idle,
                                    onReload = { onEvent(ForumEvent.Refresh) },
                                    modifier = Modifier.fillMaxSize(),
                                    loadingScreen = {
                                        // Under the header where it is, not where it is fully open
                                        Column(modifier = Modifier
                                            .fillMaxSize()
                                            .offset { IntOffset(0, -sharedCollapse) }
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
                    }
                    ForumHeader(
                        forum = forum,
                        loggedIn = state.loggedIn,
                        onEvent = onEvent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset { IntOffset(0, contentPadding.calculateTopPadding().roundToPx() - headerCollapsed(forumScroll, headerHeight, sharedCollapse)) }
                            .onSizeChanged { headerHeight = it.height }
                            .scrollsPageContent(),
                    )
                }
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

/**
 * How much of the header has gone under the bar: how far the open tab's list has scrolled, up to
 * all of it, or [held] while it has no list to tell, as while it loads.
 */
private fun headerCollapsed(page: PageScrollState?, headerHeight: Int, held: Int): Int =
    (page?.scrolled ?: held).coerceIn(0, headerHeight)

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

/**
 * Puts this list where it belongs under a header that is [collapsed] of [headerHeight] gone: just
 * as far down while the header still shows, since a header in view means the top of every tab;
 * once it has gone, anywhere below it, so a list further down keeps its place.
 */
private fun LazyListState.alignWithHeader(collapsed: Int, headerHeight: Int) {
    if (headerHeight <= 0) return
    // Item offsets start at the end of the top padding. A list not laid out yet, or out of view,
    // goes by its own scroll position; past the first item it is far down.
    val scrolled = layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }?.offset?.let { -it }
        ?: if (firstVisibleItemIndex == 0) firstVisibleItemScrollOffset else Int.MAX_VALUE
    val target = if (collapsed < headerHeight) collapsed else maxOf(scrolled, headerHeight)
    if (target != scrolled) requestScrollToItem(0, target)
}
