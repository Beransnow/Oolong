package com.huanchengfly.tieba.post.ui.page.main.explore.personalized

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.api.models.protos.ThreadInfo
import com.huanchengfly.tieba.post.arch.GlobalEvent
import com.huanchengfly.tieba.post.arch.LoadStatus
import com.huanchengfly.tieba.post.arch.onGlobalEvent
import com.huanchengfly.tieba.post.model.DislikeOption
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.common.theme.compose.pullRefreshIndicator
import com.huanchengfly.tieba.post.ui.page.main.explore.FeedThreadEvents
import com.huanchengfly.tieba.post.ui.page.main.explore.rememberFeedThreadEvents
import com.huanchengfly.tieba.post.ui.widgets.compose.BlockTip
import com.huanchengfly.tieba.post.ui.widgets.compose.BlockableContent
import com.huanchengfly.tieba.post.ui.widgets.compose.Container
import com.huanchengfly.tieba.post.ui.widgets.compose.ErrorScreen
import com.huanchengfly.tieba.post.ui.widgets.compose.FeedCard
import com.huanchengfly.tieba.post.ui.widgets.compose.LoadMoreLayout
import com.huanchengfly.tieba.post.ui.widgets.compose.MyLazyColumn
import com.huanchengfly.tieba.post.ui.widgets.compose.VerticalDivider
import com.huanchengfly.tieba.post.ui.widgets.compose.states.StateScreen
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PersonalizedPage(
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: PersonalizedViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.open() }
    onGlobalEvent<GlobalEvent.Refresh>(filter = { it.key == "personalized" }) { viewModel.refresh() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    PersonalizedScreen(
        state = state,
        events = rememberFeedThreadEvents(onAgree = viewModel::agree),
        onRefresh = viewModel::refresh,
        onLoadMore = viewModel::loadMore,
        onDislike = viewModel::dislike,
        contentPadding = contentPadding,
    )
}

/**
 * Recommended threads, which pull down to refresh and load more at their end. After a refresh a
 * bubble tells how many threads came, and a row marks where the threads already read begin.
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun PersonalizedScreen(
    state: PersonalizedState,
    events: FeedThreadEvents,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onDislike: (ThreadInfo, reasons: List<DislikeOption>, clickTime: Long) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val list = state.list
    val refreshing = list.refresh == LoadStatus.Loading
    val error = (list.refresh as? LoadStatus.Error)?.error
    val pullRefreshState = rememberPullRefreshState(refreshing = refreshing, onRefresh = onRefresh)
    val lazyListState = rememberLazyListState()
    var showRefreshTip by remember { mutableStateOf(false) }

    val lastRefresh = state.lastRefresh
    if (lastRefresh != null) {
        LaunchedEffect(lastRefresh.id) {
            showRefreshTip = true
            launch {
                delay(20)
                lazyListState.scrollToItem(0, 0)
            }
            delay(2000)
            showRefreshTip = false
        }
    }
    StateScreen(
        modifier = Modifier.fillMaxSize(),
        isEmpty = list.items.isEmpty(),
        isError = error != null,
        isLoading = refreshing,
        onReload = onRefresh,
        errorScreen = { ErrorScreen(error = error) },
    ) {
        Box(modifier = Modifier.pullRefresh(pullRefreshState)) {
            LoadMoreLayout(
                isLoading = list.append == LoadStatus.Loading,
                onLoadMore = onLoadMore,
                loadEnd = !list.hasMore,
                lazyListState = lazyListState,
                isEmpty = list.items.isEmpty()
            ) {
                FeedList(
                    state = lazyListState,
                    contentPadding = contentPadding,
                    items = list.items,
                    newCount = state.newCount,
                    disliked = state.disliked,
                    events = events,
                    onDislike = onDislike,
                    onRefresh = onRefresh,
                )
            }

            PullRefreshIndicator(
                refreshing = refreshing,
                state = pullRefreshState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = contentPadding.calculateTopPadding()),
                backgroundColor = ExtendedTheme.colors.pullRefreshIndicator,
                contentColor = ExtendedTheme.colors.primary,
            )

            AnimatedVisibility(
                visible = showRefreshTip,
                enter = fadeIn() + slideInVertically(),
                exit = slideOutVertically() + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = contentPadding.calculateTopPadding())
            ) {
                RefreshTip(refreshCount = lastRefresh?.count ?: 0)
            }
        }
    }
}

@Composable
private fun BoxScope.RefreshTip(refreshCount: Int) {
    Box(
        modifier = Modifier
            .padding(top = 72.dp)
            .clip(RoundedCornerShape(100))
            .background(
                color = ExtendedTheme.colors.primary,
                shape = RoundedCornerShape(100)
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .align(Alignment.TopCenter)
    ) {
        Text(
            text = stringResource(id = R.string.toast_feed_refresh, refreshCount),
            color = ExtendedTheme.colors.onAccent
        )
    }
}

@Composable
private fun FeedList(
    state: LazyListState,
    contentPadding: PaddingValues,
    items: ImmutableList<RecommendedItem>,
    newCount: Int,
    disliked: ImmutableSet<Long>,
    events: FeedThreadEvents,
    onDislike: (ThreadInfo, reasons: List<DislikeOption>, clickTime: Long) -> Unit,
    onRefresh: () -> Unit,
) {
    MyLazyColumn(
        state = state,
        contentPadding = contentPadding,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        itemsIndexed(
            items = items,
            key = { _, item -> "${item.feed.thread.get { id }}" },
            contentType = { _, item ->
                val thread = item.feed.thread
                when {
                    thread.get { videoInfo } != null -> "Video"
                    thread.get { media }.size == 1 -> "SingleMedia"
                    thread.get { media }.size > 1 -> "MultiMedia"
                    else -> "PlainText"
                }
            }
        ) { index, (feed, dislikeOptions) ->
            val (item, blocked) = feed
            val isHidden = item.get { threadId } in disliked
            val isRefreshPosition = index + 1 == newCount
            val showDivider = !isHidden && !isRefreshPosition && index < items.size - 1
            Container {
                AnimatedVisibility(
                    visible = !isHidden,
                    enter = EnterTransition.None,
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        BlockableContent(
                            blocked = blocked,
                            blockedTip = { BlockTip(text = { Text(text = stringResource(id = R.string.tip_blocked_thread)) }) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 16.dp)
                        ) {
                            Column {
                                FeedCard(
                                    item = item,
                                    onClick = events.onOpen,
                                    onClickReply = events.onOpenReplies,
                                    onAgree = events.onAgree,
                                    onClickForum = events.onOpenForum,
                                    onClickUser = events.onOpenUser,
                                    onClickOriginThread = events.onOpenOrigin,
                                ) {
                                    if (dislikeOptions.isNotEmpty()) {
                                        Dislike(
                                            options = dislikeOptions,
                                            onDislike = { clickTime, reasons -> onDislike(item.get(), reasons, clickTime) }
                                        )
                                    }
                                }
                                if (showDivider) {
                                    VerticalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        thickness = 2.dp
                                    )
                                }
                            }
                        }
                        if (isRefreshPosition) {
                            RefreshTip(onRefresh)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RefreshTip(
    onRefresh: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onRefresh)
            .padding(8.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Refresh,
            contentDescription = null
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = stringResource(id = R.string.tip_refresh),
            style = MaterialTheme.typography.subtitle1
        )
    }
}