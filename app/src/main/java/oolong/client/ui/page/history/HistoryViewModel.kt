package oolong.client.ui.page.history

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
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

/** How much of the history clearing it deletes. */
enum class HistoryRange { LastHour, Today, All }

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

/** The start of the day [time] is in. */
internal fun startOfDay(time: Long, zone: TimeZone = TimeZone.getDefault()): Long = Calendar.getInstance(zone).apply {
    timeInMillis = time
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** [entries], latest first, under the day each was visited on as of [now]. */
internal fun <T : HistoryEntry> sectionsOf(entries: List<T>, now: Long, zone: TimeZone = TimeZone.getDefault()): ImmutableList<HistorySection<T>> {
    val startOfToday = startOfDay(now, zone)
    val startOfYesterday = Calendar.getInstance(zone).apply {
        timeInMillis = startOfToday
        add(Calendar.DAY_OF_MONTH, -1)
    }.timeInMillis
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
    /** The visits chosen, by id, while choosing; null otherwise. */
    val selected: ImmutableSet<Long>? = null,
) {
    val selecting: Boolean get() = selected != null

    fun list(tab: HistoryTab): HistoryList<*> = when (tab) {
        HistoryTab.Threads -> threads
        HistoryTab.Forums -> forums
    }
}

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
    private val selected = MutableStateFlow<Set<Long>?>(null)

    val state: StateFlow<HistoryState> = combine(
        list(HistoryTab.Threads, repository::threads),
        list(HistoryTab.Forums, repository::forums),
        query,
        selected,
    ) { threads, forums, query, selected -> HistoryState(threads, forums, query, selected?.toImmutableSet()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, HistoryState())

    /** Starts choosing visits, with [entry] chosen if given. */
    fun startSelecting(entry: HistoryEntry? = null) {
        selected.value = setOfNotNull(entry?.id)
    }

    fun stopSelecting() {
        selected.value = null
    }

    /** Chooses [entry], or unchooses it if it was chosen. */
    fun toggle(entry: HistoryEntry) = selected.update { chosen ->
        chosen?.let { if (entry.id in it) it - entry.id else it + entry.id }
    }

    /** Chooses all the visits shown in [tab], or none if all of them were chosen. */
    fun toggleAll(tab: HistoryTab) {
        val shown = state.value.list(tab).sections.flatMap { section -> section.entries.map { it.id } }.toSet()
        selected.update { chosen -> chosen?.let { if (it.containsAll(shown)) it - shown else it + shown } }
    }

    /** Deletes the visits chosen and stops choosing. */
    fun deleteSelected() = actions.launch("delete-selected") {
        val ids = selected.value.orEmpty()
        if (ids.isEmpty()) return@launch
        runCatching { repository.delete(ids) }
            .onSuccess {
                selected.value = null
                messages.show(R.string.delete_history_success)
            }
            .onFailure { messages.showError(R.string.delete_history_failure, it) }
    }

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

    /** Deletes the visits in [range]: the last hour's, today's or all of them. */
    fun clear(range: HistoryRange) = actions.launch("clear") {
        val now = System.currentTimeMillis()
        val since = when (range) {
            HistoryRange.LastHour -> now - HOUR_MILLIS
            HistoryRange.Today -> startOfDay(now)
            HistoryRange.All -> null
        }
        runCatching { repository.clear(since) }
            .onSuccess { messages.show(R.string.toast_clear_success) }
            .onFailure { messages.showError(R.string.toast_clear_failure, it) }
    }

    private fun <T : HistoryEntry> list(tab: HistoryTab, entries: (limit: Int, query: String) -> Flow<List<T>>): Flow<HistoryList<T>> =
        combine(limits.map { it.getValue(tab) }, query.map { it.trim() }) { limit, query -> limit to query }.distinctUntilChanged().flatMapLatest { (limit, query) ->
            entries(limit, query).map { list ->
                HistoryList(sectionsOf(list, System.currentTimeMillis()), hasMore = list.size >= limit)
            }
        }

    private companion object {
        const val PAGE_SIZE = 100
        const val HOUR_MILLIS = 60 * 60 * 1000L
    }
}
