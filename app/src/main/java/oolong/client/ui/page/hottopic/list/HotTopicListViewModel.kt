package oolong.client.ui.page.hottopic.list

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import oolong.client.arch.LoadStatus
import oolong.client.model.RankedTopic
import oolong.client.repository.ExploreRepository
import javax.inject.Inject

@Immutable
data class HotTopicListState(
    val topics: ImmutableList<RankedTopic> = persistentListOf(),
    val load: LoadStatus = LoadStatus.Loading,
)

/** The full hot topics ranking. */
@HiltViewModel
class HotTopicListViewModel @Inject constructor(
    private val repository: ExploreRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(HotTopicListState())
    val state: StateFlow<HotTopicListState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        loadJob?.cancel()
        _state.update { it.copy(load = LoadStatus.Loading) }
        loadJob = viewModelScope.launch {
            try {
                val topics = repository.topics()
                _state.value = HotTopicListState(topics.toImmutableList(), LoadStatus.Idle)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.update { it.copy(load = LoadStatus.Error(e)) }
            }
        }
    }
}
