package com.huanchengfly.tieba.post.ui.page.search

import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ProvideTextStyle
import androidx.compose.material.Tab
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachIndexed
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.model.SearchHistoryItem
import com.huanchengfly.tieba.post.model.SearchOrder
import com.huanchengfly.tieba.post.model.SearchedPost
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.common.theme.compose.TiebaLiteTheme
import com.huanchengfly.tieba.post.ui.navigation.ForumKey
import com.huanchengfly.tieba.post.ui.navigation.Navigator
import com.huanchengfly.tieba.post.ui.navigation.ThreadKey
import com.huanchengfly.tieba.post.ui.navigation.UserProfileKey
import com.huanchengfly.tieba.post.ui.page.LocalNavigator
import com.huanchengfly.tieba.post.ui.page.ProvideNavigator
import com.huanchengfly.tieba.post.ui.page.search.forum.SearchForumList
import com.huanchengfly.tieba.post.ui.page.search.thread.SearchThreadResults
import com.huanchengfly.tieba.post.ui.page.search.user.SearchUserList
import com.huanchengfly.tieba.post.ui.widgets.compose.Button
import com.huanchengfly.tieba.post.ui.widgets.compose.Container
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassScaffold
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassTopBar
import com.huanchengfly.tieba.post.ui.widgets.compose.LazyLoadHorizontalPager
import com.huanchengfly.tieba.post.ui.widgets.compose.MyBackHandler
import com.huanchengfly.tieba.post.ui.widgets.compose.PagerTabIndicator
import com.huanchengfly.tieba.post.ui.widgets.compose.SearchBox
import com.huanchengfly.tieba.post.ui.widgets.compose.TabClickMenu
import com.huanchengfly.tieba.post.ui.widgets.compose.TabRow
import com.huanchengfly.tieba.post.ui.widgets.compose.picker.ListSinglePicker
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.launch

@Immutable
data class SearchPageItem(
    val id: String,
    val text: @Composable (selected: Boolean) -> Unit,
    val content: @Composable (contentPadding: PaddingValues) -> Unit,
    val supportSort: Boolean = false,
    val sortTypes: ImmutableMap<String, Int> = persistentMapOf(),
    val selectedSortType: () -> Int = { -1 },
    val onSelectedSortTypeChange: (Int) -> Unit = {},
)

@Composable
fun SearchPage(
    navigator: Navigator = LocalNavigator.current,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state.keyword) {
        if (state.keyword.isNotEmpty()) input = state.keyword
    }
    MyBackHandler(
        enabled = state.keyword.isNotEmpty(),
    ) {
        viewModel.search("")
    }

    ProvideNavigator(navigator = navigator) {
        SearchScreen(
            state = state,
            input = input,
            onInputChange = {
                input = it
                viewModel.onInputChange(it)
            },
            onSearch = {
                input = it
                viewModel.search(it)
            },
            onBack = {
                if (state.keyword.isEmpty()) navigator.navigateUp() else viewModel.search("")
            },
            onOpenTab = viewModel::open,
            onRefreshTab = viewModel::refresh,
            onLoadMoreThreads = viewModel::loadMoreThreads,
            onSetOrder = viewModel::setOrder,
            onForget = viewModel::forget,
            onClearHistory = viewModel::clearHistory,
            onOpenForum = { navigator.navigate(ForumKey(it)) },
            onOpenUser = { navigator.navigate(UserProfileKey(it)) },
            onOpenThread = { navigator.navigate(ThreadKey(threadId = it.threadId)) },
        )
    }
}

