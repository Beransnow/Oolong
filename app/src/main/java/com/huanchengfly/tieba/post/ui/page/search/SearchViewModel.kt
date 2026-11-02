package com.huanchengfly.tieba.post.ui.page.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.arch.Actions
import com.huanchengfly.tieba.post.arch.LoadStatus
import com.huanchengfly.tieba.post.arch.MessageController
import com.huanchengfly.tieba.post.arch.Pager
import com.huanchengfly.tieba.post.arch.PagerState
import com.huanchengfly.tieba.post.arch.pager
import com.huanchengfly.tieba.post.model.SearchHistoryItem
import com.huanchengfly.tieba.post.model.SearchMatches
import com.huanchengfly.tieba.post.model.SearchOrder
import com.huanchengfly.tieba.post.model.SearchedForum
import com.huanchengfly.tieba.post.model.SearchedPost
import com.huanchengfly.tieba.post.model.SearchedUser
import com.huanchengfly.tieba.post.model.User
import com.huanchengfly.tieba.post.repository.BlockPlace
import com.huanchengfly.tieba.post.repository.BlockRuleSource
import com.huanchengfly.tieba.post.repository.BlockVerdict
import com.huanchengfly.tieba.post.repository.Blockable
import com.huanchengfly.tieba.post.repository.SearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The kinds of results a search shows, each in its own tab. */
enum class SearchTab { Forums, Threads, Users }

/** Forums or users found for a keyword, and how loading them went. */
@Immutable
data class MatchesState<T>(
    val matches: SearchMatches<T> = SearchMatches(),
    val load: LoadStatus = LoadStatus.Loading,
)

@Immutable
data class SearchState(
    /** The keyword searched for; empty while the user is still typing one. */
    val keyword: String = "",
    val history: ImmutableList<SearchHistoryItem> = persistentListOf(),
    /** Keywords that start like what the user typed. */
    val suggestions: ImmutableList<String> = persistentListOf(),
    val forums: MatchesState<SearchedForum> = MatchesState(),
    val threads: PagerState<SearchedPost> = PagerState(refresh = LoadStatus.Loading),
    /** How the block rules block found threads, by [SearchedPost.key]. */
    val threadBlocks: ImmutableMap<String, BlockVerdict> = persistentMapOf(),
    val users: MatchesState<SearchedUser> = MatchesState(),
    val order: SearchOrder = SearchOrder.Newest,
)

/**
 * Searching Tieba: the keywords searched before, suggestions while typing and, once the user
 * searches, the forums, threads and users found, each loaded the first time its tab shows.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: SearchRepository,
    private val messages: MessageController,
    blockRuleSource: BlockRuleSource,
) : ViewModel() {
    private val actions = Actions(viewModelScope)
    private val input = MutableStateFlow("")
    private val suggestions = MutableStateFlow<List<String>>(emptyList())
    private val local = MutableStateFlow(Local())
    private val threadPager = MutableStateFlow<Pager<Int, SearchedPost>?>(null)
    private val loadJobs = mutableMapOf<SearchTab, Job>()

    private data class Local(
        val keyword: String = "",
        val order: SearchOrder = SearchOrder.Newest,
        val forums: MatchesState<SearchedForum> = MatchesState(),
        val users: MatchesState<SearchedUser> = MatchesState(),
        /** The tabs loaded for the keyword. */
        val opened: Set<SearchTab> = emptySet(),
    )

    val state: StateFlow<SearchState> = combine(
        local,
        repository.history,
        suggestions,
        threadPager.flatMapLatest { it?.state ?: flowOf(PagerState(refresh = LoadStatus.Loading)) },
        blockRuleSource.rules,
    ) { local, history, suggestions, threads, rules ->
        SearchState(
            keyword = local.keyword,
            history = history.toImmutableList(),
            suggestions = suggestions.toImmutableList(),
            forums = local.forums,
            threads = threads,
            threadBlocks = threads.items.mapNotNull { post ->
                val author = User(post.author.id, post.author.name, post.author.nameShow, post.author.portrait)
                rules.check(Blockable(author, post.content, BlockPlace.Feeds, title = post.title, forum = post.forumName))
                    ?.let { post.key to it }
            }.toMap().toImmutableMap(),
            users = local.users,
            order = local.order,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SearchState())

    init {
        input.debounce(SUGGESTION_DELAY_MILLIS)
            .mapLatest { text ->
                if (text.isBlank()) emptyList() else runCatching { repository.suggestions(text) }.getOrDefault(emptyList())
            }
            .onEach { suggestions.value = it }
            .launchIn(viewModelScope)
    }

    /** Asks for suggestions for what the user typed, once they pause typing. */
    fun onInputChange(text: String) {
        input.value = text
    }

    /** Searches for [keyword], or with a blank one goes back to the history. */
    fun search(keyword: String) {
        val trimmed = keyword.trim()
        loadJobs.values.forEach { it.cancel() }
        loadJobs.clear()
        threadPager.value = null
        local.update { Local(keyword = trimmed, order = it.order) }
        if (trimmed.isNotEmpty()) viewModelScope.launch { runCatching { repository.remember(trimmed) } }
    }

    /** Starts loading [tab] for the keyword unless it already has. */
    fun open(tab: SearchTab) {
        val current = local.value
        if (current.keyword.isEmpty() || tab in current.opened) return
        local.update { it.copy(opened = it.opened + tab) }
        load(tab)
    }

    fun refresh(tab: SearchTab) {
        if (local.value.keyword.isEmpty()) return
        local.update { it.copy(opened = it.opened + tab) }
        load(tab)
    }

    fun loadMoreThreads() {
        threadPager.value?.loadMore()
    }

    fun setOrder(order: SearchOrder) {
        if (order == local.value.order) return
        local.update { it.copy(order = order) }
        if (SearchTab.Threads in local.value.opened) load(SearchTab.Threads)
    }

    fun forget(item: SearchHistoryItem) = actions.launch("forget-${item.id}") {
        runCatching { repository.forget(item) }.onFailure { messages.showError(R.string.toast_delete_failure, it) }
    }

    fun clearHistory() = actions.launch("clear") {
        runCatching { repository.clearHistory() }
            .onSuccess { messages.show(R.string.toast_clear_success) }
            .onFailure { messages.showError(R.string.toast_clear_failure, it) }
    }

    private fun load(tab: SearchTab) {
        val keyword = local.value.keyword
        when (tab) {
            SearchTab.Threads -> {
                val order = local.value.order
                threadPager.value = pager(initialKey = 1, itemKey = SearchedPost::key) { page ->
                    repository.threads(keyword, order, page)
                }
            }

            SearchTab.Forums -> loadMatches(tab, { repository.forums(keyword) }) { state -> copy(forums = state(forums)) }
            SearchTab.Users -> loadMatches(tab, { repository.users(keyword) }) { state -> copy(users = state(users)) }
        }
    }

    private fun <T> loadMatches(
        tab: SearchTab,
        fetch: suspend () -> SearchMatches<T>,
        set: Local.((MatchesState<T>) -> MatchesState<T>) -> Local,
    ) {
        loadJobs[tab]?.cancel()
        local.update { it.set { state -> state.copy(load = LoadStatus.Loading) } }
        loadJobs[tab] = viewModelScope.launch {
            try {
                val matches = fetch()
                local.update { it.set { MatchesState(matches, LoadStatus.Idle) } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                local.update { it.set { state -> state.copy(load = LoadStatus.Error(e)) } }
            }
        }
    }

    private companion object {
        const val SUGGESTION_DELAY_MILLIS = 500L
    }
}
