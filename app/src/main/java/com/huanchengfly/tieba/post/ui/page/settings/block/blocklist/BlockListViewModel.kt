package com.huanchengfly.tieba.post.ui.page.settings.block.blocklist

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huanchengfly.tieba.post.arch.Actions
import com.huanchengfly.tieba.post.model.BlockRule
import com.huanchengfly.tieba.post.model.BlockRule.Condition
import com.huanchengfly.tieba.post.model.tests
import com.huanchengfly.tieba.post.repository.BlockListRepository
import com.huanchengfly.tieba.post.ui.page.settings.block.typedValue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** What the rule list can be narrowed to. */
enum class RuleFilter { All, User, Keyword, Forum, Other }

@Immutable
data class BlockListState(
    /** Newest first. */
    val rules: ImmutableList<BlockRule> = persistentListOf(),
    /** Whether the rules are still being read. */
    val loading: Boolean = true,
    /** When the list was read, for how long ago rules were made and last matched. */
    val now: Long = 0,
) {
    /** Rules that have done nothing for [StaleMillis], among those whose age is known. */
    val stale: List<BlockRule> get() = rules.filter { it.isStale(now) }
}

/** Whether any of the rule's tests is of the kind [filter] picks. */
fun BlockRule.matches(filter: RuleFilter): Boolean = filter == RuleFilter.All || condition.tests.any { it.kind == filter }

private val Condition.kind: RuleFilter
    get() = when (this) {
        is Condition.User, is Condition.AuthorName -> RuleFilter.User
        is Condition.Text -> RuleFilter.Keyword
        is Condition.Forum, is Condition.ForumName -> RuleFilter.Forum
        else -> RuleFilter.Other
    }

/** Whether [query] is in what any of the rule's tests looks for. */
fun BlockRule.matches(query: String): Boolean {
    if (query.isBlank()) return true
    return condition.tests.any { test ->
        val text = when (test) {
            is Condition.User -> test.name + " " + test.displayName
            is Condition.Forum -> test.name
            is Condition.Ip -> test.region
            else -> test.typedValue
        }
        text.contains(query.trim(), ignoreCase = true)
    }
}

private fun BlockRule.isStale(now: Long) = createdAt > 0 && action != BlockRule.Action.Allow &&
    now - (lastHitAt ?: createdAt) > StaleMillis

const val StaleMillis = 90 * 24 * 60 * 60 * 1000L

/** The block rules, which follow what is saved, and deleting them. */
@HiltViewModel
class BlockListViewModel @Inject constructor(
    private val repository: BlockListRepository,
) : ViewModel() {
    private val actions = Actions(viewModelScope)

    val state: StateFlow<BlockListState> = repository.blockRules.map { rules ->
        BlockListState(rules.sortedByDescending { it.id }.toImmutableList(), loading = false, now = System.currentTimeMillis())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, BlockListState())

    fun delete(rules: List<BlockRule>) = actions.launch("delete-${rules.map { it.id }}") {
        repository.remove(rules.map { it.id })
    }

    /** Puts back [rules] just deleted. */
    fun restore(rules: List<BlockRule>) = actions.launch("restore-${rules.map { it.id }}") {
        repository.restore(rules)
    }
}
