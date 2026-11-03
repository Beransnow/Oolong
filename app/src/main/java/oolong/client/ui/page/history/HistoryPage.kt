package oolong.client.ui.page.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.Icon
import androidx.compose.material.Tab
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import oolong.client.ui.widgets.compose.GlassTopBar
import oolong.client.ui.widgets.compose.GlassTopBarTitle
import oolong.client.ui.widgets.compose.IconButton
import oolong.client.ui.widgets.compose.PagerTabIndicator
import oolong.client.ui.widgets.compose.TabRow

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

/** The visited threads and forums, in two tabs. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    state: HistoryState,
    onBack: () -> Unit,
    onClear: () -> Unit,
    onLoadMore: (HistoryTab) -> Unit,
    onDelete: (HistoryEntry) -> Unit,
    onOpen: (HistoryEntry) -> Unit,
) {
    val pagerState = rememberPagerState { 2 }
    val coroutineScope = rememberCoroutineScope()

    GlassScaffold(
        backgroundColor = Color.Transparent,
        topBar = {
            GlassTopBar(
                title = { GlassTopBarTitle(text = stringResource(id = R.string.title_history)) },
                navigationIcon = {
                    BackNavigationIcon(onBackPressed = onBack)
                },
                actions = {
                    IconButton(onClick = onClear) {
                        Icon(
                            imageVector = Symbols.Delete,
                            contentDescription = stringResource(id = R.string.title_history_delete),
                            tint = ExtendedTheme.colors.topBarContent
                        )
                    }
                },
                content = {
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
                        contentColor = ExtendedTheme.colors.emphasis,
                        modifier = Modifier
                            .width(100.dp * 2)
                            .align(Alignment.CenterHorizontally)
                    ) {
                        Tab(
                            text = {
                                Text(
                                    text = stringResource(id = R.string.title_history_thread),
                                    fontSize = 13.sp
                                )
                            },
                            selected = pagerState.currentPage == 0,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(0)
                                }
                            },
                            selectedContentColor = ExtendedTheme.colors.topBarContent,
                            unselectedContentColor = ExtendedTheme.colors.topBarContentSecondary
                        )
                        Tab(
                            text = {
                                Text(
                                    text = stringResource(id = R.string.title_history_forum),
                                    fontSize = 13.sp
                                )
                            },
                            selected = pagerState.currentPage == 1,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(1)
                                }
                            },
                            selectedContentColor = ExtendedTheme.colors.topBarContent,
                            unselectedContentColor = ExtendedTheme.colors.topBarContentSecondary
                        )
                    }
                }
            )
        }
    ) { contentPaddings ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { it },
            verticalAlignment = Alignment.Top,
            userScrollEnabled = true,
        ) {
            if (it == 0) {
                HistoryListContent(
                    list = state.threads,
                    onLoadMore = { onLoadMore(HistoryTab.Threads) },
                    onOpen = onOpen,
                    onDelete = onDelete,
                    contentPadding = contentPaddings,
                )
            } else {
                HistoryListContent(
                    list = state.forums,
                    onLoadMore = { onLoadMore(HistoryTab.Forums) },
                    onOpen = onOpen,
                    onDelete = onDelete,
                    contentPadding = contentPaddings,
                )
            }
        }
    }
}
