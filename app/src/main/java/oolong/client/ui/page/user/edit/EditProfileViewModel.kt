package oolong.client.ui.page.user.edit

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import oolong.client.R
import oolong.client.arch.Actions
import oolong.client.arch.LoadStatus
import oolong.client.arch.MessageController
import oolong.client.model.UserProfile
import oolong.client.repository.UserRepository
import oolong.core.network.identity.SessionProvider
import java.io.File
import javax.inject.Inject

@Immutable
data class EditProfileState(
    /** The profile as loaded, which the edits are compared against. */
    val profile: UserProfile? = null,
    val load: LoadStatus = LoadStatus.Idle,
    val nickName: String = "",
    /** 1 male, 2 female, anything else unset. */
    val sex: Int = 0,
    val intro: String = "",
    val saving: Boolean = false,
    val uploading: Boolean = false,
) {
    val changed: Boolean
        get() = profile != null && (nickName != profile.displayName || sex != profile.sex || intro != profile.intro)
}

sealed interface EditProfileEffect {
    /** The profile is saved, or there was nothing to save. */
    data object Done : EditProfileEffect
}

/** Editing the logged-in user's own nickname, sex, intro and portrait. */
@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val repository: UserRepository,
    private val messages: MessageController,
    private val sessionProvider: SessionProvider,
) : ViewModel() {
    private val _state = MutableStateFlow(EditProfileState())
    val state: StateFlow<EditProfileState> = _state.asStateFlow()

    private val effects = Channel<EditProfileEffect>(Channel.BUFFERED)
    val effect: Flow<EditProfileEffect> = effects.receiveAsFlow()
    private val actions = Actions(viewModelScope)

    init {
        load()
    }

    fun load() = actions.launch("load") {
        val uid = sessionProvider.currentSession()?.uid?.toLongOrNull()
        if (uid == null) {
            _state.update { it.copy(load = LoadStatus.Error(IllegalStateException("not logged in"))) }
            return@launch
        }
        _state.update { it.copy(load = LoadStatus.Loading) }
        runCatching { repository.profile(uid) }
            .onSuccess { profile ->
                _state.update {
                    it.copy(
                        profile = profile,
                        load = LoadStatus.Idle,
                        nickName = profile.displayName,
                        sex = profile.sex,
                        intro = profile.intro,
                    )
                }
            }
            .onFailure { e -> _state.update { it.copy(load = LoadStatus.Error(e)) } }
    }

    fun setNickName(nickName: String) = _state.update { it.copy(nickName = nickName) }

    fun setSex(sex: Int) = _state.update { it.copy(sex = sex) }

    fun setIntro(intro: String) = _state.update { it.copy(intro = intro) }

    /** Saves the edits, which closes the page; with no edits it just closes. An empty nickname is not sent. */
    fun save() {
        val state = _state.value
        val profile = state.profile ?: return
        if (!state.changed) {
            effects.trySend(EditProfileEffect.Done)
            return
        }
        if (state.nickName.isEmpty() || state.saving) return
        actions.launch("save") {
            _state.update { it.copy(saving = true) }
            runCatching { repository.modifyProfile(profile, state.nickName, state.sex, state.intro) }
                .onSuccess {
                    messages.show(R.string.toast_success)
                    effects.send(EditProfileEffect.Done)
                }
                .onFailure { messages.showError(R.string.toast_save_profile_failed, it) }
            _state.update { it.copy(saving = false) }
        }
    }

    /** Uploads [picture] as the new portrait and shows it once the server takes it. */
    fun uploadPortrait(picture: File) = actions.launch("portrait") {
        _state.update { it.copy(uploading = true) }
        runCatching { repository.uploadPortrait(picture) }
            .onSuccess { inReview ->
                if (inReview != null) {
                    messages.show(R.string.toast_server_message, inReview)
                } else {
                    messages.show(R.string.toast_upload_portrait_success)
                    reloadPortrait()
                }
            }
            .onFailure { messages.showError(R.string.toast_upload_portrait_failed, it) }
        _state.update { it.copy(uploading = false) }
    }

    private suspend fun reloadPortrait() {
        val profile = _state.value.profile ?: return
        runCatching { repository.profile(profile.id) }.onSuccess { reloaded ->
            _state.update { it.copy(profile = it.profile?.copy(portrait = reloaded.portrait)) }
        }
    }
}
