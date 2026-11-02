package com.huanchengfly.tieba.post.ui.page.main.notifications.list

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huanchengfly.tieba.post.arch.Pager
import com.huanchengfly.tieba.post.arch.PagerState
import com.huanchengfly.tieba.post.arch.pager
import com.huanchengfly.tieba.post.model.Notification
import com.huanchengfly.tieba.post.model.NotificationType
import com.huanchengfly.tieba.post.repository.BlockPlace
import com.huanchengfly.tieba.post.repository.BlockRuleSource
import com.huanchengfly.tieba.post.repository.BlockVerdict
import com.huanchengfly.tieba.post.repository.Blockable
import com.huanchengfly.tieba.post.repository.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** A notification, and how the block rules block it, if they do. */
@Immutable
data class NotificationItem(val notification: Notification, val block: BlockVerdict? = null) {
    val blocked: Boolean get() = block != null
}

@Immutable
data class NotificationsState(
    val lists: ImmutableMap<NotificationType, PagerState<NotificationItem>> = persistentMapOf(),
) {
    fun list(type: NotificationType): PagerState<NotificationItem> = lists[type] ?: PagerState()
}

/** The replies to the user and their mentions, each list loaded the first time it is shown. */
@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val repository: NotificationRepository,
    blockRuleSource: BlockRuleSource,
) : ViewModel() {
    private val pagers = MutableStateFlow<Map<NotificationType, Pager<Int, Notification>>>(emptyMap())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val listStates = pagers.flatMapLatest { pagers ->
        if (pagers.isEmpty()) flowOf(emptyMap())
        else combine(pagers.map { (type, pager) -> pager.state.map { type to it } }) { it.toMap() }
    }

    val state: StateFlow<NotificationsState> = combine(listStates, blockRuleSource.rules) { lists, rules ->
        NotificationsState(
            lists.mapValues { (_, list) ->
                PagerState(
                    items = list.items.map { item ->
                        NotificationItem(item, item.author?.let { rules.check(Blockable(it, item.content, BlockPlace.Messages)) })
                    }.toImmutableList(),
                    refresh = list.refresh,
                    append = list.append,
                    hasMore = list.hasMore,
                )
            }.toImmutableMap()
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, NotificationsState())

    /** Starts loading [type]'s list unless it already has. */
    fun open(type: NotificationType) {
        pagerOf(type)
    }

    fun refresh(type: NotificationType) = pagerOf(type).refresh()

    fun loadMore(type: NotificationType) = pagerOf(type).loadMore()

    private fun pagerOf(type: NotificationType): Pager<Int, Notification> {
        pagers.value[type]?.let { return it }
        val pager = pager(
            initialKey = 1,
            itemKey = { item: Notification -> "${item.postId}_${item.author?.id}_${item.time}" },
        ) { page -> repository.page(type, page) }
        pagers.update { it + (type to pager) }
        return pager
    }
}
