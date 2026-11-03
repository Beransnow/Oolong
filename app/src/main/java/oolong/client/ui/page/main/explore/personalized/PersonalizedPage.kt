package oolong.client.ui.page.main.explore.personalized

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import oolong.client.R
import oolong.client.arch.GlobalEvent
import oolong.client.arch.LoadStatus
import oolong.client.arch.onGlobalEvent
import oolong.client.model.DislikeOption
import oolong.client.model.ThreadSummary
import oolong.client.ui.common.theme.compose.Corners
import oolong.client.ui.common.theme.compose.ExtendedTheme
import oolong.client.ui.common.theme.compose.Motion
import oolong.client.ui.common.theme.compose.Symbols
import oolong.client.ui.page.main.explore.FeedThreadEvents
import oolong.client.ui.page.main.explore.rememberFeedThreadEvents
import oolong.client.ui.page.main.explore.rememberThreadPeeker
import oolong.client.ui.widgets.compose.BlockTip
import oolong.client.ui.widgets.compose.BlockableContent
import oolong.client.ui.widgets.compose.Container
import oolong.client.ui.widgets.compose.ErrorScreen
import oolong.client.ui.widgets.compose.FeedCard
import oolong.client.ui.widgets.compose.LoadMoreLayout
import oolong.client.ui.widgets.compose.MyLazyColumn
import oolong.client.ui.widgets.compose.VerticalDivider
import oolong.client.ui.widgets.compose.loadMoreFooter
import oolong.client.ui.widgets.compose.states.StateScreen

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
        onUndoDislike = viewModel::undoDislike,
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
    onDislike: (ThreadSummary, reasons: List<DislikeOption>, clickTime: Long) -> Unit,
    onUndoDislike: (threadId: Long) -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val list = state.list
    val refreshing = list.refresh == LoadStatus.Loading
    val error = (list.refresh as? LoadStatus.Error)?.error
    val pullRefreshState = rememberPullRefreshState(refreshing = refreshing, onRefresh = onRefresh)
    val lazyListState = rememberLazyListState()
    var showRefreshTip by remember { mutableStateOf(false) }
    // Saved, so coming back from another page neither shows the tip again nor scrolls to the top
    var handledRefresh by rememberSaveable { mutableIntStateOf(0) }

    val lastRefresh = state.lastRefresh
    LaunchedEffect(lastRefresh?.id) {
        if (lastRefresh == null || lastRefresh.id == handledRefresh) return@LaunchedEffect
        handledRefresh = lastRefresh.id
        showRefreshTip = true
        launch {
            delay(20)
            lazyListState.scrollToItem(0, 0)
        }
        delay(2000)
        showRefreshTip = false
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
                    undoable = state.undoable,
                    events = events,
                    onDislike = onDislike,
                    onUndoDislike = onUndoDislike,
                    onRefresh = onRefresh,
                    footer = { loadMoreFooter(list.append, onLoadMore) },
                )
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

            AnimatedVisibility(
                visible = showRefreshTip,
                enter = fadeIn(Motion.fadeIn()) + slideInVertically(Motion.settle()),
                exit = slideOutVertically(Motion.dismiss()) + fadeOut(Motion.fadeOut()),
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
            .clip(Corners.full)
            .background(
                color = ExtendedTheme.colors.emphasis,
                shape = Corners.full
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .align(Alignment.TopCenter)
    ) {
        Text(
            text = pluralStringResource(id = R.plurals.toast_feed_refresh, count = refreshCount, refreshCount),
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
    undoable: ImmutableMap<Long, String?>,
    events: FeedThreadEvents,
    onDislike: (ThreadSummary, reasons: List<DislikeOption>, clickTime: Long) -> Unit,
    onUndoDislike: (threadId: Long) -> Unit,
    onRefresh: () -> Unit,
    footer: LazyListScope.() -> Unit,
) {
    val peeker = rememberThreadPeeker()
    MyLazyColumn(
        state = state,
        contentPadding = contentPadding,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        itemsIndexed(
            items = items,
            key = { _, item -> "${item.feed.thread.id}" },
            contentType = { _, item ->
                val thread = item.feed.thread
                when {
                    thread.video != null -> "Video"
                    thread.images.size == 1 -> "SingleMedia"
                    thread.images.size > 1 -> "MultiMedia"
                    else -> "PlainText"
                }
            }
        ) { index, (feed, dislikeOptions) ->
            val (item, blocked) = feed
            val threadId = item.id
            val isHidden = threadId in disliked
            val isRefreshPosition = index + 1 == newCount
            val showDivider = !isHidden && !isRefreshPosition && index < items.size - 1
            Container {
                AnimatedVisibility(
                    visible = !isHidden,
                    enter = EnterTransition.None,
                    exit = shrinkVertically(Motion.dismiss()) + fadeOut(Motion.fadeOut())
                ) {
                    Column {
                        AnimatedContent(
                            targetState = threadId in undoable,
                            transitionSpec = { fadeIn(Motion.fadeIn()) togetherWith fadeOut(Motion.fadeOut()) using SizeTransform(clip = true) },
                            label = "dislike",
                        ) { undo ->
                            if (undo) {
                                UndoDislikeRow(text = undoable[threadId], onUndo = { onUndoDislike(threadId) })
                            } else {
                                BlockableContent(
                                    block = blocked,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp, horizontal = 16.dp)
                                ) {
                                    Column {
                                        FeedCard(
                                            thread = item,
                                            onClick = events.onOpen,
                                            onClickReply = events.onOpenReplies,
                                            onAgree = events.onAgree,
                                            onClickForum = events.onOpenForum,
                                            onClickUser = events.onOpenUser,
                                            onClickShared = events.onOpenOrigin,
                                            onLongPress = peeker?.let { peeker ->
                                                { thread, from, at, origin ->
                                                    peeker.peek(thread, from, at, origin, dislikeOptions) { clickTime, reasons ->
                                                        onDislike(thread, reasons, clickTime)
                                                    }
                                                }
                                            },
                                        ) {
                                            FeedMenu(
                                                thread = item,
                                                options = dislikeOptions,
                                                onDislike = { clickTime, reasons -> onDislike(item, reasons, clickTime) },
                                            )
                                        }
                                        if (showDivider) {
                                            VerticalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                        }
                                    }
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
        footer()
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
            imageVector = Symbols.Refresh,
            contentDescription = null
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = stringResource(id = R.string.tip_refresh),
            style = MaterialTheme.typography.subtitle1
        )
    }
}

/** In place of a thread just disliked: what happens next, and a way to take it back. */
@Composable
private fun UndoDislikeRow(text: String?, onUndo: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(Corners.md)
            .background(ExtendedTheme.colors.chip)
            .padding(start = 16.dp, end = 4.dp),
    ) {
        Text(
            text = text ?: stringResource(id = R.string.tip_disliked),
            color = ExtendedTheme.colors.textSecondary,
            style = MaterialTheme.typography.body2,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 14.dp),
        )
        Text(
            text = stringResource(id = R.string.button_undo),
            color = ExtendedTheme.colors.emphasis,
            style = MaterialTheme.typography.subtitle2,
            modifier = Modifier
                .clip(Corners.sm)
                .clickable(onClick = onUndo)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}
