package oolong.client.ui.page.forum.detail

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import oolong.client.arch.LoadStatus
import oolong.client.model.ForumIntro
import oolong.client.repository.ForumRepository
import oolong.client.ui.navigation.ForumDetailKey

@Immutable
data class ForumDetailState(
    val intro: ForumIntro? = null,
    val load: LoadStatus = LoadStatus.Loading,
)

/** A forum's info page. */
@HiltViewModel(assistedFactory = ForumDetailViewModel.Factory::class)
class ForumDetailViewModel @AssistedInject constructor(
    @Assisted key: ForumDetailKey,
    private val repository: ForumRepository,
) : ViewModel() {
    private val forumId: Long = key.forumId
    private val _state = MutableStateFlow(ForumDetailState())
    val state: StateFlow<ForumDetailState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        loadJob?.cancel()
        _state.update { it.copy(load = LoadStatus.Loading) }
        loadJob = viewModelScope.launch {
            try {
                val intro = repository.forumIntro(forumId)
                _state.value = ForumDetailState(intro, LoadStatus.Idle)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.update { it.copy(load = LoadStatus.Error(e)) }
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(key: ForumDetailKey): ForumDetailViewModel
    }
}
