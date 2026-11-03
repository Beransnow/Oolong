package com.huanchengfly.tieba.post.ui.page.thread

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.arch.Actions
import com.huanchengfly.tieba.post.arch.ApplicationScope
import com.huanchengfly.tieba.post.arch.MessageController
import com.huanchengfly.tieba.post.arch.PagerState
import com.huanchengfly.tieba.post.arch.query.Loads
import com.huanchengfly.tieba.post.arch.query.QueryClient
import com.huanchengfly.tieba.post.arch.query.QueryPager
import com.huanchengfly.tieba.post.arch.query.withLatestEntities
import com.huanchengfly.tieba.post.model.Post
import com.huanchengfly.tieba.post.model.PostContent
import com.huanchengfly.tieba.post.model.ThreadDetail
import com.huanchengfly.tieba.post.model.ThreadSummary
import com.huanchengfly.tieba.post.model.User
import com.huanchengfly.tieba.post.model.Viewer
import com.huanchengfly.tieba.post.repository.BlockMemo
import com.huanchengfly.tieba.post.repository.BlockPlace
import com.huanchengfly.tieba.post.repository.BlockRuleSource
import com.huanchengfly.tieba.post.repository.BlockRules
import com.huanchengfly.tieba.post.repository.BlockVerdict
import com.huanchengfly.tieba.post.repository.Blockable
import com.huanchengfly.tieba.post.repository.Posts
import com.huanchengfly.tieba.post.repository.ThreadActions
import com.huanchengfly.tieba.post.repository.ThreadCursor
import com.huanchengfly.tieba.post.repository.ThreadHistory
import com.huanchengfly.tieba.post.repository.ThreadPage
import com.huanchengfly.tieba.post.repository.ThreadQuery
import com.huanchengfly.tieba.post.repository.ThreadRepository
import com.huanchengfly.tieba.post.repository.ThreadSort
import com.huanchengfly.tieba.post.repository.Threads
import com.huanchengfly.tieba.post.repository.check
import com.huanchengfly.tieba.post.ui.navigation.ThreadKey
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

/** A reply and how the block rules block it, or some of the replies shown inside it, by their ids. */
@Immutable
data class PostItem(
    val post: Post,
    val block: BlockVerdict? = null,
    val subPostBlocks: ImmutableMap<Long, BlockVerdict> = persistentMapOf(),
) {
    val blocked: Boolean get() = block != null
}

@Immutable
data class ThreadState(
    val query: ThreadQuery,
    /** Shown until the thread loads. */
    val preview: ThreadSummary? = null,
    val thread: ThreadDetail? = null,
    val firstPost: Post? = null,
    val posts: PagerState<PostItem> = PagerState(),
    val viewer: Viewer = Viewer(null),
    /** The furthest page loaded, and the number of pages, for jumping to a page. */
    val currentPage: Int = 0,
    val totalPage: Int = 0,
)

sealed interface ThreadEffect {
    data object NavigateUp : ThreadEffect

    /** Scroll to the first reply, after jumping somewhere in the thread. */
    data object ScrollToFirstReply : ThreadEffect

