package com.huanchengfly.tieba.post.ui.page.user

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huanchengfly.tieba.core.network.identity.SessionProvider
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.arch.Actions
import com.huanchengfly.tieba.post.arch.LoadStatus
import com.huanchengfly.tieba.post.arch.MessageController
import com.huanchengfly.tieba.post.arch.Pager
import com.huanchengfly.tieba.post.arch.PagerState
import com.huanchengfly.tieba.post.arch.pager
import com.huanchengfly.tieba.post.arch.query.QueryClient
import com.huanchengfly.tieba.post.arch.query.pager
import com.huanchengfly.tieba.post.model.LikedForum
import com.huanchengfly.tieba.post.model.UserPost
import com.huanchengfly.tieba.post.model.UserProfile
import com.huanchengfly.tieba.post.model.UserThread
import com.huanchengfly.tieba.post.models.database.Block
import com.huanchengfly.tieba.post.repository.BlackSettings
import com.huanchengfly.tieba.post.repository.BlockListRepository
import com.huanchengfly.tieba.post.repository.ThreadActions
import com.huanchengfly.tieba.post.repository.Threads
import com.huanchengfly.tieba.post.repository.UserRepository
import com.huanchengfly.tieba.post.ui.navigation.UserProfileKey
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

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
    val threads: PagerState<UserPost> = PagerState(),
    val posts: PagerState<UserPost> = PagerState(),
    val forums: PagerState<LikedForum> = PagerState(),
    /** Whether the user hides their threads and replies from others. */
    val postsHidden: Boolean = false,
    /** What the viewer bars this user from, once asked for; null until then. */
    val black: BlackSettings? = null,
)

/** A user's profile, and their threads, replies and forums, each loaded the first time its tab shows. */
@HiltViewModel(assistedFactory = UserProfileViewModel.Factory::class)
class UserProfileViewModel @AssistedInject constructor(
    @Assisted key: UserProfileKey,
    private val repository: UserRepository,
    private val client: QueryClient,
    private val threads: ThreadActions,
    private val blockList: BlockListRepository,
    private val messages: MessageController,
    private val sessionProvider: SessionProvider,
) : ViewModel() {
    private val uid: Long = key.uid

    private val profile = MutableStateFlow(ProfileState())
    private val actions = Actions(viewModelScope)

    private data class ProfileState(
        val profile: UserProfile? = null,
        val load: LoadStatus = LoadStatus.Idle,
        val following: Boolean = false,
        val postsHidden: Boolean = false,
        val black: BlackSettings? = null,
    )

    private val postPagers = MutableStateFlow<Map<UserTab, Pager<Int, UserPost>>>(emptyMap())
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
                black = profile.black,
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

    /** Puts the user on the block list, or on the allow list when [allow]. */
    fun addToList(allow: Boolean) {
        val user = profile.value.profile ?: return
        val category = if (allow) Block.CATEGORY_WHITE_LIST else Block.CATEGORY_BLACK_LIST
        actions.launch("block") {
            runCatching { blockList.addUser(category, user.id, user.name, user.nameShow) }
                .onSuccess { messages.show(R.string.toast_add_success) }
        }
    }

    /** Asks what the viewer bars this user from, for [UserProfileState.black]. */
    fun loadBlack() = actions.launch("black") {
        profile.update { it.copy(black = null) }
        runCatching { repository.blackSettings(uid) }
            .onSuccess { settings -> profile.update { it.copy(black = settings) } }
            .onFailure { messages.showError(R.string.toast_black_load_failed, it) }
    }

    fun setBlack(settings: BlackSettings) = actions.launch("black") {
        runCatching { repository.setBlack(uid, settings) }
            .onSuccess {
                profile.update { it.copy(black = settings) }
                messages.show(if (settings.any) R.string.toast_black_saved else R.string.toast_black_removed)
            }
            .onFailure { messages.showError(R.string.toast_black_save_failed, it) }
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
        val thread = pager.state.value.items
            .firstNotNullOfOrNull { (it as? UserThread)?.thread?.takeIf { t -> t.id == threadId && t.firstPostId == postId } }
            ?: return
        actions.launch("agree-$threadId-$postId") {
            runCatching { threads.agree(thread, !thread.agree.agreed) }
                .onFailure { messages.showError(R.string.toast_agree_failed, it) }
        }
    }

    private fun postsOf(tab: UserTab): Pager<Int, UserPost> {
        postPagers.value[tab]?.let { return it }
        val pager = client.pager(
            scope = viewModelScope,
            initialKey = 1,
            itemKey = { post: UserPost -> post.key },
            normalize = { if (it is UserThread) UserThread(Threads(it.thread)) else it },
        ) { page ->
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

    @AssistedFactory
    interface Factory {
        fun create(key: UserProfileKey): UserProfileViewModel
    }
}

private fun PagerState<UserPost>.toItems() = PagerState(
    items = items,
    refresh = refresh,
    append = append,
    hasMore = hasMore,
)
