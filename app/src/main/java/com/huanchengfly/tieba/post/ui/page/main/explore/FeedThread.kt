package com.huanchengfly.tieba.post.ui.page.main.explore

import androidx.compose.runtime.Immutable
import com.huanchengfly.tieba.post.arch.PagerState
import com.huanchengfly.tieba.post.model.ThreadSummary
import com.huanchengfly.tieba.post.repository.BlockPlace
import com.huanchengfly.tieba.post.repository.BlockRules
import com.huanchengfly.tieba.post.repository.BlockVerdict
import com.huanchengfly.tieba.post.repository.check
import kotlinx.collections.immutable.toImmutableList

/** A thread in one of the explore feeds, and how the block rules block it, if they do. */
@Immutable
data class FeedThread(val thread: ThreadSummary, val block: BlockVerdict? = null) {
    val blocked: Boolean get() = block != null
}

internal fun BlockRules.feedThread(thread: ThreadSummary) = FeedThread(thread, check(thread, BlockPlace.Feeds))

internal fun <T, R> PagerState<T>.mapItems(transform: (T) -> R) = PagerState(
    items = items.map(transform).toImmutableList(),
    refresh = refresh,
    append = append,
    prepend = prepend,
    hasMore = hasMore,
    hasPrevious = hasPrevious,
)
