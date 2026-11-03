package oolong.client.ui.page.main.explore.concern

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import oolong.client.R
import oolong.client.arch.Actions
import oolong.client.arch.LoadStatus
import oolong.client.arch.MessageController
import oolong.client.arch.Pager
import oolong.client.arch.PagerState
import oolong.client.arch.query.QueryClient
import oolong.client.arch.query.pager
import oolong.client.model.ThreadSummary
import oolong.client.repository.BlockRuleSource
import oolong.client.repository.ExploreRepository
import oolong.client.repository.ThreadActions
import oolong.client.repository.Threads
import oolong.client.ui.page.main.explore.FeedThread
import oolong.client.ui.page.main.explore.feedThread
import oolong.client.ui.page.main.explore.mapItems
import javax.inject.Inject

/** The threads of the users the user follows, loaded the first time the tab shows. */
@HiltViewModel
class ConcernViewModel @Inject constructor(
    private val repository: ExploreRepository,
    private val client: QueryClient,
    private val threads: ThreadActions,
    private val messages: MessageController,
    blockRuleSource: BlockRuleSource,
) : ViewModel() {
    private val actions = Actions(viewModelScope)
    private val pager = MutableStateFlow<Pager<String, ThreadSummary>?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<PagerState<FeedThread>> = combine(
        pager.flatMapLatest { it?.state ?: flowOf(PagerState(refresh = LoadStatus.Loading)) },
        blockRuleSource.rules,
    ) { list, rules ->
        list.mapItems(rules::feedThread)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PagerState(refresh = LoadStatus.Loading))

    /** Starts loading the list unless it already has. */
    fun open() {
        pager()
    }

    fun refresh() = pager().refresh()

    fun loadMore() = pager().loadMore()

    fun agree(thread: ThreadSummary) = actions.launch("agree-${thread.id}") {
        runCatching { threads.agree(thread, !thread.agree.agreed) }
            .onFailure { messages.showError(R.string.toast_agree_failed, it) }
    }

    private fun pager(): Pager<String, ThreadSummary> {
        pager.value?.let { return it }
        return client.pager(
            scope = viewModelScope,
            initialKey = "",
            itemKey = { thread: ThreadSummary -> thread.id },
            normalize = { Threads(it) },
        ) { pageTag ->
            repository.followedUsersThreads(pageTag)
        }.also { pager.value = it }
    }
}
