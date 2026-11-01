package com.huanchengfly.tieba.post.ui.page.main.explore

import androidx.compose.runtime.Immutable
import com.huanchengfly.tieba.post.api.models.protos.ThreadInfo
import com.huanchengfly.tieba.post.api.models.protos.updateAgreeStatus
import com.huanchengfly.tieba.post.arch.ImmutableHolder
import com.huanchengfly.tieba.post.arch.PagerState
import com.huanchengfly.tieba.post.arch.wrapImmutable
import com.huanchengfly.tieba.post.repository.BlockRules
import com.huanchengfly.tieba.post.repository.blocks
import kotlinx.collections.immutable.toImmutableList

/** A thread in one of the explore feeds, and whether the block rules hide it. */
@Immutable
data class FeedThread(val thread: ImmutableHolder<ThreadInfo>, val blocked: Boolean = false)

internal fun BlockRules.feedThread(thread: ThreadInfo) = FeedThread(thread.wrapImmutable(), blocks(thread))

internal fun <T, R> PagerState<T>.mapItems(transform: (T) -> R) = PagerState(
    items = items.map(transform).toImmutableList(),
    refresh = refresh,
    append = append,
    prepend = prepend,
    hasMore = hasMore,
    hasPrevious = hasPrevious,
)

internal fun ThreadInfo.withAgreed(agreed: Boolean) = updateAgreeStatus(if (agreed) 1 else 0)

internal fun List<ThreadInfo>.withAgreed(threadId: Long, agreed: Boolean) =
    map { if (it.threadId == threadId) it.withAgreed(agreed) else it }
