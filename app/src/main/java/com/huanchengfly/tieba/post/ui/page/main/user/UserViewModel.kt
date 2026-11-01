package com.huanchengfly.tieba.post.ui.page.main.user

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huanchengfly.tieba.core.network.identity.SessionProvider
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.arch.Actions
import com.huanchengfly.tieba.post.arch.MessageController
import com.huanchengfly.tieba.post.repository.OwnProfileCache
import com.huanchengfly.tieba.post.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@Immutable
data class UserState(val isLoading: Boolean = false)

/**
 * The "me" tab. It shows the account as it is kept, and reloading refreshes what is kept from the
 * user's profile.
 */
@HiltViewModel
class UserViewModel @Inject constructor(
    private val repository: UserRepository,
    private val cache: OwnProfileCache,
    private val messages: MessageController,
    private val sessionProvider: SessionProvider,
) : ViewModel() {
    private val _state = MutableStateFlow(UserState())
    val state: StateFlow<UserState> = _state.asStateFlow()
    private val actions = Actions(viewModelScope)

    fun refresh() = actions.launch("refresh") {
        val uid = sessionProvider.currentSession()?.uid?.toLongOrNull() ?: return@launch
        _state.update { it.copy(isLoading = true) }
        runCatching { repository.profile(uid) }
            .onSuccess(cache::store)
            .onFailure { messages.showError(R.string.toast_server_message, it) }
        _state.update { it.copy(isLoading = false) }
    }
}
