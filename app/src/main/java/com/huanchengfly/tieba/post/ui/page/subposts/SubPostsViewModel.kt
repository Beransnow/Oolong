package com.huanchengfly.tieba.post.ui.page.subposts

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huanchengfly.tieba.core.network.identity.SessionProvider
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.arch.Actions
import com.huanchengfly.tieba.post.arch.MessageController
import com.huanchengfly.tieba.post.arch.PagerState
import com.huanchengfly.tieba.post.arch.query.QueryClient
import com.huanchengfly.tieba.post.arch.query.pager
import com.huanchengfly.tieba.post.arch.query.withLatestEntities
import com.huanchengfly.tieba.post.model.ForumSummary
import com.huanchengfly.tieba.post.model.Post
import com.huanchengfly.tieba.post.model.PostContent
import com.huanchengfly.tieba.post.model.SubPost
import com.huanchengfly.tieba.post.model.User
import com.huanchengfly.tieba.post.repository.BlockMemo
import com.huanchengfly.tieba.post.repository.BlockPlace
import com.huanchengfly.tieba.post.repository.BlockRuleSource
import com.huanchengfly.tieba.post.repository.BlockRules
import com.huanchengfly.tieba.post.repository.BlockVerdict
import com.huanchengfly.tieba.post.repository.Blockable
import com.huanchengfly.tieba.post.repository.FloorCursor
import com.huanchengfly.tieba.post.repository.FloorPage
import com.huanchengfly.tieba.post.repository.SubPosts
import com.huanchengfly.tieba.post.repository.ThreadActions
import com.huanchengfly.tieba.post.repository.ThreadRepository
import com.huanchengfly.tieba.post.repository.check
import com.huanchengfly.tieba.post.repository.post
import com.huanchengfly.tieba.post.ui.navigation.SubPostsKey
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
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

/** A reply inside the post and how the block rules block it, if they do. */
@Immutable
data class SubPostItem(val subPost: SubPost, val block: BlockVerdict? = null) {
    val blocked: Boolean get() = block != null
}

@Immutable
data class SubPostsState(
    val threadId: Long,
    val post: Post? = null,
    val postBlock: BlockVerdict? = null,
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

    /** Scroll past the post to the replies, once they first show. */
    data object ScrollToReplies : SubPostsEffect
}

/**
 * The replies inside one post. Opened from a reply (`loadFromSubPost`), the post and forum ids
 * passed in may not be the post's; the ones the first page returns are used from then on.
 */
@HiltViewModel(assistedFactory = SubPostsViewModel.Factory::class)
class SubPostsViewModel @AssistedInject constructor(
    @Assisted key: SubPostsKey,
    private val repository: ThreadRepository,
    private val client: QueryClient,
    private val threads: ThreadActions,
    blockRuleSource: BlockRuleSource,
    private val messages: MessageController,
    private val sessionProvider: SessionProvider,
) : ViewModel() {
    private val threadId: Long = key.threadId
    private val subPostId: Long = key.subPostId
    private val loadFromSubPost: Boolean = key.loadFromSubPost

    private var postId: Long = key.postId
    private var forumId: Long = key.forumId

    private val floor = MutableStateFlow<FloorPage?>(null)
    private val effects = Channel<SubPostsEffect>(Channel.BUFFERED)
    val effect: Flow<SubPostsEffect> = effects.receiveAsFlow()
    private val actions = Actions(viewModelScope)

    private val firstKey = FloorCursor(page = 1, subPostId = subPostId.takeIf { loadFromSubPost } ?: 0L)

    /** A reply to scroll to as soon as a page containing it loads. */
    private var scrollToSubPost: Long? = firstKey.subPostId.takeIf { it != 0L }

    private var scrollToReplies = key.atReplies

    private val subPosts = client.pager(
        scope = viewModelScope,
        initialKey = firstKey,
        itemKey = { subPost: SubPost -> subPost.id },
        normalize = { SubPosts(it) },
    ) { key ->
        val page = repository.floor(threadId, postId, forumId, key)
        postId = page.post.id
        forumId = page.forum.id
        floor.value = page
        if (scrollToReplies && page.subPosts.items.isNotEmpty()) {
            scrollToReplies = false
            effects.send(SubPostsEffect.ScrollToReplies)
        }
        scrollToSubPost?.let { id ->
            if (page.subPosts.items.any { it.id == id }) {
                scrollToSubPost = null
                effects.send(SubPostsEffect.ScrollToSubPost(id))
            }
        }
        page.subPosts
    }

    /** Block flags for the last list and rules seen, which most state changes leave as they are. */
    private val blocked = BlockMemo<SubPost, SubPostItem>()

    /** The post as liked here or anywhere else it shows. */
    private val latestFloor = floor.withLatestEntities(client) { it?.copy(post = post(it.post)) }

    val state: StateFlow<SubPostsState> = combine(latestFloor, subPosts.state, blockRuleSource.rules) { floor, pager, rules ->
        val forumName = floor?.forum?.name
        val threadAuthorId = floor?.threadAuthorId ?: 0L
        fun check(author: User, content: List<PostContent>) = rules.check(
            author, content, BlockPlace.Thread, forum = forumName, byThreadAuthor = threadAuthorId != 0L && author.id == threadAuthorId,
        )
        val items = blocked.map(Triple(rules, forumName, threadAuthorId), pager.items) { SubPostItem(it, check(it.author, it.content)) }
            .toImmutableList()
        SubPostsState(
            threadId = threadId,
            post = floor?.post,
            postBlock = floor?.post?.let { check(it.author, it.content) },
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
        val post = state.value.post ?: return@launch
        runCatching { threads.agreePost(threadId, forumId, post.id, !post.agree.agreed) }
            .onFailure { messages.showError(R.string.toast_agree_failed, it) }
    }

    fun agreeSubPost(subPostId: Long) = actions.launch("agree-$subPostId") {
        val subPost = subPosts.state.value.items.firstOrNull { it.id == subPostId } ?: return@launch
        runCatching { threads.agreeSubPost(threadId, forumId, subPostId, !subPost.agree.agreed) }
            .onFailure { messages.showError(R.string.toast_agree_failed, it) }
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

    @AssistedFactory
    interface Factory {
        fun create(key: SubPostsKey): SubPostsViewModel
    }
}
