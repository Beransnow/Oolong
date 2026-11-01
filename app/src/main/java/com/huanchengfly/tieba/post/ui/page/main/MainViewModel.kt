package com.huanchengfly.tieba.post.ui.page.main

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@Immutable
data class MainState(
    /** How many new notifications the badge on the notifications tab counts. */
    val messageCount: Int = 0,
)

/** The main page's own state: the notifications badge. */
@HiltViewModel
class MainViewModel @Inject constructor() : ViewModel() {
    private val _state = MutableStateFlow(MainState())
    val state: StateFlow<MainState> = _state.asStateFlow()

    fun setMessageCount(count: Int) {
        _state.value = MainState(messageCount = count)
    }

    fun clearMessages() = setMessageCount(0)
}
