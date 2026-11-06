package oolong.client

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Browsing without leaving a trace on this device: while on, no visits or searches are kept. It is
 * held only in memory, so it ends with the app's process.
 */
object Incognito {
    private val _on = MutableStateFlow(false)
    val on: StateFlow<Boolean> = _on.asStateFlow()

    fun set(on: Boolean) {
        _on.value = on
    }
}
