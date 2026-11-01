package com.huanchengfly.tieba.post.ui.page.history

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.arch.Actions
import com.huanchengfly.tieba.post.arch.MessageController
import com.huanchengfly.tieba.post.model.HistoryEntry
import com.huanchengfly.tieba.post.repository.HistoryRepository
import com.huanchengfly.tieba.post.utils.DateTimeUtils
import com.huanchengfly.tieba.post.utils.HistoryUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** The kinds of history, each in its own tab. */
enum class HistoryTab { Threads, Forums }

/** One tab's history, split into today's visits and the earlier ones. */
@Immutable
data class HistoryList<T : HistoryEntry>(
    val today: ImmutableList<T> = persistentListOf(),
    val before: ImmutableList<T> = persistentListOf(),
    /** Whether older visits remain to be shown. */
    val hasMore: Boolean = false,
) {
    val isEmpty: Boolean get() = today.isEmpty() && before.isEmpty()
}

@Immutable
data class HistoryState(
    val threads: HistoryList<HistoryEntry.Thread> = HistoryList(),
    val forums: HistoryList<HistoryEntry.Forum> = HistoryList(),
)

/** The browsing history, which follows the database and shows more as the user scrolls. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: HistoryRepository,
    private val messages: MessageController,
) : ViewModel() {
    private val actions = Actions(viewModelScope)
    private val limits = MutableStateFlow(HistoryTab.entries.associateWith { PAGE_SIZE })

    val state: StateFlow<HistoryState> = combine(
        list(HistoryTab.Threads, repository::threads),
        list(HistoryTab.Forums, repository::forums),
    ) { threads, forums -> HistoryState(threads, forums) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, HistoryState())

    /** Shows another page of older visits in [tab]. */
    fun loadMore(tab: HistoryTab) = limits.update { it + (tab to it.getValue(tab) + PAGE_SIZE) }

    fun delete(entry: HistoryEntry) = actions.launch("delete-${entry.id}") {
        runCatching { repository.delete(entry) }
            .onSuccess { messages.show(R.string.delete_history_success) }
            .onFailure { messages.showError(R.string.delete_history_failure, it) }
    }

    fun clear() = actions.launch("clear") {
        runCatching { repository.clear() }.onSuccess { messages.show(R.string.toast_clear_success) }
    }

    private fun <T : HistoryEntry> list(tab: HistoryTab, entries: (limit: Int) -> Flow<List<T>>): Flow<HistoryList<T>> =
        limits.map { it.getValue(tab) }.distinctUntilChanged().flatMapLatest { limit ->
            entries(limit).map { list ->
                val (today, before) = list.partition { DateTimeUtils.isToday(it.time) }
                HistoryList(today.toImmutableList(), before.toImmutableList(), hasMore = list.size >= limit)
            }
        }

    private companion object {
        const val PAGE_SIZE = HistoryUtil.PAGE_SIZE
    }
}
