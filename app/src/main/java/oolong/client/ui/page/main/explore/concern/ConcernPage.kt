package oolong.client.ui.page.main.explore.concern

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Text
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import oolong.client.R
import oolong.client.arch.GlobalEvent
import oolong.client.arch.LoadStatus
import oolong.client.arch.PagerState
import oolong.client.arch.onGlobalEvent
import oolong.client.ui.common.theme.compose.ExtendedTheme
import oolong.client.ui.page.main.explore.FeedThread
import oolong.client.ui.page.main.explore.FeedThreadEvents
import oolong.client.ui.page.main.explore.rememberFeedThreadEvents
import oolong.client.ui.widgets.compose.BlockTip
import oolong.client.ui.widgets.compose.BlockableContent
import oolong.client.ui.widgets.compose.Container
import oolong.client.ui.widgets.compose.ErrorScreen
import oolong.client.ui.widgets.compose.FeedCard
import oolong.client.ui.widgets.compose.LoadMoreLayout
import oolong.client.ui.widgets.compose.MyLazyColumn
import oolong.client.ui.widgets.compose.VerticalDivider
import oolong.client.ui.widgets.compose.states.StateScreen

@Composable
fun ConcernPage(
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: ConcernViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.open() }
    onGlobalEvent<GlobalEvent.Refresh>(filter = { it.key == "concern" }) { viewModel.refresh() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    ConcernScreen(
        list = state,
        events = rememberFeedThreadEvents(onAgree = viewModel::agree),
        onRefresh = viewModel::refresh,
        onLoadMore = viewModel::loadMore,
        contentPadding = contentPadding,
    )
}

/** The threads of the users the user follows, which pull down to refresh and load more at their end. */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ConcernScreen(
    list: PagerState<FeedThread>,
    events: FeedThreadEvents,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val error = (list.refresh as? LoadStatus.Error)?.error
    StateScreen(
        modifier = Modifier.fillMaxSize(),
        isEmpty = list.items.isEmpty(),
        isError = error != null,
        isLoading = list.refresh == LoadStatus.Loading,
        onReload = onRefresh,
        errorScreen = { ErrorScreen(error = error) },
    ) {
        val refreshing = list.refresh == LoadStatus.Loading
        val pullRefreshState = rememberPullRefreshState(refreshing = refreshing, onRefresh = onRefresh)
        val lazyListState = rememberLazyListState()
        Box(modifier = Modifier.pullRefresh(pullRefreshState)) {
            LoadMoreLayout(
                isLoading = list.append == LoadStatus.Loading,
                onLoadMore = onLoadMore,
                loadEnd = !list.hasMore,
                lazyListState = lazyListState,
            ) {
                MyLazyColumn(
                    state = lazyListState,
                    contentPadding = contentPadding,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    itemsIndexed(items = list.items, key = { _, item -> item.thread.id }) { index, item ->
                        Container {
                            BlockableContent(
                                block = item.block,
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
                            ) {
                                Column {
                                    FeedCard(
                                        thread = item.thread,
                                        onClick = events.onOpen,
                                        onClickReply = events.onOpenReplies,
                                        onAgree = events.onAgree,
                                        onClickForum = events.onOpenForum,
                                        onClickUser = events.onOpenUser,
                                        onClickShared = events.onOpenOrigin,
                                        onLongPress = events.onPeek,
                                    )
                                    if (index < list.items.size - 1) {
                                        VerticalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    }
                                }
                            }
                        }
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
