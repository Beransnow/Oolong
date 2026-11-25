package oolong.client.ui.page.likes

import androidx.compose.runtime.Immutable
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
import oolong.client.arch.MessageController
import oolong.client.arch.Pager
import oolong.client.arch.PagerState
import oolong.client.arch.pager
import oolong.client.arch.query.QueryClient
import oolong.client.arch.query.pager
import oolong.client.model.LikedReply
import oolong.client.model.ThreadSummary
import oolong.client.repository.ThreadActions
import oolong.client.repository.Threads
import oolong.client.repository.UserRepository
import javax.inject.Inject

/** The lists of what the user liked, each in its own tab. */
enum class LikesTab { Threads, Replies }

@Immutable
data class LikesState(
    val threads: PagerState<ThreadSummary> = PagerState(),
    val replies: PagerState<LikedReply> = PagerState(),
)

/**
 * The threads and replies the user liked, each list loaded the first time its tab shows. A like
 * taken back here stays on its list, to be given again, until the list is next loaded.
 */
@HiltViewModel
class LikesViewModel @Inject constructor(
    private val repository: UserRepository,
    private val client: QueryClient,
    private val threadActions: ThreadActions,
    private val messages: MessageController,
) : ViewModel() {
    private val actions = Actions(viewModelScope)
    private val threadPager = MutableStateFlow<Pager<Int, ThreadSummary>?>(null)
    private val replyPager = MutableStateFlow<Pager<Int, LikedReply>?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<LikesState> = combine(
        threadPager.flatMapLatest { it?.state ?: flowOf(PagerState()) },
        replyPager.flatMapLatest { it?.state ?: flowOf(PagerState()) },
        ::LikesState,
    ).stateIn(viewModelScope, SharingStarted.Eagerly, LikesState())

    /** Starts loading [tab]'s list unless it already has. */
    fun open(tab: LikesTab) {
        when (tab) {
            LikesTab.Threads -> threads()
            LikesTab.Replies -> replies()
        }
    }

    fun refresh(tab: LikesTab) = when (tab) {
        LikesTab.Threads -> threads().refresh()
        LikesTab.Replies -> replies().refresh()
    }

    fun loadMore(tab: LikesTab) = when (tab) {
        LikesTab.Threads -> threads().loadMore()
        LikesTab.Replies -> replies().loadMore()
    }

    /** Takes back the like of [thread], or gives it again. */
    fun agree(thread: ThreadSummary) = actions.launch("thread-${thread.id}") {
        runCatching { threadActions.agree(thread, !thread.agree.agreed) }
            .onFailure { messages.showError(R.string.toast_agree_failed, it) }
    }

    /** Takes back the like of [reply], or gives it again; shown at once, and undone if it fails. */
    fun agree(reply: LikedReply) = actions.launch("reply-${reply.postId}") {
        val agreed = !reply.agree.agreed
        setAgreed(reply.postId, agreed)
        runCatching {
            if (reply.floorId == null) {
                threadActions.agreePost(reply.threadId, reply.forum.id, reply.postId, agreed)
            } else {
                threadActions.agreeSubPost(reply.threadId, reply.forum.id, reply.postId, agreed)
            }
        }.onFailure {
            setAgreed(reply.postId, !agreed)
            messages.showError(R.string.toast_agree_failed, it)
        }
    }

    private fun setAgreed(postId: Long, agreed: Boolean) {
        replyPager.value?.update { replies ->
            replies.map { if (it.postId == postId) it.copy(agree = it.agree.withAgreed(agreed)) else it }
        }
    }

    private fun threads(): Pager<Int, ThreadSummary> {
        threadPager.value?.let { return it }
        return client.pager(
            scope = viewModelScope,
            initialKey = 1,
            itemKey = { thread: ThreadSummary -> thread.id },
            normalize = { Threads(it) },
        ) { page -> repository.likedThreads(page) }.also { threadPager.value = it }
    }

    private fun replies(): Pager<Int, LikedReply> {
        replyPager.value?.let { return it }
        return pager(initialKey = 1, itemKey = { reply: LikedReply -> reply.postId }) { page ->
            repository.likedReplies(page)
        }.also { replyPager.value = it }
    }
}
