package com.huanchengfly.tieba.post.ui.page.threadstore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.arch.Actions
import com.huanchengfly.tieba.post.arch.MessageController
import com.huanchengfly.tieba.post.arch.PagerState
import com.huanchengfly.tieba.post.arch.pager
import com.huanchengfly.tieba.post.model.StoredThread
import com.huanchengfly.tieba.post.repository.ThreadStoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
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
