package oolong.client.ui.page.main.user

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import oolong.client.R
import oolong.client.arch.Actions
import oolong.client.arch.MessageController
import oolong.client.model.HistoryEntry
import oolong.client.repository.HistoryRepository
import oolong.client.repository.OwnProfileCache
import oolong.client.repository.ThreadStoreRepository
import oolong.client.repository.UpdateRepository
import oolong.client.repository.UserRepository
import oolong.core.network.identity.SessionProvider
import javax.inject.Inject

/**
 * @param lastThread the thread visited last, shown on the history entry
 * @param storedWithReplies how many stored threads have new replies, as the first page of them says
 * @param updateAvailable whether a newer release is out, marked on the About row
 */
@Immutable
data class UserState(
    val isLoading: Boolean = false,
    val lastThread: HistoryEntry.Thread? = null,
    val storedWithReplies: Int = 0,
    val updateAvailable: Boolean = false,
)

/**
 * The "me" tab. It shows the account as it is kept, and reloading refreshes what is kept from the
 * user's profile and how many stored threads have new replies.
 */
@HiltViewModel
class UserViewModel @Inject constructor(
    private val repository: UserRepository,
    private val cache: OwnProfileCache,
    private val messages: MessageController,
    private val sessionProvider: SessionProvider,
    private val threadStore: ThreadStoreRepository,
    history: HistoryRepository,
    updates: UpdateRepository,
) : ViewModel() {
    private val loaded = MutableStateFlow(UserState())
    val state: StateFlow<UserState> = combine(loaded, history.threads(limit = 1), updates.available) { state, threads, release ->
        state.copy(lastThread = threads.firstOrNull(), updateAvailable = release != null)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UserState())
    private val actions = Actions(viewModelScope)

    fun refresh() = actions.launch("refresh") {
        val uid = sessionProvider.currentSession()?.uid?.toLongOrNull()
        if (uid == null) {
            loaded.update { it.copy(storedWithReplies = 0) }
            return@launch
        }
        loaded.update { it.copy(isLoading = true) }
        runCatching { repository.profile(uid) }
            .onSuccess(cache::store)
            .onFailure { messages.showError(R.string.toast_server_message, it) }
        // A count is only a hint: it is left as it was if the stored threads can't be read
        runCatching { threadStore.page(0).items.count { it.newPostCount > 0 } }
            .onSuccess { count -> loaded.update { it.copy(storedWithReplies = count) } }
        loaded.update { it.copy(isLoading = false) }
    }
}
