package com.huanchengfly.tieba.post.ui.page.main.explore.hot

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.arch.Actions
import com.huanchengfly.tieba.post.arch.LoadStatus
import com.huanchengfly.tieba.post.arch.MessageController
import com.huanchengfly.tieba.post.arch.query.QueryClient
import com.huanchengfly.tieba.post.arch.query.withLatestEntities
import com.huanchengfly.tieba.post.model.HotCategory
import com.huanchengfly.tieba.post.model.HotTopic
import com.huanchengfly.tieba.post.model.ThreadSummary
import com.huanchengfly.tieba.post.repository.BlockRuleSource
import com.huanchengfly.tieba.post.repository.ExploreRepository
import com.huanchengfly.tieba.post.repository.ThreadActions
import com.huanchengfly.tieba.post.repository.Threads
import com.huanchengfly.tieba.post.ui.page.main.explore.FeedThread
import com.huanchengfly.tieba.post.ui.page.main.explore.feedThread
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class HotState(
    val topics: ImmutableList<HotTopic> = persistentListOf(),
    val categories: ImmutableList<HotCategory> = persistentListOf(),
    /** The code of the category whose threads are listed. */
    val category: String = HotCategory.ALL,
    val threads: ImmutableList<FeedThread> = persistentListOf(),
    /** How loading the whole page went. */
    val load: LoadStatus = LoadStatus.Loading,
    /** Whether the threads of another category are on their way. */
    val switching: Boolean = false,
)

/** The hot threads ranking, with its categories and the top hot topics; loaded the first time the tab shows. */
@HiltViewModel
class HotViewModel @Inject constructor(
    private val repository: ExploreRepository,
    client: QueryClient,
    private val threads: ThreadActions,
    private val messages: MessageController,
    blockRuleSource: BlockRuleSource,
) : ViewModel() {
    private val actions = Actions(viewModelScope)
    private val local = MutableStateFlow(Local())
    private var loadJob: Job? = null

    private data class Local(
        val topics: List<HotTopic> = emptyList(),
        val categories: List<HotCategory> = emptyList(),
        val category: String = HotCategory.ALL,
        val threads: List<ThreadSummary> = emptyList(),
        val load: LoadStatus = LoadStatus.Loading,
        val switching: Boolean = false,
        val opened: Boolean = false,
    )

    val state: StateFlow<HotState> = combine(
        local.withLatestEntities(client) { it.copy(threads = it.threads.map { thread -> Threads(thread) }) },
        blockRuleSource.rules,
    ) { local, rules ->
        HotState(
            topics = local.topics.toImmutableList(),
            categories = local.categories.toImmutableList(),
            category = local.category,
            threads = local.threads.map(rules::feedThread).toImmutableList(),
            load = local.load,
            switching = local.switching,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HotState())

    /** Starts loading the page unless it already has. */
    fun open() {
        if (!local.value.opened) refresh()
    }

    /** Loads the whole page again, back on the ranking of every category. */
    fun refresh() = load(HotCategory.ALL, whole = true)

    fun selectCategory(code: String) {
        if (code != local.value.category) load(code, whole = false)
    }

    fun agree(thread: ThreadSummary) = actions.launch("agree-${thread.id}") {
        runCatching { threads.agree(thread, !thread.agree.agreed) }
            .onFailure { messages.showError(R.string.toast_agree_failed, it) }
    }

    private fun load(code: String, whole: Boolean) {
        loadJob?.cancel()
        val previous = local.value.category
        local.update {
            if (whole) it.copy(load = LoadStatus.Loading, switching = false, opened = true)
            else it.copy(category = code, switching = true)
        }
        loadJob = viewModelScope.launch {
            try {
                val hot = repository.hotThreads(code)
                local.update {
                    if (whole) {
                        it.copy(
                            topics = hot.topics,
                            categories = hot.categories,
                            category = code,
                            threads = hot.threads,
                            load = LoadStatus.Idle,
                        )
                    } else {
                        it.copy(threads = hot.threads, switching = false)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (whole) {
                    local.update { it.copy(load = LoadStatus.Error(e)) }
                } else {
                    local.update { it.copy(category = previous, switching = false) }
                    messages.show(R.string.toast_load_failed)
                }
            }
        }
    }
}
