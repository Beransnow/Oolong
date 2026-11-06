package oolong.client.ui.page.history

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import oolong.client.R
import oolong.client.arch.Actions
import oolong.client.arch.MessageController
import oolong.client.model.HistoryEntry
import oolong.client.repository.HistoryRepository
import java.util.Calendar
import java.util.TimeZone
import javax.inject.Inject

/** The kinds of history, each in its own tab. */
enum class HistoryTab { Threads, Forums }

/** The days visits are grouped under. */
enum class HistoryDay { Today, Yesterday, Earlier }

/** The visits of one [day], latest first. */
@Immutable
data class HistorySection<T : HistoryEntry>(val day: HistoryDay, val entries: ImmutableList<T>)

/** One tab's history, grouped by day; days without visits are left out. */
@Immutable
data class HistoryList<T : HistoryEntry>(
    val sections: ImmutableList<HistorySection<T>> = persistentListOf(),
    /** Whether older visits remain to be shown. */
    val hasMore: Boolean = false,
) {
    val isEmpty: Boolean get() = sections.isEmpty()
}

/** [entries], latest first, under the day each was visited on as of [now]. */
internal fun <T : HistoryEntry> sectionsOf(entries: List<T>, now: Long, zone: TimeZone = TimeZone.getDefault()): ImmutableList<HistorySection<T>> {
    val today = Calendar.getInstance(zone).apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val startOfToday = today.timeInMillis
    val startOfYesterday = today.apply { add(Calendar.DAY_OF_MONTH, -1) }.timeInMillis
    return entries
        .groupBy {
            when {
                it.time >= startOfToday -> HistoryDay.Today
                it.time >= startOfYesterday -> HistoryDay.Yesterday
                else -> HistoryDay.Earlier
            }
        }
        .toSortedMap()
        .map { (day, visits) -> HistorySection(day, visits.toImmutableList()) }
        .toImmutableList()
}

@Immutable
data class HistoryState(
    val threads: HistoryList<HistoryEntry.Thread> = HistoryList(),
    val forums: HistoryList<HistoryEntry.Forum> = HistoryList(),
    /** What the lists are narrowed to; empty for all. */
    val query: String = "",
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
    private val query = MutableStateFlow("")

    val state: StateFlow<HistoryState> = combine(
        list(HistoryTab.Threads, repository::threads),
        list(HistoryTab.Forums, repository::forums),
        query,
    ) { threads, forums, query -> HistoryState(threads, forums, query) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, HistoryState())

    /** Narrows both lists to visits that have [text]; empty shows them all again. */
    fun search(text: String) {
        query.value = text
        limits.value = HistoryTab.entries.associateWith { PAGE_SIZE }
    }

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

    private fun <T : HistoryEntry> list(tab: HistoryTab, entries: (limit: Int, query: String) -> Flow<List<T>>): Flow<HistoryList<T>> =
        combine(limits.map { it.getValue(tab) }, query.map { it.trim() }) { limit, query -> limit to query }.distinctUntilChanged().flatMapLatest { (limit, query) ->
            entries(limit, query).map { list ->
                HistoryList(sectionsOf(list, System.currentTimeMillis()), hasMore = list.size >= limit)
            }
        }

    private companion object {
        const val PAGE_SIZE = 100
    }
}
