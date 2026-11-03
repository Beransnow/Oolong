package oolong.client.ui.page.main.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import oolong.client.R
import oolong.client.arch.Actions
import oolong.client.arch.LoadStatus
import oolong.client.arch.MessageController
import oolong.client.model.FollowedForum
import oolong.client.model.VisitedForum
import oolong.client.repository.HomeRepository
import oolong.core.network.identity.SessionProvider
import javax.inject.Inject

@Immutable
data class HomeState(
    val forums: ImmutableList<FollowedForum> = persistentListOf(),
    val pinnedIds: ImmutableSet<Long> = persistentSetOf(),
    val visited: ImmutableList<VisitedForum> = persistentListOf(),
    /** How loading the followed forums went. */
    val load: LoadStatus = LoadStatus.Idle,
    val historyExpanded: Boolean = true,
    val loggedIn: Boolean = false,
) {
    /** The followed forums the user pinned, in the order they are followed. */
    val pinned: ImmutableList<FollowedForum> get() = forums.filter { it.id in pinnedIds }.toImmutableList()
}

/** The home page: the forums the user follows, with the pinned ones first and the visited ones above. */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: HomeRepository,
    private val messages: MessageController,
    private val sessionProvider: SessionProvider,
) : ViewModel() {
    private val local = MutableStateFlow(Local())
    private val actions = Actions(viewModelScope)

    private data class Local(
        val forums: List<FollowedForum> = emptyList(),
        // Loading until refresh() knows whether there is anyone to load for, so a logged-in user
        // never sees the guest page first
        val load: LoadStatus = LoadStatus.Loading,
        val historyExpanded: Boolean = true,
        val loggedIn: Boolean = false,
    )

    val state: StateFlow<HomeState> =
        combine(local, repository.pinnedForumIds, repository.visitedForums) { local, pinned, visited ->
            HomeState(
                forums = local.forums.toImmutableList(),
                pinnedIds = pinned.toImmutableSet(),
                visited = visited.toImmutableList(),
                load = local.load,
                historyExpanded = local.historyExpanded,
                loggedIn = local.loggedIn,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeState(load = LoadStatus.Loading))

    init {
        refresh()
    }

    /** Loads the followed forums again, as the account that is logged in now. */
    fun refresh() = actions.launch("refresh") {
        val loggedIn = sessionProvider.currentSession() != null
        if (!loggedIn) {
            local.update { it.copy(forums = emptyList(), load = LoadStatus.Idle, loggedIn = false) }
            return@launch
        }
        local.update { it.copy(load = LoadStatus.Loading, loggedIn = true) }
        runCatching { repository.followedForums() }
            .onSuccess { forums -> local.update { it.copy(forums = forums, load = LoadStatus.Idle) } }
            .onFailure { e -> local.update { it.copy(load = LoadStatus.Error(e)) } }
    }

    fun pin(forum: FollowedForum) = actions.launch("pin-${forum.id}") {
        runCatching { repository.pin(forum.id) }.onFailure { messages.showError(R.string.toast_top_forum_failed, it) }
    }

    fun unpin(forum: FollowedForum) = actions.launch("pin-${forum.id}") {
        runCatching { repository.unpin(forum.id) }.onFailure { messages.showError(R.string.toast_top_forum_failed, it) }
    }

    fun unfollow(forum: FollowedForum) = actions.launch("unfollow-${forum.id}") {
        runCatching { repository.unfollow(forum) }
            .onSuccess { local.update { state -> state.copy(forums = state.forums.filterNot { it.id == forum.id }) } }
            .onFailure { messages.showError(R.string.toast_unlike_failed, it) }
    }

    fun toggleHistory() = local.update { it.copy(historyExpanded = !it.historyExpanded) }
}
