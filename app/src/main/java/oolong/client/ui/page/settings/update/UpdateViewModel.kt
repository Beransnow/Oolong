package oolong.client.ui.page.settings.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import oolong.client.data.update.Release
import oolong.client.data.update.UpdateChannel
import oolong.client.data.update.UpdateCheck
import oolong.client.data.update.UpdateDownload
import oolong.client.data.update.UpdateRepository
import javax.inject.Inject

data class UpdateState(
    val check: UpdateCheck = UpdateCheck.Idle,
    val download: UpdateDownload = UpdateDownload.Idle,
    val autoCheck: Boolean = true,
    val channel: UpdateChannel = UpdateChannel.default,
    val installing: Boolean = false,
    /** What changed in the version installed, by language. */
    val installedNotes: Map<String, Map<String, List<String>>> = emptyMap(),
) {
    /** The newer release to offer, if the last check found one. */
    val offered: Release? get() = (check as? UpdateCheck.Done)?.latest?.takeIf { it.isNewer }
}

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val repository: UpdateRepository,
) : ViewModel() {
    private val installedNotes = repository.installedNotes

    // Starts from what is known at once, so the page opens as it is instead of filling in after
    val state: StateFlow<UpdateState> = combine(
        repository.check,
        repository.download,
        repository.autoCheck,
        repository.channel,
        repository.installing,
    ) { check, download, autoCheck, channel, installing ->
        UpdateState(check, download, autoCheck, channel, installing, installedNotes)
    }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            UpdateState(
                check = repository.check.value,
                download = repository.download.value,
                installing = repository.installing.value,
                installedNotes = installedNotes,
            ),
        )

    init {
        // Opening the page checks, as a system update page does, unless a check has an answer
        if (repository.check.value !is UpdateCheck.Done) repository.checkNow()
        // A release seen here needs no hint on the main page
        viewModelScope.launch { repository.available.filterNotNull().collect { repository.markHinted(it) } }
    }

    fun check() = repository.checkNow()

    fun download(release: Release) = repository.download(release)

    fun skip(release: Release) {
        viewModelScope.launch { repository.skip(release) }
    }

    fun setAutoCheck(enabled: Boolean) {
        viewModelScope.launch { repository.setAutoCheck(enabled) }
    }

    fun setChannel(channel: UpdateChannel) {
        viewModelScope.launch { repository.setChannel(channel) }
    }

    fun install(download: UpdateDownload.Ready) = repository.install(download)
}
