package com.huanchengfly.tieba.post.ui.page.subposts

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huanchengfly.tieba.core.network.identity.SessionProvider
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.arch.Actions
import com.huanchengfly.tieba.post.arch.MessageController
import com.huanchengfly.tieba.post.arch.PagerState
import com.huanchengfly.tieba.post.arch.pager
import com.huanchengfly.tieba.post.model.ForumSummary
import com.huanchengfly.tieba.post.model.Post
import com.huanchengfly.tieba.post.model.SubPost
import com.huanchengfly.tieba.post.model.plainText
import com.huanchengfly.tieba.post.repository.BlockRuleSource
import com.huanchengfly.tieba.post.repository.BlockRules
import com.huanchengfly.tieba.post.repository.FloorCursor
import com.huanchengfly.tieba.post.repository.FloorPage
import com.huanchengfly.tieba.post.repository.ThreadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** A reply inside the post and whether the block rules hide it. */
@Immutable
data class SubPostItem(val subPost: SubPost, val blocked: Boolean = false)

@Immutable
data class SubPostsState(
    val threadId: Long,
    val post: Post? = null,
    val postBlocked: Boolean = false,
    val threadAuthorId: Long = 0,
    val forum: ForumSummary? = null,
    val subPosts: PagerState<SubPostItem> = PagerState(),
    /** How many replies the post has in all. */
    val totalCount: Int = 0,
    /** The logged-in user's id, or null for a guest. */
    val viewerId: Long? = null,
)

sealed interface SubPostsEffect {
    data object NavigateUp : SubPostsEffect

    /** Scroll to a reply that has just been loaded. */
    data class ScrollToSubPost(val subPostId: Long) : SubPostsEffect
}

/**
 * The replies inside one post. Opened from a reply (`loadFromSubPost`), the post and forum ids
 * passed in may not be the post's; the ones the first page returns are used from then on.
 */
@HiltViewModel
class SubPostsViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: ThreadRepository,
    blockRuleSource: BlockRuleSource,
    private val messages: MessageController,
    private val sessionProvider: SessionProvider,
) : ViewModel() {
    private val threadId: Long = checkNotNull(savedState["threadId"])
    private val subPostId: Long = savedState["subPostId"] ?: 0L
    private val loadFromSubPost: Boolean = savedState["loadFromSubPost"] ?: false

    private var postId: Long = savedState["postId"] ?: 0L
    private var forumId: Long = savedState["forumId"] ?: 0L

    private val floor = MutableStateFlow<FloorPage?>(null)
    private val effects = Channel<SubPostsEffect>(Channel.BUFFERED)
    val effect: Flow<SubPostsEffect> = effects.receiveAsFlow()
    private val actions = Actions(viewModelScope)

    private val firstKey = FloorCursor(page = 1, subPostId = subPostId.takeIf { loadFromSubPost } ?: 0L)

    /** A reply to scroll to as soon as a page containing it loads. */
    private var scrollToSubPost: Long? = firstKey.subPostId.takeIf { it != 0L }

    private val subPosts = pager(initialKey = firstKey, itemKey = { subPost: SubPost -> subPost.id }) { key ->
        val page = repository.floor(threadId, postId, forumId, key)
        postId = page.post.id
        forumId = page.forum.id
        floor.value = page
        scrollToSubPost?.let { id ->
            if (page.subPosts.items.any { it.id == id }) {
                scrollToSubPost = null
                effects.send(SubPostsEffect.ScrollToSubPost(id))
            }
        }
        page.subPosts
    }

    /** Block flags for the last list and rules seen, which most state changes leave as they are. */
    private var blocked: Triple<List<SubPost>, BlockRules, ImmutableList<SubPostItem>>? = null

    val state: StateFlow<SubPostsState> = combine(floor, subPosts.state, blockRuleSource.rules) { floor, pager, rules ->
        val cached = blocked?.takeIf { it.first === pager.items && it.second === rules }
        val items = cached?.third
            ?: pager.items.map { SubPostItem(it, rules.blocks(it.author, it.content.plainText)) }.toImmutableList()
                .also { blocked = Triple(pager.items, rules, it) }
        SubPostsState(
            threadId = threadId,
            post = floor?.post,
            postBlocked = floor?.post?.let { rules.blocks(it.author, it.content.plainText) } ?: false,
            threadAuthorId = floor?.threadAuthorId ?: 0L,
            forum = floor?.forum,
            subPosts = PagerState(
                items = items,
                refresh = pager.refresh,
                append = pager.append,
                prepend = pager.prepend,
                hasMore = pager.hasMore,
                hasPrevious = pager.hasPrevious,
            ),
            totalCount = floor?.totalCount ?: 0,
            viewerId = sessionProvider.currentSession()?.uid?.toLongOrNull(),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SubPostsState(threadId))

    fun retry() = subPosts.refresh(firstKey)

    fun refresh() = subPosts.refresh(FloorCursor(1))

    fun loadMore() = subPosts.loadMore()

    fun loadPrevious() = subPosts.loadPrevious()

    fun agreePost() = actions.launch("agree-post") {
        val post = floor.value?.post ?: return@launch
        val agree = !post.agree.agreed
        updatePost { it.copy(agree = it.agree.toggled()) }
        runCatching { repository.agreePost(threadId, forumId, post.id, agree) }.onFailure { e ->
            updatePost { it.copy(agree = it.agree.toggled()) }
            messages.showError(R.string.toast_agree_failed, e)
        }
    }

    fun agreeSubPost(subPostId: Long) = actions.launch("agree-$subPostId") {
        val subPost = subPosts.state.value.items.firstOrNull { it.id == subPostId } ?: return@launch
        val agree = !subPost.agree.agreed
        updateSubPost(subPostId) { it.copy(agree = it.agree.toggled()) }
        runCatching { repository.agreeSubPost(threadId, forumId, subPostId, agree) }.onFailure { e ->
            updateSubPost(subPostId) { it.copy(agree = it.agree.toggled()) }
            messages.showError(R.string.toast_agree_failed, e)
        }
    }

    /** Deletes a reply inside the post, or with null the post itself, which closes the page. */
    fun delete(subPost: SubPost?) = actions.launch("delete-${subPost?.id}") {
        val page = floor.value ?: return@launch
        val viewerId = sessionProvider.currentSession()?.uid?.toLongOrNull()
        val author = subPost?.author ?: page.post.author
        runCatching {
            repository.deletePost(
                forum = page.forum,
                threadId = threadId,
                postId = subPost?.id ?: page.post.id,
                ownPost = author.id == viewerId,
                isSubPost = subPost != null,
                tbs = page.tbs,
            )
        }
            .onSuccess {
                messages.show(R.string.toast_delete_success)
                if (subPost == null) {
                    effects.send(SubPostsEffect.NavigateUp)
                } else {
                    subPosts.update { items -> items.filterNot { it.id == subPost.id } }
                }
            }
            .onFailure { messages.showError(R.string.toast_delete_failure, it) }
    }

    /** The user replied inside this post: load the page with their reply and show it. */
    fun onReplySent(newSubPostId: Long) {
        scrollToSubPost = newSubPostId
        subPosts.refresh(FloorCursor(page = 1, subPostId = newSubPostId))
    }

    private fun updatePost(transform: (Post) -> Post) {
        floor.update { it?.copy(post = transform(it.post)) }
    }

    private fun updateSubPost(subPostId: Long, transform: (SubPost) -> SubPost) {
        subPosts.update { items -> items.map { if (it.id == subPostId) transform(it) else it } }
    }
}
