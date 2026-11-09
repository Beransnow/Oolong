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
import oolong.client.repository.FeedPosition
import oolong.client.ui.common.theme.compose.ExtendedTheme
import oolong.client.ui.page.main.explore.FeedThread
import oolong.client.ui.page.main.explore.FeedThreadEvents
import oolong.client.ui.page.main.explore.KeepFeedPosition
import oolong.client.ui.page.main.explore.rememberFeedThreadEvents
import oolong.client.ui.widgets.compose.BlockTip
import oolong.client.ui.widgets.compose.BlockableContent
import oolong.client.ui.widgets.compose.Container
import oolong.client.ui.widgets.compose.EndPull
import oolong.client.ui.widgets.compose.ErrorScreen
import oolong.client.ui.widgets.compose.FeedCard
import oolong.client.ui.widgets.compose.LoadMoreEffect
import oolong.client.ui.widgets.compose.MyLazyColumn
import oolong.client.ui.widgets.compose.PullRefresh
import oolong.client.ui.widgets.compose.VerticalDivider
import oolong.client.ui.widgets.compose.loadMoreFooter
import oolong.client.ui.widgets.compose.states.StateScreen

@Composable
fun ConcernPage(
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: ConcernViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.open() }
    onGlobalEvent<GlobalEvent.Refresh>(filter = { it.key == "concern" }) { viewModel.refresh() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val keptPosition by viewModel.keptPosition.collectAsStateWithLifecycle()
    ConcernScreen(
        list = state,
        keptPosition = keptPosition,
        onPositionRestored = viewModel::positionRestored,
        onLeave = viewModel::leave,
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
    keptPosition: FeedPosition? = null,
    onPositionRestored: () -> Unit = {},
    onLeave: (FeedPosition?) -> Unit = {},
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
        val lazyListState = rememberLazyListState()
        KeepFeedPosition(
            state = lazyListState,
            ids = { list.items.map { it.thread.id } },
            restore = keptPosition,
            onRestored = onPositionRestored,
            onLeave = onLeave,
        )
        LoadMoreEffect(lazyListState, enabled = list.hasMore && list.append == LoadStatus.Idle, onLoadMore = onLoadMore)
        PullRefresh(
            refreshing = list.refresh == LoadStatus.Loading,
            revealUnpulled = true,
            onRefresh = onRefresh,
            indicatorTop = contentPadding.calculateTopPadding(),
            endPull = if (list.hasMore) EndPull.None else EndPull.Tease,
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
                loadMoreFooter(list.append, list.hasMore, onLoadMore)
            }
        }
    }
}
