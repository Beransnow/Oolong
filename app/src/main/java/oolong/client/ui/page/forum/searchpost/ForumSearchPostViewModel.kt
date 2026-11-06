package oolong.client.ui.page.forum.searchpost

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import oolong.client.R
import oolong.client.arch.Actions
import oolong.client.arch.LoadStatus
import oolong.client.arch.MessageController
import oolong.client.arch.Pager
import oolong.client.arch.PagerState
import oolong.client.arch.pager
import oolong.client.model.ForumSearchOrder
import oolong.client.model.SearchHistoryItem
import oolong.client.model.SearchedPost
import oolong.client.repository.SearchRepository
import oolong.client.ui.navigation.ForumSearchPostKey

@Immutable
data class ForumSearchPostState(
    val forumName: String,
    /** The keyword searched for; empty until the user searches, while the history shows. */
    val keyword: String = "",
    val order: ForumSearchOrder = ForumSearchOrder.Newest,
    val onlyThreads: Boolean = false,
    val list: PagerState<SearchedPost> = PagerState(refresh = LoadStatus.Loading),
    /** The keywords searched for in any forum. */
    val history: ImmutableList<SearchHistoryItem> = persistentListOf(),
)

/** Searching one forum's threads and replies. */
@HiltViewModel(assistedFactory = ForumSearchPostViewModel.Factory::class)
class ForumSearchPostViewModel @AssistedInject constructor(
    @Assisted key: ForumSearchPostKey,
    private val repository: SearchRepository,
    private val messages: MessageController,
) : ViewModel() {
    private val forumName: String = key.forumName
    private val forumId: Long = key.forumId

    private val actions = Actions(viewModelScope)
    private val local = MutableStateFlow(Local())
    private val pager = MutableStateFlow<Pager<Int, SearchedPost>?>(null)

    private data class Local(
        val keyword: String = "",
        val order: ForumSearchOrder = ForumSearchOrder.Newest,
        val onlyThreads: Boolean = false,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ForumSearchPostState> = combine(
        local,
        pager.flatMapLatest { it?.state ?: flowOf(PagerState(refresh = LoadStatus.Loading)) },
        repository.forumHistory,
    ) { local, list, history ->
        ForumSearchPostState(
            forumName = forumName,
            keyword = local.keyword,
            order = local.order,
            onlyThreads = local.onlyThreads,
            list = list,
            history = history.toImmutableList(),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ForumSearchPostState(forumName))

    /** Searches for [keyword], or with a blank one goes back to the history. */
    fun search(keyword: String) {
        val trimmed = keyword.trim()
        local.update { it.copy(keyword = trimmed) }
        if (trimmed.isEmpty()) {
            pager.value = null
            return
        }
        viewModelScope.launch { runCatching { repository.remember(trimmed, forumName) } }
        load()
    }

    fun refresh() = load()

    fun loadMore() {
        pager.value?.loadMore()
    }

    fun setOrder(order: ForumSearchOrder) {
        if (order == local.value.order) return
        local.update { it.copy(order = order) }
        load()
    }

    fun setOnlyThreads(onlyThreads: Boolean) {
        if (onlyThreads == local.value.onlyThreads) return
        local.update { it.copy(onlyThreads = onlyThreads) }
        load()
    }

    fun forget(item: SearchHistoryItem) = actions.launch("forget-${item.id}") {
        runCatching { repository.forget(item) }.onFailure { messages.showError(R.string.toast_delete_failure, it) }
    }

    fun clearHistory() = actions.launch("clear") {
        runCatching { repository.clearForumHistory() }
            .onSuccess { messages.show(R.string.toast_clear_success) }
            .onFailure { messages.showError(R.string.toast_clear_failure, it) }
    }

    private fun load() {
        val (keyword, order, onlyThreads) = local.value
        if (keyword.isEmpty()) return
        pager.value = pager(initialKey = 1, itemKey = { post: SearchedPost -> "${post.threadId}_${post.postId}_${post.subPostId}" }) { page ->
            repository.threadsInForum(keyword, forumName, forumId, order, onlyThreads, page)
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(key: ForumSearchPostKey): ForumSearchPostViewModel
    }
}
