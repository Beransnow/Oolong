package com.huanchengfly.tieba.post.ui.page.main.explore

import androidx.compose.runtime.Immutable
import com.huanchengfly.tieba.post.arch.PagerState
import com.huanchengfly.tieba.post.model.ThreadSummary
import com.huanchengfly.tieba.post.repository.BlockRules
import com.huanchengfly.tieba.post.repository.blocks
import kotlinx.collections.immutable.toImmutableList

/** A thread in one of the explore feeds, and whether the block rules hide it. */
@Immutable
data class FeedThread(val thread: ThreadSummary, val blocked: Boolean = false)

internal fun BlockRules.feedThread(thread: ThreadSummary) = FeedThread(thread, blocks(thread))

internal fun <T, R> PagerState<T>.mapItems(transform: (T) -> R) = PagerState(
    items = items.map(transform).toImmutableList(),
    refresh = refresh,
    append = append,
    prepend = prepend,
    hasMore = hasMore,
    hasPrevious = hasPrevious,
)

/** [thread] liked or not, its like count following. */
internal fun ThreadSummary.withAgreed(agreed: Boolean) = if (agree.agreed == agreed) this else copy(agree = agree.toggled())

internal fun List<ThreadSummary>.withAgreed(threadId: Long, agreed: Boolean) =
    map { if (it.id == threadId) it.withAgreed(agreed) else it }