    /** Scroll to a post that has just been loaded, such as the user's own reply. */
    data class ScrollToPost(val postId: Long) : ThreadEffect
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = ThreadViewModel.Factory::class)
class ThreadViewModel @AssistedInject constructor(
    @Assisted private val args: ThreadKey,
    private val repository: ThreadRepository,
    private val client: QueryClient,
    private val threads: ThreadActions,
    blockRuleSource: BlockRuleSource,
    private val history: ThreadHistory,
    private val messages: MessageController,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : ViewModel() {
    private val query = MutableStateFlow(args.query())

    /** What each loaded page says about the thread, as opposed to its posts. */
    private val meta = MutableStateFlow(Meta())

    private data class Meta(
        val page: ThreadPage? = null,
        val maxPage: Int = 0,
    )

    /** The thread as the list it was opened from showed it, if it was. */
    private val preview = client.get(Threads, args.threadId)

    private val effects = Channel<ThreadEffect>(Channel.BUFFERED)
    val effect: Flow<ThreadEffect> = effects.receiveAsFlow()

    private val actions = Actions(viewModelScope)
    private val readUpTo = MutableStateFlow<Long?>(null)

    /** Where the list was last loaded from as a whole, so that retrying loads the same place. */
    private var refreshKey: ThreadCursor = ThreadCursor.first(args.postId)
    private var scrollAfterRefresh = args.scrollToReply && args.postId == 0L

    /** A post to scroll to as soon as a page containing it loads. */
    private var scrollToPost: Long? = args.postId.takeIf { it != 0L }

    /** Holds the list shown; the one shown before is let go when another replaces it. */
    private var listJob: Job? = null

    /**
     * The posts of the query shown, kept by the app: a thread opened again, or just peeked at,
     * shows what was loaded of it, and turning a filter back off shows the list from before.
     */
    private val lists = MutableStateFlow(open(query.value, refreshKey))

    private val posts: QueryPager<ThreadCursor, Post, ThreadPage> get() = lists.value

    private fun open(query: ThreadQuery, from: ThreadCursor): QueryPager<ThreadCursor, Post, ThreadPage> {
        listJob?.cancel()
        val job = Job(viewModelScope.coroutineContext.job).also { listJob = it }
        return repository.posts.pager(viewModelScope + job, query, from)
    }

    /** Takes what the list loaded besides its posts, and scrolls to a post once a page brings it. */
    private suspend fun onLoads(loads: Loads<ThreadCursor, ThreadPage>) {
        val latest = loads.latest ?: return
        val page = latest.response
        val refreshed = loads.refreshed?.response
        meta.update { meta ->
            meta.copy(
                page = page.copy(firstPost = page.firstPost ?: refreshed?.firstPost ?: meta.page?.firstPost),
                maxPage = if (latest.refreshed) page.currentPage else maxOf(meta.maxPage, page.currentPage, refreshed?.currentPage ?: 0),
            )
        }
        if (latest.refreshed && latest.cursor == refreshKey && scrollAfterRefresh) {
            scrollAfterRefresh = false
            effects.send(ThreadEffect.ScrollToFirstReply)
        }
        scrollToPost?.let { postId ->
            if (page.posts.items.any { it.id == postId } || page.firstPost?.id == postId) {
                scrollToPost = null
                effects.send(ThreadEffect.ScrollToPost(postId))
            }
        }
    }

    /** Block flags for each post, which most state changes leave as they are. */
    private val blocked = BlockMemo<Post, PostItem>()

    private val items: Flow<PagerState<PostItem>> = combine(
        lists.flatMapLatest { it.state },
        blockRuleSource.rules,
        // Rules for one forum, or about the thread author, need to know the thread's
        meta.map { meta -> meta.page?.thread?.let { ThreadOrigin(it.forum.name, it.author.id) } }.distinctUntilChanged(),
    ) { state, rules, origin ->
        val items = blocked.map(rules to origin, state.items) { it.withBlocked(rules, origin) }.toImmutableList()
        PagerState(
            items = items,
            refresh = state.refresh,
            append = state.append,
            prepend = state.prepend,
            hasMore = state.hasMore,
            hasPrevious = state.hasPrevious,
        )
    }

    private val latestMeta = meta.withLatestEntities(client) { meta ->
        meta.copy(page = meta.page?.let { it.copy(thread = it.thread.copy(summary = Threads(it.thread.summary))) })
    }

    val state: StateFlow<ThreadState> = combine(query, latestMeta, items) { query, meta, items ->
        val page = meta.page
        ThreadState(
            query = query,
            preview = preview.takeIf { page == null },
            thread = page?.thread,
            firstPost = page?.firstPost,
            posts = items,
            viewer = page?.viewer ?: Viewer(null),
            currentPage = meta.maxPage,
            totalPage = page?.totalPage ?: 0,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ThreadState(query.value))

    init {
        lists.flatMapLatest { it.loads }.onEach(::onLoads).launchIn(viewModelScope)
        combine(meta.map { it.page?.thread }.filterNotNull(), readUpTo) { thread, postId -> thread to postId }
            .debounce(HISTORY_DELAY)
            .onEach { (thread, postId) -> history.save(thread, query.value.seeLz, findPost(postId)) }
            .launchIn(viewModelScope)
    }

    /** Loads the thread again from its start, or from its newest post when newest first. */
    fun refresh() = refreshFrom(ThreadCursor.first())

    /** Loads the thread again from where it was last loaded, after an error. */
    fun retry() = posts.refresh(refreshKey)

    fun loadMore() = posts.loadMore()

    /** Pulling up past the end: looks for replies newer than the last one loaded. */
    fun loadLatest() {
        val last = posts.state.value.items.lastOrNull() ?: return
        if (query.value.sort == ThreadSort.NewestFirst) return
        posts.loadMore(from = ThreadCursor.After(last.id))
    }

    /** The page before the first one loaded, or the newer posts when newest first. */
    fun loadPrevious() {
        if (query.value.sort == ThreadSort.NewestFirst) {
            val newest = posts.state.value.items.firstOrNull() ?: return refresh()
            val meta = meta.value
            posts.loadPrevious(
                from = ThreadCursor.newerThan(newest.id, meta.maxPage, meta.page?.lastPage ?: 0)
            )
        } else {
            posts.loadPrevious()
        }
    }

    /** Only oldest first is paged by number; newest first has no page numbers to jump to. */
    fun jumpToPage(page: Int) {
        if (query.value.sort == ThreadSort.NewestFirst) return
        refreshFrom(ThreadCursor.Number(page), scrollToFirstReply = true)
    }

    fun jumpToPost(postId: Long) {
        scrollToPost = postId
        refreshFrom(ThreadCursor.Anchor(postId))
    }

    fun setSeeLz(seeLz: Boolean) {
        if (query.value.seeLz == seeLz) return
        query.update { it.copy(seeLz = seeLz) }
        show(ThreadCursor.first())
    }

    fun setSort(sort: ThreadSort) {
        if (query.value.sort == sort) return
        query.update { it.copy(sort = sort) }
        show(ThreadCursor.first())
    }

    fun agreeThread() = actions.launch("agree-thread") {
        val thread = state.value.thread ?: return@launch
        runCatching { threads.agree(thread.summary, !thread.agree.agreed) }
            .onFailure { messages.showError(R.string.toast_agree_failed, it) }
    }

    /** Votes for [optionIds] in the thread's poll, shown at once and rolled back if it fails. */
    fun vote(optionIds: Set<Int>) = actions.launch("vote") {
        val thread = meta.value.page?.thread ?: return@launch
        val poll = thread.poll?.takeIf { !it.hasVoted && !it.closed && optionIds.isNotEmpty() } ?: return@launch
        updateThread {
            it.copy(
                poll = poll.copy(
                    options = poll.options.map { option -> if (option.id in optionIds) option.copy(votes = option.votes + 1) else option }
                        .toImmutableList(),
                    voters = poll.voters + 1,
                    hasVoted = true,
                    chosen = optionIds.toImmutableSet(),
                ),
            )
        }
        runCatching { repository.vote(thread, optionIds) }.onFailure { e ->
            updateThread { it.copy(poll = poll) }
            messages.showError(R.string.toast_vote_failed, e)
        }
    }

    fun agreePost(postId: Long) = actions.launch("agree-$postId") {
        val thread = meta.value.page?.thread ?: return@launch
        val post = posts.state.value.items.firstOrNull { it.id == postId } ?: return@launch
        runCatching { threads.agreePost(thread.id, thread.forum.id, postId, !post.agree.agreed) }
            .onFailure { messages.showError(R.string.toast_agree_failed, it) }
    }

    /** Stores the thread marked as read up to [post], by default the last one the user saw. */
    fun addStore(post: Post? = findPost(readUpTo.value)) = actions.launch("store") {
        val thread = meta.value.page?.thread ?: return@launch
        val mark = post ?: meta.value.page?.firstPost ?: return@launch
        runCatching { repository.addStore(thread.id, mark.id) }
            .onSuccess {
                updateThread { it.copy(storeMarkPostId = mark.id) }
                messages.show(R.string.message_add_favorite_success, mark.floor)
            }
            .onFailure { messages.showError(R.string.message_add_favorite_failure, it) }
    }

    fun removeStore() = actions.launch("store") {
        val page = meta.value.page ?: return@launch
        runCatching { repository.removeStore(page.thread.id, page.thread.forum.id, page.tbs) }
            .onSuccess {
                updateThread { it.copy(storeMarkPostId = null) }
                messages.show(R.string.message_remove_favorite_success)
            }
            .onFailure { messages.showError(R.string.delete_store_failure, it) }
    }

    /** The post the store mark would move to on leaving, or null when there is nothing to update. */
    fun storeMarkUpdate(): Post? {
        val thread = meta.value.page?.thread ?: return null
        val post = findPost(readUpTo.value) ?: return null
        return post.takeIf { thread.isStored && thread.storeMarkPostId != post.id && post.floor > 0 }
    }

    /** Leaves the thread, moving the store mark to [post] on the way out. */
    fun leaveUpdatingStoreMark(post: Post) {
        val threadId = meta.value.page?.thread?.id ?: return
        // The page closes right away, so the request must not be tied to it
        applicationScope.launch {
            runCatching { repository.addStore(threadId, post.id) }
                .onSuccess { messages.show(R.string.message_update_collect_mark_success) }
                .onFailure { messages.showError(R.string.message_update_collect_mark_failed, it) }
        }
        effects.trySend(ThreadEffect.NavigateUp)
    }

    fun deletePost(post: Post) = actions.launch("delete-${post.id}") {
        val page = meta.value.page ?: return@launch
        val own = post.author.id == page.viewer.user?.id
        runCatching { repository.deletePost(page.thread, post.id, ownPost = own, tbs = page.tbs) }
            .onSuccess {
                posts.update { items -> items.filterNot { it.id == post.id } }
                messages.show(R.string.toast_delete_success)
            }
            .onFailure { messages.showError(R.string.toast_delete_failure, it) }
    }

    fun deleteThread() = actions.launch("delete-thread") {
        val page = meta.value.page ?: return@launch
        val own = page.thread.author.id == page.viewer.user?.id
        runCatching { repository.deleteThread(page.thread, ownThread = own, tbs = page.tbs) }
            .onSuccess { effects.send(ThreadEffect.NavigateUp) }
            .onFailure { messages.showError(R.string.toast_delete_failure, it) }
    }

    /**
     * The user's reply was sent: load it and show it. A reply inside post [inPost] updates that
     * post where it is.
     */
    fun onReplySent(newPostId: Long, inPost: Long? = null) {
        repository.changed(args.threadId)
        if (inPost != null) return reloadPost(inPost)
        scrollToPost = newPostId
        when {
            query.value.sort == ThreadSort.NewestFirst -> loadPrevious()
            posts.state.value.hasMore -> refreshFrom(ThreadCursor.Anchor(newPostId))
            else -> posts.loadMore(from = ThreadCursor.Anchor(newPostId))
        }
    }

    private fun reloadPost(postId: Long) = actions.launch("reload-$postId") {
        runCatching { repository.page(query.value, ThreadCursor.Anchor(postId)) }
            .onSuccess { page ->
                val reloaded = page.posts.items.firstOrNull { it.id == postId } ?: return@onSuccess
                client.update(Posts, postId) { reloaded }
            }
    }

    /** The last post on screen, which history and the store mark remember. */
    fun onReadUpTo(postId: Long) {
        readUpTo.value = postId
    }

    /** Shows the list of the query now asked for, as kept if it is, from [from]. */
    private fun show(from: ThreadCursor) {
        refreshKey = from
        scrollAfterRefresh = false
        lists.value = open(query.value, from)
    }

    private fun refreshFrom(key: ThreadCursor, scrollToFirstReply: Boolean = false) {
        refreshKey = key
        scrollAfterRefresh = scrollToFirstReply
        posts.refresh(key)
    }

    private fun findPost(postId: Long?): Post? {
        postId ?: return null
        return posts.state.value.items.firstOrNull { it.id == postId }
            ?: meta.value.page?.firstPost?.takeIf { it.id == postId }
    }

    private fun updateThread(transform: (ThreadDetail) -> ThreadDetail) {
        meta.update { meta ->
            meta.copy(page = meta.page?.let { it.copy(thread = transform(it.thread)) })
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(args: ThreadKey): ThreadViewModel
    }

    private companion object {
        const val HISTORY_DELAY = 1_000L
    }
}

/** Where a thread's posts were written: its forum, by name, and who started it. */
private data class ThreadOrigin(val forum: String, val authorId: Long)

private fun Post.withBlocked(rules: BlockRules, origin: ThreadOrigin?): PostItem {
    val post = this
    fun check(author: User, content: List<PostContent>) = rules.check(
        author, content, BlockPlace.Thread, forum = origin?.forum,
        byThreadAuthor = origin != null && author.id == origin.authorId,
    )
    return PostItem(
        post = post,
        block = check(post.author, post.content),
        subPostBlocks = post.subPosts
            .mapNotNull { subPost -> check(subPost.author, subPost.content)?.let { subPost.id to it } }
            .toMap()
            .toImmutableMap(),
    )
}
