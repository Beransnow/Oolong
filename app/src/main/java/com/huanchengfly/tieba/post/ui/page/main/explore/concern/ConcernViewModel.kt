package com.huanchengfly.tieba.post.ui.page.main.explore.concern

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.arch.Actions
import com.huanchengfly.tieba.post.arch.LoadStatus
import com.huanchengfly.tieba.post.arch.MessageController
import com.huanchengfly.tieba.post.arch.Pager
import com.huanchengfly.tieba.post.arch.PagerState
import com.huanchengfly.tieba.post.arch.pager
import com.huanchengfly.tieba.post.model.ThreadSummary
import com.huanchengfly.tieba.post.repository.BlockRuleSource
import com.huanchengfly.tieba.post.repository.ExploreRepository
import com.huanchengfly.tieba.post.ui.page.main.explore.FeedThread
import com.huanchengfly.tieba.post.ui.page.main.explore.feedThread
import com.huanchengfly.tieba.post.ui.page.main.explore.mapItems
import com.huanchengfly.tieba.post.ui.page.main.explore.withAgreed
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The threads of the users the user follows, loaded the first time the tab shows. */
@HiltViewModel
class ConcernViewModel @Inject constructor(
    private val repository: ExploreRepository,
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

    fun agree(thread: ThreadSummary) {
        val pager = pager.value ?: return
        val agree = !thread.agree.agreed
        actions.launch("agree-${thread.id}") {
            pager.update { it.withAgreed(thread.id, agree) }
            runCatching { repository.agreeThread(thread, agree) }.onFailure {
                pager.update { it.withAgreed(thread.id, !agree) }
                messages.showError(R.string.toast_agree_failed, it)
            }
        }
    }

    private fun pager(): Pager<String, ThreadSummary> {
        pager.value?.let { return it }
        return pager(initialKey = "", itemKey = { thread: ThreadSummary -> thread.id }) { pageTag ->
            repository.followedUsersThreads(pageTag)
        }.also { pager.value = it }
    }
}
