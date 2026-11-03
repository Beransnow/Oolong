package oolong.client.ui.page.settings.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import oolong.client.repository.Release
import oolong.client.repository.UpdateRepository
import javax.inject.Inject

@HiltViewModel
class AboutViewModel @Inject constructor(
    repository: UpdateRepository,
) : ViewModel() {
    /** A newer release not skipped, shown on the software update row. */
    val available: StateFlow<Release?> = repository.available
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
