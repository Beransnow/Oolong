package oolong.client.ui.widgets.compose

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FractionalThreshold
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import oolong.client.R
import oolong.client.api.retrofit.exception.NoConnectivityException
import oolong.client.arch.LoadStatus
import oolong.client.ui.common.theme.compose.Corners
import oolong.client.ui.common.theme.compose.ExtendedTheme
import oolong.client.ui.common.theme.compose.Motion
import kotlin.math.abs
import kotlin.math.roundToInt

private val LoadDistance = 70.dp

private const val LoadMoreFooterKey = "LoadMoreFooter"

/**
 * The end of a list while its next page loads, or why it could not load with a way to retry.
 * Goes after the list's items; the one key keeps the row in place from a failure to its retry.
 */
fun LazyListScope.loadMoreFooter(append: LoadStatus, onRetry: () -> Unit) {
    if (append == LoadStatus.Idle) return
    item(key = LoadMoreFooterKey, contentType = LoadMoreFooterKey) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (append is LoadStatus.Error) {
                val message = if (append.error is NoConnectivityException) R.string.title_no_internet_connectivity else R.string.title_api_error
                Text(
                    text = stringResource(id = message),
                    color = ExtendedTheme.colors.textSecondary,
                    style = MaterialTheme.typography.body2,
                )
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onRetry) { Text(text = stringResource(id = R.string.button_retry)) }
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = ExtendedTheme.colors.emphasis,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class, FlowPreview::class)
@Composable
fun LoadMoreLayout(
    isLoading: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    enableLoadMore: Boolean = true,
    loadEnd: Boolean = false,
    indicator: @Composable (Boolean, Boolean, Boolean) -> Unit = { loading, end, willLoad ->
        DefaultIndicator(
            isLoading = loading,
            loadEnd = end,
            willLoad = willLoad
        )
    },
    lazyListState: LazyListState? = null,
    isEmpty: Boolean = lazyListState?.layoutInfo?.totalItemsCount == 0,
    preloadCount: Int = 1,
    content: @Composable () -> Unit,
) {
    val loadDistance = with(LocalDensity.current) { LoadDistance.toPx() }

    val curOnLoadMore by rememberUpdatedState(newValue = onLoadMore)
    var lastTriggerTime by remember { mutableLongStateOf(0L) }
    var waitingStateReset by remember { mutableStateOf(false) }
    val loadMoreFlow = remember {
        MutableSharedFlow<Long>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_LATEST
        )
    }
    val coroutineScope = rememberCoroutineScope()
    DisposableEffect(Unit) {
        val job = coroutineScope.launch {
            loadMoreFlow
                .sample(500)
                .collect {
                    curOnLoadMore()
                    waitingStateReset = true
                    lastTriggerTime = it
                }
        }

        onDispose { job.cancel() }
    }

    val canLoadMore = remember(enableLoadMore, loadEnd) { enableLoadMore && !loadEnd }
    val curIsEmpty by rememberUpdatedState(newValue = isEmpty)
    val curIsLoading by rememberUpdatedState(newValue = isLoading)
    val curCanLoadMore by rememberUpdatedState(newValue = canLoadMore)
    val curPreloadCount by rememberUpdatedState(newValue = preloadCount)

    // 处理列表滚动到底部时自动加载更多
    // Only once per list length: a load that failed or brought nothing new leaves the list as it
    // was, and loading again right away would repeat the same request twice a second.
    var autoLoadedAtCount by remember { mutableIntStateOf(-1) }
    val curLazyListState by rememberUpdatedState(newValue = lazyListState)
    LaunchedEffect(curLazyListState) {
        curLazyListState?.let { state ->
            snapshotFlow {
                val shouldPreload =
                    !curIsEmpty && curCanLoadMore && !curIsLoading && curPreloadCount > 0
                val totalItemsCount = state.loadedItemsCount
                val isInPreloadRange =
                    state.firstVisibleItemIndex + state.layoutInfo.visibleItemsInfo.size - 1 >= totalItemsCount - curPreloadCount
                shouldPreload && isInPreloadRange && totalItemsCount != autoLoadedAtCount
            }
                .distinctUntilChanged()
                .collect {
                    if (it) {
                        autoLoadedAtCount = state.loadedItemsCount
                        val curTime = System.currentTimeMillis()
                        coroutineScope.launch {
                            loadMoreFlow.emit(curTime)
                        }
                        curTime - lastTriggerTime >= 500
                    }
                }
        }
    }

    val swipeableState = rememberSwipeableState(false) { newValue ->
        if (newValue && !curIsLoading && curCanLoadMore) {
            val curTime = System.currentTimeMillis()
            coroutineScope.launch {
                loadMoreFlow.emit(curTime)
            }
            curTime - lastTriggerTime >= 500
        } else !newValue
    }

    val isStateReset by remember { derivedStateOf { abs(swipeableState.offset.value - loadDistance) < 1f } }
    LaunchedEffect(waitingStateReset, isStateReset) {
        if (waitingStateReset && isStateReset) {
            waitingStateReset = false
        }
    }

    LaunchedEffect(isLoading) { if (!isLoading) swipeableState.animateTo(isLoading) }

    Box(
        modifier = Modifier
            .nestedScroll(swipeableState.LoadPreDownPostUpNestedScrollConnection)
            .swipeable(
                state = swipeableState,
                anchors = mapOf(
                    loadDistance to false,
                    -loadDistance to true,
                ),
                thresholds = { _, _ -> FractionalThreshold(0.5f) },
                orientation = Orientation.Vertical,
                enabled = enableLoadMore && !waitingStateReset,
            )
            .fillMaxSize()
            .then(modifier)
    ) {
        content()

        Box(modifier = Modifier
            .align(Alignment.BottomCenter)
            .offset { IntOffset(0, swipeableState.offset.value.roundToInt()) }
        ) {
            if (enableLoadMore && swipeableState.offset.value != loadDistance) {
                indicator(isLoading, loadEnd, swipeableState.targetValue)
            }
        }
    }
}

