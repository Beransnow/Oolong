package com.huanchengfly.tieba.post.ui.page.main.explore.personalized

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.api.models.protos.ThreadInfo
import com.huanchengfly.tieba.post.api.models.protos.hasAgreed
import com.huanchengfly.tieba.post.arch.Actions
import com.huanchengfly.tieba.post.arch.LoadStatus
import com.huanchengfly.tieba.post.arch.MessageController
import com.huanchengfly.tieba.post.arch.Page
import com.huanchengfly.tieba.post.arch.Pager
import com.huanchengfly.tieba.post.arch.PagerState
import com.huanchengfly.tieba.post.arch.RefreshMode
import com.huanchengfly.tieba.post.arch.pager
import com.huanchengfly.tieba.post.model.DislikeOption
import com.huanchengfly.tieba.post.model.RecommendedThread
import com.huanchengfly.tieba.post.repository.BlockRuleSource
import com.huanchengfly.tieba.post.repository.ExploreRepository
import com.huanchengfly.tieba.post.ui.page.main.explore.FeedThread
import com.huanchengfly.tieba.post.ui.page.main.explore.feedThread
import com.huanchengfly.tieba.post.ui.page.main.explore.mapItems
import com.huanchengfly.tieba.post.ui.page.main.explore.withAgreed
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** A recommended thread, whether the block rules hide it, and why the user may not want it. */
@Immutable
data class RecommendedItem(
    val feed: FeedThread,
    val dislikeOptions: ImmutableList<DislikeOption> = persistentListOf(),
)

/** What the last refresh brought. */
@Immutable
data class FeedRefresh(
    /** How many threads it added in front of the list. */
    val count: Int,
    /** Counts the refreshes, so that one bringing as many threads as the last still shows. */
    val id: Int,
)

@Immutable
data class PersonalizedState(
    val list: PagerState<RecommendedItem> = PagerState(refresh = LoadStatus.Loading),
    /** The threads the user said they don't want, which the list hides. */
    val disliked: ImmutableSet<Long> = persistentSetOf(),
    /**
     * How many threads at the top the last refresh put in front of those already read, which the
     * list marks; 0 when the list was empty before.
     */
    val newCount: Int = 0,
    val lastRefresh: FeedRefresh? = null,
)

/**
 * Recommended threads, loaded the first time the tab shows. Like a feed, refreshing puts the new
 * threads in front of those already loaded.
 */
@HiltViewModel
class PersonalizedViewModel @Inject constructor(
    private val repository: ExploreRepository,
    private val messages: MessageController,
    blockRuleSource: BlockRuleSource,
) : ViewModel() {
    private val actions = Actions(viewModelScope)
    private val pager = MutableStateFlow<Pager<Int, RecommendedThread>?>(null)
    private val local = MutableStateFlow(Local())

    private data class Local(
        val disliked: Set<Long> = emptySet(),
        val newCount: Int = 0,
        val lastRefresh: FeedRefresh? = null,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<PersonalizedState> = combine(
        pager.flatMapLatest { it?.state ?: flowOf(PagerState(refresh = LoadStatus.Loading)) },
        local,
        blockRuleSource.rules,
    ) { list, local, rules ->
        PersonalizedState(
            list = list.mapItems { RecommendedItem(rules.feedThread(it.thread), it.dislikeOptions) },
            disliked = local.disliked.toImmutableSet(),
            newCount = local.newCount,
            lastRefresh = local.lastRefresh,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PersonalizedState())

    /** Starts loading the list unless it already has. */
    fun open() {
        pager()
    }

    fun refresh() = pager().refresh()

    fun loadMore() = pager().loadMore()

    fun agree(thread: ThreadInfo) {
        val pager = pager.value ?: return
        val agree = !thread.hasAgreed
        actions.launch("agree-${thread.threadId}") {
            pager.setAgreed(thread.threadId, agree)
            runCatching { repository.agreeThread(thread, agree) }.onFailure {
                pager.setAgreed(thread.threadId, !agree)
                messages.showError(R.string.toast_agree_failed, it)
            }
        }
    }

    /** Hides [thread] and tells the server why the user doesn't want it; it stays hidden if that fails. */
    fun dislike(thread: ThreadInfo, reasons: List<DislikeOption>, clickTime: Long) {
        local.update { it.copy(disliked = it.disliked + thread.threadId) }
        actions.launch("dislike-${thread.threadId}") {
            runCatching { repository.dislike(thread, reasons, clickTime) }
        }
    }

    private fun Pager<Int, RecommendedThread>.setAgreed(threadId: Long, agree: Boolean) = update { items ->
        items.map { item ->
            if (item.thread.threadId == threadId) item.copy(thread = item.thread.withAgreed(agree)) else item
        }
    }

    private fun pager(): Pager<Int, RecommendedThread> {
        pager.value?.let { return it }
        // Page 1 asks for new threads, the pages after it continue the list
        return pager(
            initialKey = 1,
            itemKey = { item: RecommendedThread -> item.thread.threadId },
            refreshMode = RefreshMode.Prepend,
        ) { page ->
            val loaded = pager.value?.state?.value?.items.orEmpty()
            val threads = repository.recommended(refresh = page == 1, page = page)
            if (page == 1) onRefreshed(loaded, threads)
            Page(threads, next = page + 1)
        }.also { pager.value = it }
    }

    private fun onRefreshed(loaded: List<RecommendedThread>, threads: List<RecommendedThread>) {
        val loadedIds = loaded.mapTo(HashSet()) { it.thread.threadId }
        val count = threads.distinctBy { it.thread.threadId }.count { it.thread.threadId !in loadedIds }
        local.update {
            it.copy(
                newCount = if (loaded.isEmpty()) 0 else count,
                lastRefresh = FeedRefresh(count, (it.lastRefresh?.id ?: 0) + 1),
            )
        }
    }
}