/**
 * The search page: the history and suggestions while the user types a keyword, then the forums,
 * threads and users found, in tabs.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(
    state: SearchState,
    input: String,
    onInputChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onBack: () -> Unit,
    onOpenTab: (SearchTab) -> Unit,
    onRefreshTab: (SearchTab) -> Unit,
    onLoadMoreThreads: () -> Unit,
    onSetOrder: (SearchOrder) -> Unit,
    onForget: (SearchHistoryItem) -> Unit,
    onClearHistory: () -> Unit,
    onOpenForum: (forumName: String) -> Unit,
    onOpenUser: (uid: Long) -> Unit,
    onOpenThread: (SearchedPost) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val searching = state.keyword.isNotEmpty()

    val orderNames = persistentMapOf(
        stringResource(R.string.title_search_order_new) to SearchOrder.Newest,
        stringResource(R.string.title_search_order_old) to SearchOrder.Oldest,
        stringResource(R.string.title_search_order_relevant) to SearchOrder.Relevant,
    )
    val pages = persistentListOf(
        SearchPageItem(
            id = "forum",
            text = { TabText(R.string.title_search_forum, it) },
            content = { padding ->
                LaunchedEffect(state.keyword) { onOpenTab(SearchTab.Forums) }
                SearchForumList(
                    state = state.forums,
                    onRefresh = { onRefreshTab(SearchTab.Forums) },
                    onOpen = { onOpenForum(it.name) },
                    contentPadding = padding,
                )
            }
        ),
        SearchPageItem(
            id = "thread",
            text = { TabText(R.string.title_search_thread, it) },
            content = { padding ->
                LaunchedEffect(state.keyword) { onOpenTab(SearchTab.Threads) }
                SearchThreadResults(
                    list = state.threads,
                    keyword = state.keyword,
                    onRefresh = { onRefreshTab(SearchTab.Threads) },
                    onLoadMore = onLoadMoreThreads,
                    onOpen = onOpenThread,
                    onOpenUser = { author -> author.id.takeIf { it != 0L }?.let(onOpenUser) },
                    onOpenForum = onOpenForum,
                    contentPadding = padding,
                )
            },
            supportSort = true,
            sortTypes = orderNames.mapValues { it.value.ordinal }.toImmutableMap(),
            selectedSortType = { state.order.ordinal },
            onSelectedSortTypeChange = { onSetOrder(SearchOrder.entries[it]) }
        ),
        SearchPageItem(
            id = "user",
            text = { TabText(R.string.title_search_user, it) },
            content = { padding ->
                LaunchedEffect(state.keyword) { onOpenTab(SearchTab.Users) }
                SearchUserList(
                    state = state.users,
                    onRefresh = { onRefreshTab(SearchTab.Users) },
                    onOpen = { onOpenUser(it.id) },
                    contentPadding = padding,
                )
            }
        ),
    )
    val pagerState = rememberPagerState { pages.size }
    GlassScaffold(
        topBar = {
            GlassTopBar(
                title = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .padding(end = 12.dp)
                    ) {
                        // The bar styles its title slot as a title; the field keeps body text.
                        ProvideTextStyle(MaterialTheme.typography.body1) {
                            SearchTopBar(
                                keyword = input,
                                onKeywordChange = onInputChange,
                                onKeywordSubmit = onSearch,
                                onBack = onBack,
                            )
                        }
                    }
                },
            ) {
                if (searching) {
                    SearchTabRow(pagerState = pagerState, pages = pages)
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (searching) {
                LazyLoadHorizontalPager(
                    state = pagerState,
                    key = { pages[it].id },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    pages[it].content(paddingValues)
                }
            } else if (state.suggestions.isNotEmpty() && input.isNotBlank()) {
                SearchSuggestionList(
                    suggestions = state.suggestions,
                    contentPadding = paddingValues,
                    onItemClick = onSearch,
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(paddingValues)
                ) {
                    Container {
                        SearchHistoryList(
                            searchHistories = state.history,
                            onSearchHistoryClick = { onSearch(it.keyword) },
                            expanded = expanded,
                            onToggleExpand = { expanded = !expanded },
                            onDelete = onForget,
                            onClear = onClearHistory,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabText(@StringRes text: Int, selected: Boolean) {
    Text(text = stringResource(id = text), fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchSuggestionList(
    suggestions: ImmutableList<String>,
    onItemClick: (String) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = contentPadding,
    ) {
        items(
            items = suggestions,
            key = { it }
        ) {
            Container(
                modifier = Modifier.animateItem()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .clickable {
                            onItemClick(it)
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = stringResource(id = R.string.desc_search_sug, it),
                        tint = ExtendedTheme.colors.text
                    )

                    Text(
                        text = it,
                        style = MaterialTheme.typography.subtitle2,
                        modifier = Modifier.weight(1f),
                        color = ExtendedTheme.colors.text
                    )
                }
            }
        }
    }
}

@Preview("SearchSuggestionList", backgroundColor = 0xFFFFFFFF)
@Composable
private fun SearchSuggestionListPreview() {
    TiebaLiteTheme {
        Box(
            modifier = Modifier.background(ExtendedTheme.colors.topBar)
        ) {
            SearchSuggestionList(
                suggestions = persistentListOf("1", "2", "3"),
                onItemClick = {}
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ColumnScope.SearchTabRow(
    pagerState: PagerState,
    pages: ImmutableList<SearchPageItem>,
) {
    val coroutineScope = rememberCoroutineScope()
    TabRow(
        selectedTabIndex = pagerState.currentPage,
        indicator = { tabPositions ->
            PagerTabIndicator(
                pagerState = pagerState,
                tabPositions = tabPositions
            )
        },
        divider = {},
        backgroundColor = Color.Transparent,
        contentColor = ExtendedTheme.colors.onTopBar,
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .width(76.dp * pages.size),
    ) {
        pages.fastForEachIndexed { index, item ->
            val tabTextStyle =
                MaterialTheme.typography.button.copy(fontSize = 13.sp, letterSpacing = 0.sp)

            if (item.supportSort) {
                TabClickMenu(
                    selected = pagerState.currentPage == index,
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    text = {
                        ProvideTextStyle(value = tabTextStyle) {
                            item.text(pagerState.currentPage == index)
                        }
                    },
                    menuContent = {
                        ListSinglePicker(
                            itemTitles = item.sortTypes.keys.toImmutableList(),
                            itemValues = item.sortTypes.values.toImmutableList(),
                            selectedPosition = item.sortTypes.values.indexOf(item.selectedSortType()),
                            onItemSelected = { _, _, value, changed ->
                                if (changed) item.onSelectedSortTypeChange(value)
                                dismiss()
                            }
                        )
                    }
                )
            } else {
                Tab(
                    text = {
                        ProvideTextStyle(value = tabTextStyle) {
                            item.text(pagerState.currentPage == index)
                        }
                    },
                    selected = pagerState.currentPage == index,
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun SearchHistoryList(
    searchHistories: ImmutableList<SearchHistoryItem>,
    onSearchHistoryClick: (SearchHistoryItem) -> Unit,
    expanded: Boolean = false,
    onToggleExpand: () -> Unit = {},
    onDelete: (SearchHistoryItem) -> Unit = {},
    onClear: () -> Unit = {},
) {
    val hasItem = remember(searchHistories) {
        searchHistories.isNotEmpty()
    }
    val hasMore = remember(searchHistories) {
        searchHistories.size > 6
    }
    val showItem = remember(expanded, hasMore, searchHistories) {
        if (!expanded && hasMore) searchHistories.take(6) else searchHistories
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(id = R.string.title_search_history),
                modifier = Modifier
                    .weight(1f),
                style = MaterialTheme.typography.subtitle1
            )
            if (hasItem) {
                Text(
                    text = stringResource(id = R.string.button_clear_all),
                    modifier = Modifier.clickable(onClick = onClear),
                    style = MaterialTheme.typography.button
                )
            }
        }
        FlowRow(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .animateContentSize(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            showItem.fastForEach { searchHistory ->
                Box(
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(100))
                        .combinedClickable(
                            onClick = { onSearchHistoryClick(searchHistory) },
                            onLongClick = { onDelete(searchHistory) }
                        )
                        .background(ExtendedTheme.colors.chip)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = searchHistory.keyword
                    )
                }
            }
        }
        if (hasMore) {
            Button(
                onClick = onToggleExpand,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(
                    backgroundColor = Color.Transparent,
                    contentColor = ExtendedTheme.colors.text
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(
                            id = if (expanded) R.string.button_expand_less_history else R.string.button_expand_more_history
                        ),
                        style = MaterialTheme.typography.button,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        if (!hasItem) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(id = R.string.tip_empty),
                    color = ExtendedTheme.colors.textDisabled,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
private fun SearchTopBar(
    keyword: String,
    onKeywordChange: (String) -> Unit,
    onKeywordSubmit: (String) -> Unit = {},
    onBack: () -> Unit = {},
) {
    SearchBox(
        keyword = keyword,
        onKeywordChange = onKeywordChange,
        modifier = Modifier.fillMaxSize(),
        onKeywordSubmit = onKeywordSubmit,
        placeholder = {
            Text(
                text = stringResource(id = R.string.hint_search),
                color = ExtendedTheme.colors.onTopBarSurface.copy(alpha = ContentAlpha.medium)
            )
        },
        prependIcon = {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(100))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false, 24.dp),
                        role = Role.Button,
                        onClick = onBack
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(id = R.string.button_back)
                )
            }
        },
        shape = RoundedCornerShape(6.dp)
    )
}

@Preview("SearchBox")
@Composable
fun PreviewSearchBox() {
    var keyword by remember { mutableStateOf("") }
    TiebaLiteTheme {
        Box(
            modifier = Modifier
                .height(64.dp)
                .background(ExtendedTheme.colors.topBar)
                .padding(vertical = 8.dp, horizontal = 16.dp)
        ) {
            SearchTopBar(
                keyword = keyword,
                onKeywordChange = { keyword = it }
            )
        }
    }
}

@Preview("SearchHistoryList")
@Composable
fun PreviewSearchHistoryList() {
    TiebaLiteTheme {
        var expanded by remember { mutableStateOf(false) }
        Box(modifier = Modifier.background(ExtendedTheme.colors.background)) {
            SearchHistoryList(
                searchHistories = (0..20).map {
                    SearchHistoryItem(id = it.toLong(), keyword = if (it % 2 == 0) "记录$it" else "搜索记录$it")
                }.toImmutableList(),
                onSearchHistoryClick = {},
                expanded = expanded,
                onToggleExpand = { expanded = !expanded },
            )
        }
    }
}
