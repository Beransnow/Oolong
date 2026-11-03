package com.huanchengfly.tieba.post.ui.page.reader

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huanchengfly.tieba.post.arch.LoadStatus
import com.huanchengfly.tieba.post.arch.query.QueryClient
import com.huanchengfly.tieba.post.model.Post
import com.huanchengfly.tieba.post.model.ThreadDetail
import com.huanchengfly.tieba.post.model.ThreadSummary
import com.huanchengfly.tieba.post.repository.ThreadCursor
import com.huanchengfly.tieba.post.repository.ThreadHistory
import com.huanchengfly.tieba.post.repository.ThreadQuery
import com.huanchengfly.tieba.post.repository.ThreadRepository
import com.huanchengfly.tieba.post.repository.Threads
import com.huanchengfly.tieba.post.ui.navigation.ReaderKey
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

@Immutable
data class ReaderState(
    /** Shown until the thread loads. */
    val preview: ThreadSummary? = null,
    val thread: ThreadDetail? = null,
    val document: ReaderDocument = ReaderDocument.Empty,
    val loading: LoadStatus = LoadStatus.Idle,
    val hasMore: Boolean = false,
    /** The thread's last floor, which the reading position is told against. */
    val lastFloor: Int = 0,
)

/** Loads the thread author's floors page by page and reads them as one text. */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = ReaderViewModel.Factory::class)
class ReaderViewModel @AssistedInject constructor(
    @Assisted private val args: ReaderKey,
    repository: ThreadRepository,
    client: QueryClient,
    private val history: ThreadHistory,
) : ViewModel() {
    private val query = ThreadQuery(threadId = args.threadId, seeLz = true, forumId = args.forumId)

    // The thread page with only the author's posts keeps the same list in the app's cache
    private val pager = repository.posts.pager(viewModelScope, query, ThreadCursor.first())

    private val page = pager.loads.mapNotNull { it.latest?.response }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val preview = client.get(Threads, args.threadId)

    /** Heading shapes found so far, which stay found as more floors load. */
    private var kinds = emptySet<HeadingKind>()

    // Only the first page brings the first floor
    private val firstPost = pager.loads.map { it.refreshed?.response?.firstPost ?: it.latest?.response?.firstPost }

    private val document = combine(firstPost.distinctUntilChanged(), pager.state.map { it.items }.distinctUntilChanged()) { first, posts ->
        val all = if (first != null && posts.none { it.id == first.id }) listOf(first) + posts else posts
        buildReaderDocument(all, kinds).also { kinds = it.kinds }
    }.flowOn(Dispatchers.Default)

    private val readUpTo = MutableStateFlow<Long?>(null)

    val state: StateFlow<ReaderState> = combine(page, pager.state, document) { page, pager, document ->
        val thread = page?.thread
        ReaderState(
            preview = preview.takeIf { thread == null },
            thread = thread,
            document = document,
            loading = if (pager.refresh != LoadStatus.Idle) pager.refresh else pager.append,
            hasMore = pager.hasMore,
            lastFloor = maxOf(thread?.replyCount?.plus(1) ?: 0, pager.items.lastOrNull()?.floor ?: 0),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ReaderState(preview = preview))

    init {
        combine(page.map { it?.thread }.filterNotNull(), readUpTo) { thread, postId -> thread to postId }
            .debounce(HISTORY_DELAY)
            .onEach { (thread, postId) -> history.save(thread, seeLz = true, readUpTo = findPost(postId)) }
            .launchIn(viewModelScope)
    }

    fun loadMore() {
        val state = pager.state.value
        if (state.hasMore && state.append == LoadStatus.Idle && state.refresh == LoadStatus.Idle) pager.loadMore()
    }

    fun retry() {
        if (pager.state.value.items.isEmpty()) pager.refresh() else pager.loadMore()
    }

    /** The floor on screen, which history remembers. */
    fun onReadUpTo(postId: Long) {
        readUpTo.value = postId
    }

    private fun findPost(postId: Long?): Post? {
        postId ?: return null
        return pager.state.value.items.firstOrNull { it.id == postId }
            ?: page.value?.firstPost?.takeIf { it.id == postId }
    }

    @AssistedFactory
    interface Factory {
        fun create(args: ReaderKey): ReaderViewModel
    }

    private companion object {
        const val HISTORY_DELAY = 1_000L
    }
}
