package oolong.client.ui.page.threadstore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import oolong.client.R
import oolong.client.arch.Actions
import oolong.client.arch.MessageController
import oolong.client.arch.PagerState
import oolong.client.arch.pager
import oolong.client.model.StoredThread
import oolong.client.repository.ThreadStoreRepository
import javax.inject.Inject

/** The user's thread store, loaded page by page. */
@HiltViewModel
class ThreadStoreViewModel @Inject constructor(
    private val repository: ThreadStoreRepository,
    private val messages: MessageController,
) : ViewModel() {
    private val actions = Actions(viewModelScope)
    private val pager = pager(initialKey = 0, itemKey = StoredThread::threadId) { page -> repository.page(page) }

    val state: StateFlow<PagerState<StoredThread>> = pager.state

    fun refresh() = pager.refresh()

    fun loadMore() = pager.loadMore()

    /** Takes [thread] out of the store, and off the list once the server agrees. */
    fun remove(thread: StoredThread) = actions.launch("remove-${thread.threadId}") {
        runCatching { repository.remove(thread.threadId) }
            .onSuccess {
                pager.update { threads -> threads.filterNot { it.threadId == thread.threadId } }
                messages.show(R.string.delete_store_success)
            }
            .onFailure { messages.showError(R.string.delete_store_failure, it) }
    }
}
