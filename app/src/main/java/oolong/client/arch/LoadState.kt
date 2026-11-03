package oolong.client.arch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>

    data class Success<T>(val data: T) : LoadState<T>

    data class Error(val error: Throwable) : LoadState<Nothing>
}

/**
 * Runs [load] once and exposes its result as a [LoadState]; [retry] runs it again.
 */
class Loadable<T>(
    private val scope: CoroutineScope,
    private val load: suspend () -> T,
) {
    private val _state = MutableStateFlow<LoadState<T>>(LoadState.Loading)
    val state: StateFlow<LoadState<T>> = _state.asStateFlow()

    private var job: Job? = null

    init {
        retry()
    }

    fun retry() {
        job?.cancel()
        _state.value = LoadState.Loading
        job = scope.launch {
            _state.value = try {
                LoadState.Success(load())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                LoadState.Error(e)
            }
        }
    }
}

fun <T> ViewModel.loadable(load: suspend () -> T): Loadable<T> = Loadable(viewModelScope, load)
