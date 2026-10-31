package com.huanchengfly.tieba.post.ui.widgets.compose.states

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.huanchengfly.tieba.post.arch.LoadState
import com.huanchengfly.tieba.post.ui.widgets.compose.ErrorScreen

/**
 * Shows the loading, error or empty screen for [state], and [content] once data is available.
 */
@Composable
fun <T> LoadStateScreen(
    state: LoadState<T>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    isEmpty: (T) -> Boolean = { false },
    content: @Composable StateScreenScope.(T) -> Unit,
) {
    StateScreen(
        isEmpty = state !is LoadState.Success || isEmpty(state.data),
        isError = state is LoadState.Error,
        isLoading = state is LoadState.Loading,
        modifier = modifier,
        onReload = onRetry,
        errorScreen = { ErrorScreen(error = (state as? LoadState.Error)?.error) },
    ) {
        if (state is LoadState.Success) content(state.data)
    }
}
