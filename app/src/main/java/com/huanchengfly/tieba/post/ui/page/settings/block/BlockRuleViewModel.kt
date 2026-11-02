package com.huanchengfly.tieba.post.ui.page.settings.block

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huanchengfly.tieba.post.arch.Actions
import com.huanchengfly.tieba.post.model.BlockRule
import com.huanchengfly.tieba.post.repository.BlockListRepository
import com.huanchengfly.tieba.post.ui.navigation.BlockRuleKey
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

@Immutable
data class BlockRuleState(
    /** The rule as edited so far; null while the saved one is read. */
    val draft: BlockRule? = null,
    val isNew: Boolean = true,
    /** When the page opened, for how long the rule has left. */
    val now: Long = System.currentTimeMillis(),
) {
    /** Whether the draft matches anything at all, so it can be saved. */
    val canSave: Boolean
        get() = when (val match = draft?.match) {
            null -> false
            is BlockRule.Match.Keyword -> match.words.any { it.isNotBlank() } &&
                (!match.regex || runCatching { Regex(match.words.first()) }.isSuccess)
            is BlockRule.Match.User -> match.name.isNotBlank() || match.uid != 0L
            is BlockRule.Match.Forum -> match.name.isNotBlank()
            is BlockRule.Match.Level -> match.level > 0
        }
}

/** Making a rule, or changing one: what it matches, its conditions and the parts of its sentence. */
@HiltViewModel(assistedFactory = BlockRuleViewModel.Factory::class)
class BlockRuleViewModel @AssistedInject constructor(
    @Assisted private val key: BlockRuleKey,
    private val repository: BlockListRepository,
) : ViewModel() {
    private val actions = Actions(viewModelScope)
    private val _state = MutableStateFlow(BlockRuleState(isNew = key.id == 0L))
    val state: StateFlow<BlockRuleState> = _state.asStateFlow()

    private val closes = Channel<Unit>(Channel.CONFLATED)

    /** Fires once the rule is saved or deleted. */
    val closed = closes.receiveAsFlow()

    init {
        actions.launch("load") {
            val rule = if (key.id == 0L) {
                BlockRule(match = BlockRule.Match.Keyword(emptyList()))
            } else {
                repository.blockRules.first().firstOrNull { it.id == key.id }
            }
            if (rule == null) closes.send(Unit) else _state.update { it.copy(draft = rule) }
        }
    }

    fun edit(transform: (BlockRule) -> BlockRule) {
        _state.update { state -> state.copy(draft = state.draft?.let(transform)) }
    }

    /** Starts the draft over as a rule matching [match], keeping only what every kind has. */
    fun setKind(match: BlockRule.Match) = edit { it.copy(match = match, inForum = null, titleOnly = false) }

    /** Sets the rule to end [days] from now, or never with null. */
    fun setExpiry(days: Int?) = edit { it.copy(expiresAt = days?.let { d -> _state.value.now + d * DayMillis }) }

    fun save() {
        val state = _state.value
        val draft = state.draft ?: return
        if (!state.canSave) return
        actions.launch("save") {
            val rule = draft.withCleanMatch()
            if (state.isNew) repository.add(rule) else repository.update(rule)
            closes.send(Unit)
        }
    }

    fun delete() {
        val draft = _state.value.draft ?: return
        actions.launch("delete") {
            repository.remove(listOf(draft.id))
            closes.send(Unit)
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(key: BlockRuleKey): BlockRuleViewModel
    }
}

/** Keywords without blanks, names trimmed, forum names without 吧. */
private fun BlockRule.withCleanMatch(): BlockRule = copy(
    match = when (val match = match) {
        is BlockRule.Match.Keyword -> if (match.regex) match else match.copy(words = match.words.filter { it.isNotBlank() })
        is BlockRule.Match.User -> match.copy(name = match.name.trim())
        is BlockRule.Match.Forum -> match.copy(name = match.name.trim().removeSuffix("吧"))
        is BlockRule.Match.Level -> match
    },
    inForum = inForum?.trim()?.removeSuffix("吧")?.takeIf { it.isNotEmpty() },
)
