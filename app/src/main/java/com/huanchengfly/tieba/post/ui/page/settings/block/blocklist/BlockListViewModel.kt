package com.huanchengfly.tieba.post.ui.page.settings.block.blocklist

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.arch.Actions
import com.huanchengfly.tieba.post.arch.MessageController
import com.huanchengfly.tieba.post.models.database.Block
import com.huanchengfly.tieba.post.repository.BlockListRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@Immutable
data class BlockListState(
    val blackList: ImmutableList<Block> = persistentListOf(),
    val whiteList: ImmutableList<Block> = persistentListOf(),
    /** Whether the rules are still being read. */
    val loading: Boolean = true,
)

/** The block and allow lists, which follow the saved rules. */
@HiltViewModel
class BlockListViewModel @Inject constructor(
    private val repository: BlockListRepository,
    private val messages: MessageController,
) : ViewModel() {
    private val actions = Actions(viewModelScope)

    val state: StateFlow<BlockListState> = repository.rules.map { rules ->
        BlockListState(
            blackList = rules.filter { it.category == Block.CATEGORY_BLACK_LIST }.toImmutableList(),
            whiteList = rules.filter { it.category == Block.CATEGORY_WHITE_LIST }.toImmutableList(),
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, BlockListState())

    /** Adds a rule matching every word of [keywords], which are separated by spaces. */
    fun add(category: Int, keywords: String) = actions.launch("add") {
        val words = keywords.split(" ").filter { it.isNotBlank() }
        if (words.isEmpty()) return@launch
        runCatching { repository.addKeywords(category, words) }
            .onSuccess { messages.show(R.string.toast_add_success) }
            .onFailure { messages.showError(R.string.toast_server_message, it) }
    }

    fun remove(block: Block) = actions.launch("remove-${block.id}") {
        runCatching { repository.remove(block.id) }
            .onSuccess { messages.show(R.string.toast_delete_success) }
            .onFailure { messages.showError(R.string.toast_delete_failure, it) }
    }
}
