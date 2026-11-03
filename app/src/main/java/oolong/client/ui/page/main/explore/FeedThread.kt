package oolong.client.ui.page.main.explore

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.toImmutableList
import oolong.client.arch.PagerState
import oolong.client.model.ThreadSummary
import oolong.client.repository.BlockPlace
import oolong.client.repository.BlockRules
import oolong.client.repository.BlockVerdict
import oolong.client.repository.check

/** A thread in one of the explore feeds, and how the block rules block it, if they do. */
@Immutable
data class FeedThread(val thread: ThreadSummary, val block: BlockVerdict? = null) {
    val blocked: Boolean get() = block != null
}

internal fun BlockRules.feedThread(thread: ThreadSummary) = FeedThread(thread, check(thread, BlockPlace.Feeds))

internal fun <T, R> PagerState<T>.mapItems(transform: (T) -> R) = withItems(items.map(transform))

/** The same pages holding [items] in place of their own. */
internal fun <T, R> PagerState<T>.withItems(items: List<R>) = PagerState(
    items = items.toImmutableList(),
    refresh = refresh,
    append = append,
    prepend = prepend,
    hasMore = hasMore,
    hasPrevious = hasPrevious,
)
