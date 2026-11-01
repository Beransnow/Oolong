package com.huanchengfly.tieba.post.ui.page.forum

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.ScaffoldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlin.math.roundToInt
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.background
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.huanchengfly.tieba.post.arch.LoadStatus
import com.huanchengfly.tieba.post.model.ForumTab
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.widgets.compose.FeedCardPlaceholder
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassScaffold
import com.huanchengfly.tieba.post.ui.widgets.compose.Sizes
import com.huanchengfly.tieba.post.ui.widgets.compose.floatingGlass
import com.huanchengfly.tieba.post.ui.widgets.compose.states.DefaultErrorScreen
import com.huanchengfly.tieba.post.ui.widgets.compose.states.StateScreen
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min

/** What the floating button does, as set in the settings. */
enum class ForumFab { Refresh, BackToTop, Hidden }

/**
 * The forum page: the header and tabs in the top bar, collapsing as the threads scroll, and one
 * list of threads per tab.
 */
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
    val listStates = remember { mutableMapOf<ForumTab, LazyListState>() }
    fun listStateOf(tab: ForumTab) = listStates.getOrPut(tab) { LazyListState() }

    val density = LocalDensity.current
    var heightOffset by rememberSaveable { mutableFloatStateOf(0f) }
    var headerHeight by rememberSaveable { mutableFloatStateOf(with(density) { (Sizes.Large + 16.dp * 2).toPx() }) }
    val headerShown by remember { derivedStateOf { heightOffset.absoluteValue < headerHeight } }
    // The header and tabs ride in the bar, so the lists scroll under all of them and the header
    // collapses by shrinking the bar
    val currentTab by rememberUpdatedState(state.selectedTab)
    val headerNestedScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val list = listStateOf(currentTab)
                val atTop = list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset == 0
                // Pulling down at the top of the list brings the header back before the pull
                // can refresh the list
                if (available.y < 0 || (available.y > 0 && atTop && heightOffset < 0f)) {
                    val previous = heightOffset
                    heightOffset = (heightOffset + available.y).coerceIn(-headerHeight, 0f)
                    if (previous != heightOffset) return Offset(0f, heightOffset - previous)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f) {
                    // The list may have consumed less than was offered before it scrolled
                    val previous = heightOffset
                    heightOffset = min(heightOffset + available.y, 0f)
                    if (previous != heightOffset) return available.copy(x = 0f)
                }
                return Offset.Zero
            }
        }
    }

    val accent = rememberForumAccent(forum.avatar)
    CompositionLocalProvider(LocalForumAccent provides accent) {
    GlassScaffold(
        scaffoldState = scaffoldState,
        backgroundColor = Color.Transparent,
        modifier = modifier.fillMaxSize(),
        topBar = {
            ForumTopBar(
                forumName = forum.name,
                showTitle = !headerShown,
                onEvent = onEvent,
                menuContent = { ForumMenu(onEvent, liked = forum.liked, dismiss = { dismiss() }) },
                content = {
                    // Measured whole and shown from the bottom up as it collapses, in one pass, so
                    // the first frame already shows all of it whatever height was last saved
                    Box(modifier = Modifier
                        .clipToBounds()
                        .layout { measurable, constraints ->
                            val header = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                            if (headerHeight != header.height.toFloat()) headerHeight = header.height.toFloat()
                            val shown = (header.height + heightOffset).roundToInt().coerceIn(0, header.height)
                            layout(header.width, shown) { header.place(0, shown - header.height) }
                        }) {
                        Box {
                            ForumHeader(
                                forum = forum,
                                loggedIn = state.loggedIn,
                                onEvent = onEvent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                            )
                        }
                    }
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
                                listStateOf(state.selectedTab).animateScrollToItem(0)
                                if (fab == ForumFab.Refresh) onEvent(ForumEvent.Refresh)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        tint = onForumAccent(),
                        imageVector = if (fab == ForumFab.Refresh) Icons.Rounded.Refresh else Icons.Rounded.VerticalAlignTop,
                        contentDescription = null
                    )
                }
            }
        },
    ) { contentPadding ->
        Box(modifier = Modifier.nestedScroll(headerNestedScroll)) {
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
                        listState = listStateOf(tab),
                        contentPadding = contentPadding,
                        ruleTitle = forum.ruleTitle.takeIf { tab == ForumTab.Latest },
                        goodClasses = forum.goodClasses.takeIf { tab == ForumTab.Good },
                        goodClassId = state.goodClassId,
                    )
                }
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
                forumName = state.forumName,
                showTitle = false,
                onEvent = onEvent,
                canSearch = false,
                menuContent = { ForumMenu(onEvent, liked = false, dismiss = { dismiss() }) },
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
