package com.huanchengfly.tieba.post.ui.page.forum

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huanchengfly.tieba.core.network.identity.SessionProvider
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.api.models.protos.ThreadInfo
import com.huanchengfly.tieba.post.api.models.protos.abstractText
import com.huanchengfly.tieba.post.api.models.protos.updateAgreeStatus
import com.huanchengfly.tieba.post.arch.Actions
import com.huanchengfly.tieba.post.arch.ImmutableHolder
import com.huanchengfly.tieba.post.arch.LoadStatus
import com.huanchengfly.tieba.post.arch.MessageController
import com.huanchengfly.tieba.post.arch.Pager
import com.huanchengfly.tieba.post.arch.PagerState
import com.huanchengfly.tieba.post.arch.pager
import com.huanchengfly.tieba.post.arch.wrapImmutable
import com.huanchengfly.tieba.post.model.ForumDetail
import com.huanchengfly.tieba.post.model.ForumSort
import com.huanchengfly.tieba.post.model.ForumTab
import com.huanchengfly.tieba.post.model.User
import com.huanchengfly.tieba.post.repository.BlockRuleSource
import com.huanchengfly.tieba.post.repository.BlockRules
import com.huanchengfly.tieba.post.repository.ForumCursor
import com.huanchengfly.tieba.post.repository.ForumHistory
import com.huanchengfly.tieba.post.repository.ForumRepository
import com.huanchengfly.tieba.post.repository.ForumSortStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
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
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A thread in a forum's list, and whether the block rules hide it. */
@Immutable
data class ForumThreadItem(val thread: ImmutableHolder<ThreadInfo>, val blocked: Boolean = false)

@Immutable
data class ForumState(
    val forumName: String,
    /** The forum, once the first page of a tab that lists it has loaded. */
    val forum: ForumDetail? = null,
    val selectedTab: ForumTab = ForumTab.Latest,
    val sort: ForumSort = ForumSort.ReplyTime,
    /** The class of good threads listed, 0 for all of them. */
    val goodClassId: Int = 0,
    val lists: ImmutableMap<ForumTab, PagerState<ForumThreadItem>> = persistentMapOf(),
    val loggedIn: Boolean = false,
) {
    val tabs: ImmutableList<ForumTab> get() = forum?.tabs ?: ForumTab.Fixed

    fun list(tab: ForumTab): PagerState<ForumThreadItem> = lists[tab] ?: PagerState()

    /** How the page's first load went, which shows instead of the page until the forum is known. */
    val firstLoad: LoadStatus get() = if (forum != null) LoadStatus.Idle else list(selectedTab).refresh
}

/**
 * A forum's page: the forum, and a list of threads for each of its tabs, each loaded the first
 * time its tab is shown.
 */
