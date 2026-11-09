package oolong.client.ui.page.main.explore

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.flow.first
import oolong.client.repository.FeedPosition

/**
 * Keeps a feed's place across restarts of the app: scrolls [state] to [restore] once the threads,
 * by id in the list's order as [ids] gives them, have it, then calls [onRestored]; whenever the
 * screen is left, hands [onLeave] where the list is. Each thread is one item of the list, keyed by
 * its id.
 */
@Composable
fun KeepFeedPosition(
    state: LazyListState,
    ids: () -> List<Long>,
    restore: FeedPosition?,
    onRestored: () -> Unit,
    onLeave: (FeedPosition?) -> Unit,
) {
    val currentIds by rememberUpdatedState(ids)
    val currentOnRestored by rememberUpdatedState(onRestored)
    LaunchedEffect(restore) {
        restore ?: return@LaunchedEffect
        val index = snapshotFlow { currentIds() }.first { it.isNotEmpty() }.indexOf(restore.threadId)
        if (index >= 0) state.scrollToItem(index, restore.offset)
        currentOnRestored()
    }
    val currentOnLeave by rememberUpdatedState(onLeave)
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { currentOnLeave(state.feedPosition()) }
}

/** The thread at the top of the list and how far past its top the list is, or null when none shows. */
private fun LazyListState.feedPosition(): FeedPosition? {
    val first = layoutInfo.visibleItemsInfo.firstOrNull { it.key.toString().toLongOrNull() != null } ?: return null
    val offset = if (first.index == firstVisibleItemIndex) firstVisibleItemScrollOffset else 0
    return FeedPosition(first.key.toString().toLong(), offset)
}
