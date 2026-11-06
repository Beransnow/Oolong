package oolong.client.ui.page.history

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import oolong.client.R
import oolong.client.model.HistoryEntry
import oolong.client.ui.common.theme.compose.ExtendedTheme
import oolong.client.ui.common.theme.compose.Symbols
import oolong.client.ui.navigation.ForumKey
import oolong.client.ui.navigation.Navigator
import oolong.client.ui.navigation.ThreadKey
import oolong.client.ui.page.LocalNavigator
import oolong.client.ui.page.history.list.HistoryListContent
import oolong.client.ui.page.thread.ThreadPageFrom
import oolong.client.ui.widgets.compose.BackNavigationIcon
import oolong.client.ui.widgets.compose.GlassScaffold
import oolong.client.ui.widgets.compose.GlassTab
import oolong.client.ui.widgets.compose.GlassTabRow
import oolong.client.ui.widgets.compose.GlassTopBar
import oolong.client.ui.widgets.compose.IconButton

@Composable
fun HistoryPage(
    navigator: Navigator = LocalNavigator.current,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HistoryScreen(
        state = state,
        onBack = { navigator.navigateUp() },
        onClear = viewModel::clear,
        onLoadMore = viewModel::loadMore,
        onDelete = viewModel::delete,
        onSearch = viewModel::search,
        onOpen = { entry ->
            when (entry) {
                is HistoryEntry.Forum -> navigator.navigate(ForumKey(entry.name))
                is HistoryEntry.Thread -> navigator.navigate(
                    ThreadKey(
                        entry.threadId,
                        postId = entry.postId,
                        seeLz = entry.seeLz,
                        from = ThreadPageFrom.FROM_HISTORY
                    )
                )
            }
        },
    )
}

/** The visited threads and forums, in two tabs that are the bar's title. */
@Composable
fun HistoryScreen(
    state: HistoryState,
    onBack: () -> Unit,
    onClear: () -> Unit,
    onLoadMore: (HistoryTab) -> Unit,
    onDelete: (HistoryEntry) -> Unit,
    onOpen: (HistoryEntry) -> Unit,
    onSearch: (String) -> Unit = {},
) {
    val pagerState = rememberPagerState { HistoryTab.entries.size }
    val coroutineScope = rememberCoroutineScope()

    GlassScaffold(
        backgroundColor = Color.Transparent,
        topBar = {
            GlassTopBar(
                title = {
                    GlassTabRow(
                        selectedTabIndex = pagerState.currentPage,
                        pagerState = pagerState,
                        onSelect = { coroutineScope.launch { pagerState.animateScrollToPage(it) } },
                    ) {
                        HistoryTab.entries.forEachIndexed { index, tab ->
                            GlassTab(
                                text = {
                                    Text(
                                        text = stringResource(
                                            id = when (tab) {
                                                HistoryTab.Threads -> R.string.title_history_tab_threads
                                                HistoryTab.Forums -> R.string.title_history_tab_forums
                                            }
                                        )
                                    )
                                },
                                selected = pagerState.currentPage == index,
                                selectedContentColor = ExtendedTheme.colors.text,
                                unselectedContentColor = ExtendedTheme.colors.textSecondary,
                                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                            )
                        }
                    }
                },
                centerTitle = true,
                navigationIcon = { BackNavigationIcon(onBackPressed = onBack) },
                actions = {
                    IconButton(onClick = onClear) {
                        Icon(
                            imageVector = Symbols.Delete,
                            contentDescription = stringResource(id = R.string.title_history_delete),
                            tint = ExtendedTheme.colors.topBarContent
                        )
                    }
                },
            )
        }
    ) { contentPaddings ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { it },
            verticalAlignment = Alignment.Top,
        ) { page ->
            when (HistoryTab.entries[page]) {
                HistoryTab.Threads -> HistoryListContent(
                    list = state.threads,
                    query = state.query,
                    searchHint = R.string.history_search_threads,
                    onQueryChange = onSearch,
                    onLoadMore = { onLoadMore(HistoryTab.Threads) },
                    onOpen = onOpen,
                    onDelete = onDelete,
                    contentPadding = contentPaddings,
                )

                HistoryTab.Forums -> HistoryListContent(
                    list = state.forums,
                    query = state.query,
                    searchHint = R.string.history_search_forums,
                    onQueryChange = onSearch,
                    onLoadMore = { onLoadMore(HistoryTab.Forums) },
                    onOpen = onOpen,
                    onDelete = onDelete,
                    contentPadding = contentPaddings,
                )
            }
        }
    }
}