@HiltViewModel
class ForumViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: ForumRepository,
    blockRuleSource: BlockRuleSource,
    private val history: ForumHistory,
    private val sortStore: ForumSortStore,
    private val messages: MessageController,
    private val sessionProvider: SessionProvider,
) : ViewModel() {
    private val forumName: String = checkNotNull(savedState["forumName"])

    private val forum = MutableStateFlow<ForumDetail?>(null)
    private var tbs: String? = null
    private val selection = MutableStateFlow(Selection())
    private val pagers = MutableStateFlow<Map<ForumTab, Pager<ForumCursor, ThreadInfo>>>(emptyMap())
    private val actions = Actions(viewModelScope)

    private data class Selection(
        val tab: ForumTab = ForumTab.Latest,
        val sort: ForumSort = ForumSort.ReplyTime,
        val goodClassId: Int = 0,
    )

    /** Block flags for the last lists and rules seen, which most state changes leave as they are. */
    private val blocked = mutableMapOf<ForumTab, Triple<List<ThreadInfo>, BlockRules, ImmutableList<ForumThreadItem>>>()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val listStates = pagers.flatMapLatest { pagers ->
        if (pagers.isEmpty()) {
            flowOf(emptyMap())
        } else {
            combine(pagers.map { (tab, pager) -> pager.state.map { tab to it } }) { it.toMap() }
        }
    }

    val state: StateFlow<ForumState> =
        combine(forum, selection, listStates, blockRuleSource.rules) { forum, selection, lists, rules ->
            ForumState(
                forumName = forumName,
                forum = forum,
                selectedTab = selection.tab,
                sort = selection.sort,
                goodClassId = selection.goodClassId,
                lists = lists.mapValues { (tab, list) -> list.withItems(items(tab, list.items, rules)) }.toImmutableMap(),
                loggedIn = sessionProvider.currentSession() != null,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, ForumState(forumName))

    init {
        viewModelScope.launch {
            val sort = runCatching { sortStore.sort(forumName) }.getOrDefault(ForumSort.ReplyTime)
            selection.update { it.copy(sort = sort) }
            pagerOf(selection.value.tab)
        }
    }

    fun selectTab(tab: ForumTab) {
        selection.update { it.copy(tab = tab) }
        pagerOf(tab)
    }

    fun setSort(sort: ForumSort) {
        if (sort == selection.value.sort) return
        selection.update { it.copy(sort = sort) }
        viewModelScope.launch { runCatching { sortStore.setSort(forumName, sort) } }
        pagers.value[ForumTab.Latest]?.refresh()
    }

    fun setGoodClass(classId: Int) {
        if (classId == selection.value.goodClassId) return
        selection.update { it.copy(goodClassId = classId) }
        pagers.value[ForumTab.Good]?.refresh()
    }

    /** Reloads the shown tab, and with it the forum when that tab lists it. */
    fun refresh() = pagerOf(selection.value.tab).refresh()

    fun loadMore() = pagerOf(selection.value.tab).loadMore()

    /** Follows the forum, or when it is followed signs in to it. */
    fun followOrSignIn() {
        val forum = forum.value ?: return
        if (!forum.liked) like(forum) else if (!forum.signedIn) signIn(forum)
    }

    private fun like(forum: ForumDetail) = actions.launch("like") {
        runCatching { repository.like(forum, tbs) }
            .onSuccess {
                this@ForumViewModel.forum.value = it
                messages.show(R.string.toast_like_success, it.memberCount.toString())
            }
            .onFailure { messages.showError(R.string.toast_like_failed, it) }
    }

    private fun signIn(forum: ForumDetail) = actions.launch("sign-in") {
        runCatching { repository.signIn(forum, tbs) }
            .onSuccess {
                this@ForumViewModel.forum.value = it.forum
                messages.show(R.string.toast_sign_success, it.bonusPoints.toString(), it.rank.toString())
            }
            .onFailure { messages.showError(R.string.toast_sign_failed, it) }
    }

    fun unlike() {
        val forum = forum.value ?: return
        actions.launch("unlike") {
            runCatching { repository.unlike(forum, tbs) }
                .onSuccess {
                    this@ForumViewModel.forum.value = it
                    messages.show(R.string.toast_unlike_success)
                }
                .onFailure { messages.showError(R.string.toast_unlike_failed, it) }
        }
    }

    fun agree(tab: ForumTab, threadId: Long) {
        val pager = pagers.value[tab] ?: return
        val thread = pager.state.value.items.firstOrNull { it.id == threadId } ?: return
        val agree = thread.agree?.hasAgree != 1
        actions.launch("agree-$threadId") {
            setAgreed(threadId, agree)
            runCatching { repository.agreeThread(thread, agree) }.onFailure {
                setAgreed(threadId, !agree)
                messages.showError(R.string.toast_agree_failed, it)
            }
        }
    }

    /** Marks the thread agreed or not in every list holding it. */
    private fun setAgreed(threadId: Long, agree: Boolean) {
        pagers.value.values.forEach { pager ->
            pager.update { items ->
                items.map { if (it.id == threadId) it.updateAgreeStatus(if (agree) 1 else 0) else it }
            }
        }
    }

    private fun pagerOf(tab: ForumTab): Pager<ForumCursor, ThreadInfo> {
        pagers.value[tab]?.let { return it }
        val pager = pager(initialKey = ForumCursor.first(tab), itemKey = { thread: ThreadInfo -> thread.id }) { cursor ->
            val selection = selection.value
            val page = repository.threads(
                forumName = forumName,
                forumId = forum.value?.id ?: 0,
                tab = tab,
                cursor = cursor,
                sort = selection.sort,
                goodClassId = selection.goodClassId,
            )
            page.forum?.let { loaded ->
                if (forum.value == null) history.save(loaded)
                forum.value = loaded
                tbs = page.tbs ?: tbs
            }
            page.threads
        }
        pagers.update { it + (tab to pager) }
        return pager
    }

    private fun items(tab: ForumTab, threads: List<ThreadInfo>, rules: BlockRules): ImmutableList<ForumThreadItem> {
        blocked[tab]?.takeIf { it.first === threads && it.second === rules }?.let { return it.third }
        val items = threads.map { ForumThreadItem(it.wrapImmutable(), blocked = rules.blocks(it)) }.toImmutableList()
        blocked[tab] = Triple(threads, rules, items)
        return items
    }

    private fun BlockRules.blocks(thread: ThreadInfo): Boolean {
        val author = User(id = thread.authorId, name = thread.author?.name.orEmpty(), nameShow = "", portrait = "")
        return blocks(author, thread.title) || blocks(author, thread.abstractText)
    }
}

private fun <T, R> PagerState<T>.withItems(items: ImmutableList<R>) = PagerState(
    items = items,
    refresh = refresh,
    append = append,
    prepend = prepend,
    hasMore = hasMore,
    hasPrevious = hasPrevious,
)
