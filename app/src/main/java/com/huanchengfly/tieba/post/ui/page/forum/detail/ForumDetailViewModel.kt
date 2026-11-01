package com.huanchengfly.tieba.post.ui.page.forum.detail

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huanchengfly.tieba.post.arch.LoadStatus
import com.huanchengfly.tieba.post.model.ForumIntro
import com.huanchengfly.tieba.post.repository.ForumRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class ForumDetailState(
    val intro: ForumIntro? = null,
    val load: LoadStatus = LoadStatus.Loading,
)

/** A forum's info page. */
@HiltViewModel
class ForumDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: ForumRepository,
) : ViewModel() {
    private val forumId: Long = checkNotNull(savedState["forumId"])
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
}
