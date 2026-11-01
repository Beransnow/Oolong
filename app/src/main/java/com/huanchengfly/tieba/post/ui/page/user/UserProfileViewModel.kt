package com.huanchengfly.tieba.post.ui.page.user

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huanchengfly.tieba.core.network.identity.SessionProvider
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.api.models.protos.PostInfoList
import com.huanchengfly.tieba.post.api.models.protos.updateAgreeStatus
import com.huanchengfly.tieba.post.arch.Actions
import com.huanchengfly.tieba.post.arch.LoadStatus
import com.huanchengfly.tieba.post.arch.MessageController
import com.huanchengfly.tieba.post.arch.Pager
import com.huanchengfly.tieba.post.arch.PagerState
import com.huanchengfly.tieba.post.arch.pager
import com.huanchengfly.tieba.post.model.LikedForum
import com.huanchengfly.tieba.post.model.UserProfile
import com.huanchengfly.tieba.post.repository.UserRepository
import com.huanchengfly.tieba.post.ui.page.user.post.PostListItemData
import com.huanchengfly.tieba.post.ui.page.user.post.toItemData
import dagger.hilt.android.lifecycle.HiltViewModel
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
import javax.inject.Inject

/** The lists a profile shows, each in its own tab. */
enum class UserTab { Threads, Posts, Forums }

@Immutable
data class UserProfileState(
    val uid: Long,
    val profile: UserProfile? = null,
    /** How loading the profile went. */
    val load: LoadStatus = LoadStatus.Idle,
    /** Whether a follow or unfollow is on its way, which the button waits for. */
    val following: Boolean = false,
    val isSelf: Boolean = false,
    val loggedIn: Boolean = false,
    val threads: PagerState<PostListItemData> = PagerState(),
    val posts: PagerState<PostListItemData> = PagerState(),
    val forums: PagerState<LikedForum> = PagerState(),
    /** Whether the user hides their threads and replies from others. */
    val postsHidden: Boolean = false,
)

/** A user's profile, and their threads, replies and forums, each loaded the first time its tab shows. */
@HiltViewModel
class UserProfileViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: UserRepository,
    private val messages: MessageController,
    private val sessionProvider: SessionProvider,
) : ViewModel() {
    private val uid: Long = checkNotNull(savedState["uid"])

    private val profile = MutableStateFlow(ProfileState())
    private val actions = Actions(viewModelScope)

    private data class ProfileState(
        val profile: UserProfile? = null,
        val load: LoadStatus = LoadStatus.Idle,
        val following: Boolean = false,
        val postsHidden: Boolean = false,
    )

    private val postPagers = MutableStateFlow<Map<UserTab, Pager<Int, PostInfoList>>>(emptyMap())
    private val forumPager = MutableStateFlow<Pager<Int, LikedForum>?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val threadsState = postPagers.flatMapLatest { it[UserTab.Threads]?.state ?: flowOf(PagerState()) }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val postsState = postPagers.flatMapLatest { it[UserTab.Posts]?.state ?: flowOf(PagerState()) }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val forumsState = forumPager.flatMapLatest { it?.state ?: flowOf(PagerState()) }

    val state: StateFlow<UserProfileState> =
        combine(profile, threadsState, postsState, forumsState) { profile, threads, posts, forums ->
            val viewer = sessionProvider.currentSession()?.uid?.toLongOrNull()
            UserProfileState(
                uid = uid,
                profile = profile.profile,
                load = profile.load,
                following = profile.following,
                isSelf = viewer == uid,
                loggedIn = viewer != null,
                threads = threads.toItems(),
                posts = posts.toItems(),
                forums = forums,
                postsHidden = profile.postsHidden,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, UserProfileState(uid))

    init {
        refreshProfile()
    }

    /** Starts loading [tab]'s list unless it already has. */
    fun open(tab: UserTab) {
        when (tab) {
            UserTab.Forums -> forums()
            else -> postsOf(tab)
        }
    }

    fun refresh(tab: UserTab) = when (tab) {
        UserTab.Forums -> forums().refresh()
        else -> postsOf(tab).refresh()
    }

    fun loadMore(tab: UserTab) = when (tab) {
        UserTab.Forums -> forums().loadMore()
        else -> postsOf(tab).loadMore()
    }

    /** Reloads the profile and every list loaded so far. */
    fun refreshAll() {
        refreshProfile()
        postPagers.value.values.forEach { it.refresh() }
        forumPager.value?.refresh()
    }

    fun refreshProfile() = actions.launch("profile") {
        profile.update { it.copy(load = LoadStatus.Loading) }
        runCatching { repository.profile(uid) }
            .onSuccess { user -> profile.update { it.copy(profile = user, load = LoadStatus.Idle) } }
            .onFailure { e -> profile.update { it.copy(load = LoadStatus.Error(e)) } }
    }

    /** Follows the user, or unfollows them when followed; the button shows the change at once. */
    fun toggleFollow() {
        val user = profile.value.profile ?: return
        if (profile.value.following) return
        val follow = !user.followed
        actions.launch("follow") {
            profile.update { it.copy(profile = user.copy(followed = follow), following = true) }
            runCatching { if (follow) repository.follow(user) else repository.unfollow(user) }
                .onSuccess { profile.update { it.copy(following = false) } }
                .onFailure { e ->
                    profile.update { it.copy(profile = it.profile?.copy(followed = !follow), following = false) }
                    messages.showError(if (follow) R.string.toast_like_failed else R.string.toast_unlike_failed, e)
                }
        }
    }

    fun agree(tab: UserTab, threadId: Long, postId: Long) {
        val pager = postPagers.value[tab] ?: return
        val post = pager.state.value.items.firstOrNull { it.thread_id == threadId && it.post_id == postId } ?: return
        val agree = post.agree?.hasAgree != 1
        actions.launch("agree-$threadId-$postId") {
            setAgreed(pager, threadId, postId, agree)
            runCatching { repository.agreeThread(post, agree) }.onFailure {
                setAgreed(pager, threadId, postId, !agree)
                messages.showError(R.string.toast_agree_failed, it)
            }
        }
    }

    private fun setAgreed(pager: Pager<Int, PostInfoList>, threadId: Long, postId: Long, agree: Boolean) {
        pager.update { items ->
            items.map { post ->
                if (post.thread_id == threadId && post.post_id == postId) post.updateAgreeStatus(if (agree) 1 else 0) else post
            }
        }
    }

    private fun postsOf(tab: UserTab): Pager<Int, PostInfoList> {
        postPagers.value[tab]?.let { return it }
        val pager = pager(initialKey = 1, itemKey = { post: PostInfoList -> "${post.thread_id}_${post.post_id}" }) { page ->
            val result = repository.posts(uid, threads = tab == UserTab.Threads, page = page)
            if (page == 1) profile.update { it.copy(postsHidden = result.hidden) }
            result.posts
        }
        postPagers.update { it + (tab to pager) }
        return pager
    }

    private fun forums(): Pager<Int, LikedForum> {
        forumPager.value?.let { return it }
        return pager(initialKey = 1, itemKey = { forum: LikedForum -> forum.id }) { page ->
            repository.likedForums(uid, page)
        }.also { forumPager.value = it }
    }
}

private fun PagerState<PostInfoList>.toItems() = PagerState(
    items = items.map { it.toItemData() }.toImmutableList(),
    refresh = refresh,
    append = append,
    hasMore = hasMore,
)