/** Items in the list, without the [loadMoreFooter] that comes and goes with each load. */
private val LazyListState.loadedItemsCount: Int
    get() = layoutInfo.totalItemsCount - if (layoutInfo.visibleItemsInfo.lastOrNull()?.key == LoadMoreFooterKey) 1 else 0

@Composable
fun DefaultIndicator(
    isLoading: Boolean,
    loadEnd: Boolean,
    willLoad: Boolean,
    loadingText: String = stringResource(id = R.string.text_loading),
    loadEndText: String = stringResource(id = R.string.no_more),
    pullToLoadText: String = stringResource(id = R.string.pull_to_load),
    releaseToLoadText: String = stringResource(id = R.string.release_to_load),
) {
    Surface(
        elevation = 8.dp,
        shape = Corners.full,
        color = ExtendedTheme.colors.refreshIndicator,
        contentColor = ExtendedTheme.colors.text
    ) {
        Row(
            modifier = Modifier
                .height(IntrinsicSize.Min)
                .padding(10.dp)
                .animateContentSize(Motion.settle()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 3.dp,
                    color = ExtendedTheme.colors.emphasis
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = loadingText, modifier = Modifier.padding(horizontal = 8.dp))
            } else if (loadEnd) {
                Text(text = loadEndText, modifier = Modifier.padding(horizontal = 8.dp))
            } else {
                Text(
                    text = if (willLoad) releaseToLoadText else pullToLoadText,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }
}

private val <T> SwipeableState<T>.LoadPreDownPostUpNestedScrollConnection: NestedScrollConnection
    get() = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val delta = available.toFloat()
            return if (delta > 0 && source == NestedScrollSource.Drag) {
                performDrag(delta).toOffset()
            } else {
                Offset.Zero
            }
        }

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            return if (source == NestedScrollSource.Drag) {
                performDrag(available.toFloat()).toOffset()
            } else {
                Offset.Zero
            }
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            val toFling = Offset(available.x, available.y).toFloat()
            return if (toFling > 0 && offset.value < maxBound) {
                performFling(velocity = toFling)
                // since we go to the anchor with tween settling, consume all for the best UX
                available
            } else {
                Velocity.Zero
            }
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            performFling(velocity = Offset(available.x, available.y).toFloat())
            return available
        }

        private fun Float.toOffset(): Offset = Offset(0f, this)

        private fun Offset.toFloat(): Float = this.y
    }
